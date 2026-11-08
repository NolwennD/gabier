package eu.gabier.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * What {@code init-agent} does to one file of a project: {@link #plan the decision} (create, overwrite, replace the
 * block, append it, or refuse), and {@link #write the write}, atomic enough (a temporary file in the folder of the
 * file, then a move) and never outside the target folder: a link that leads out of it is refused.
 */
final class AgentFile {

    /** What is done to the file. */
    enum Action {
        CREATED("created", "would create"),
        OVERWRITTEN("overwrote", "would overwrite"),
        REPLACED("replaced the gabier block of", "would replace the gabier block of"),
        APPENDED("appended the gabier block to", "would append the gabier block to");

        private final String done;
        private final String planned;

        Action(String done, String planned) {
            this.done = done;
            this.planned = planned;
        }

        /** The name in the answer as JSON. */
        String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The words before the path, for what was done or, with a dry run, for what would be. */
        String verb(boolean dryRun) {
            return dryRun ? planned : done;
        }
    }

    /**
     * The decision.
     *
     * @param action what is done to the file
     * @param file the file, as the project names it
     * @param destination where the write goes: the file, or what a link of the project leads to inside the project
     * @param content the whole text of the file once written
     * @param written what gabier writes: the whole file, or the block it puts in an existing one
     */
    record Plan(Action action, Path file, Path destination, String content, String written) {}

    private AgentFile() {}

    /**
     * Decides what to do for this form in this folder, and writes nothing.
     *
     * @param folder the target folder, absolute
     * @param block the block of gabier to put in the file
     * @param force whether a file that gabier did not write may be overwritten
     */
    static Plan plan(AgentForm form, Path folder, String block, boolean force) {
        var file = folder.resolve(form.file());
        try {
            var destination = inside(folder, file);
            if (!Files.exists(destination)) {
                var created = form.newFile(block);

                return new Plan(Action.CREATED, file, destination, created, created);
            }
            var existing = Files.readString(destination);
            var start = existing.indexOf(AgentForm.START);
            if (start >= 0) {
                var end = existing.indexOf(AgentForm.END, start);
                if (end < 0) {
                    throw InitAgentException.blockUnfinished(file);
                }
                var replaced = existing.substring(0, start) + block + existing.substring(end + AgentForm.END.length());

                return new Plan(Action.REPLACED, file, destination, replaced, block);
            }
            if (form.appendsToAFile()) {
                var separator = existing.isEmpty() ? "" : existing.endsWith("\n") ? "\n" : "\n\n";

                return new Plan(Action.APPENDED, file, destination, existing + separator + block + "\n", block);
            }
            if (!force) {
                throw InitAgentException.fileExists(file);
            }
            var overwritten = form.newFile(block);

            return new Plan(Action.OVERWRITTEN, file, destination, overwritten, overwritten);
        } catch (IOException e) {
            throw InitAgentException.writeFailed(file, e);
        }
    }

    /** Writes the plan: the content goes to a new file next to the destination, which then takes its place. */
    static void write(Plan plan) {
        var folder = parentOf(plan.destination());
        var temporary = folder.resolve(".gabier-" + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(folder);
            try {
                Files.writeString(temporary, plan.content(), StandardOpenOption.CREATE_NEW);
                Files.move(temporary, plan.destination(), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw InitAgentException.writeFailed(plan.file(), e);
        }
    }

    /**
     * Where the write of this file goes, if it stays in the folder: the file itself, or, when it exists, what it
     * resolves to (a link of the project to a file of the project is written through and kept). The nearest part of
     * its path that exists must resolve into the folder, so that a link on the way cannot lead out.
     */
    private static Path inside(Path folder, Path file) throws IOException {
        var nearest = file;
        while (!Files.exists(nearest)) {
            nearest = parentOf(nearest);
        }
        var real = nearest.toRealPath();
        if (!real.startsWith(folder.toRealPath())) {
            throw InitAgentException.outsideFolder(file);
        }

        return nearest.equals(file) ? real : file;
    }

    /** The folder of an absolute path that is not the root. */
    private static Path parentOf(Path path) {
        return Objects.requireNonNull(path.getParent());
    }
}
