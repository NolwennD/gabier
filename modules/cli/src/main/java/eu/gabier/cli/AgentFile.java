package eu.gabier.cli;

import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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

    /** The making of a hard link, {@link Files#createLink}. */
    @FunctionalInterface
    interface HardLink {
        void create(Path link, Path existing) throws IOException;
    }

    private static final String BYTE_ORDER_MARK = "\uFEFF";

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
            var scan = scan(existing, file);
            if (scan.block().isPresent()) {
                var span = scan.block().get();
                var mark = existing.startsWith(BYTE_ORDER_MARK) ? BYTE_ORDER_MARK : "";
                var restored = form.missingFrontmatter(existing.substring(mark.length()));
                var lineEnding = lineEndingAt(existing, span.start());
                var replaced = mark
                        + inLineEnding(lineEnding, restored)
                        + existing.substring(mark.length(), span.start())
                        + inLineEnding(lineEnding, block)
                        + existing.substring(span.end());

                return new Plan(Action.REPLACED, file, destination, replaced, restored + block);
            }
            if (form.appendsToAFile()) {
                if (scan.endsInFence()) {
                    throw InitAgentException.fenceUnclosed(file);
                }
                var separator = existing.isEmpty() ? "" : existing.endsWith("\n") ? "\n" : "\n\n";
                var appended = existing + inLineEnding(lineEndingAt(existing, 0), separator + block + "\n");

                return new Plan(Action.APPENDED, file, destination, appended, block);
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

    /**
     * Writes the plan: the content goes to a new file next to the destination, which then takes its place. A new file is
     * published with a hard link, which fails if the name has been taken since the plan, so that a file made by
     * someone else in between is refused ({@code init-agent-file-exists}) and never clobbered.
     */
    static void write(Plan plan) {
        write(plan, Files::createLink);
    }

    /** As {@link #write(Plan)}, with the way a hard link is made: a test plays a file system that has none. */
    static void write(Plan plan, HardLink hardLink) {
        var destination = plan.destination();
        var folder = parentOf(destination);
        var temporary = folder.resolve(".gabier-" + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(folder);
            try {
                Files.writeString(temporary, plan.content(), StandardOpenOption.CREATE_NEW);
                if (plan.action() != Action.CREATED) {
                    keepModeOf(destination, temporary);
                }
                if (plan.action() == Action.CREATED && !Files.isSymbolicLink(destination)) {
                    publishNew(hardLink, destination, temporary);
                } else {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (FileAlreadyExistsException e) {
                throw InitAgentException.fileAppeared(plan.file());
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw InitAgentException.writeFailed(plan.file(), e);
        }
    }

    /**
     * Gives a new file its name without taking one that exists: a hard link of the temporary file; on a file system
     * that has none, a copy of it, which the file system creates exclusively and removes if it fails.
     */
    private static void publishNew(HardLink hardLink, Path destination, Path temporary) throws IOException {
        try {
            hardLink.create(destination, temporary);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (IOException | UnsupportedOperationException e) {
            Files.copy(temporary, destination);
        }
    }

    /** The temporary file takes the permissions of the file it replaces; a file system with none leaves them. */
    @ExcludeFromGeneratedCodeCoverage(
            reason = "The branch for a file system without POSIX permissions cannot be had on the machines "
                    + "that run the tests; the mode kept on the others is tested")
    private static void keepModeOf(Path destination, Path temporary) throws IOException {
        var view = Files.getFileAttributeView(destination, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(temporary, view.readAttributes().permissions());
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

    /** The line ending of the line that starts at this offset: Windows one when it has one, else Unix one. */
    private static String lineEndingAt(String text, int lineStart) {
        var lineBreak = text.indexOf('\n', lineStart);

        return lineBreak > 0 && text.charAt(lineBreak - 1) == '\r' ? "\r\n" : "\n";
    }

    /** The text, written with the line ending (a text of this class is written with Unix ones). */
    private static String inLineEnding(String lineEnding, String text) {
        return text.replace("\n", lineEnding);
    }

    /** What a text holds: the block of gabier, if any, and whether the text ends inside a code fence. */
    private record Scan(Optional<Span> block, boolean endsInFence) {}

    /** The block of gabier: from the start of its first line to the end of its last, in a text. */
    private record Span(int start, int end) {}

    /**
     * Where the block of gabier lies in the text: a marker is a line of its own, outside a code fence (a line that
     * opens with three backticks or three tildes opens or closes one), so that a text that quotes the markers does
     * not hold a block; a fence that is never closed runs to the end of the text. More than one start is refused, and
     * so is a start with no end after it.
     */
    private static Scan scan(String text, Path file) {
        var starts = new ArrayList<Integer>();
        var ends = new ArrayList<Integer>();
        var fence = "";
        var offset = 0;
        while (offset < text.length()) {
            var lineBreak = text.indexOf('\n', offset);
            var next = lineBreak < 0 ? text.length() : lineBreak + 1;
            var content = text.substring(offset, next).stripTrailing();
            var opened = fence.isEmpty() ? opening(content) : "";
            if (!fence.isEmpty()) {
                fence = closes(fence, content) ? "" : fence;
            } else if (!opened.isEmpty()) {
                fence = opened;
            } else if (content.equals(AgentForm.START)) {
                starts.add(offset);
            } else if (content.equals(AgentForm.END)) {
                ends.add(offset + AgentForm.END.length());
            }
            offset = next;
        }
        if (starts.size() > 1) {
            throw InitAgentException.blockRepeated(file);
        }
        if (starts.isEmpty()) {
            return new Scan(Optional.empty(), !fence.isEmpty());
        }
        var start = starts.getFirst();
        var end = ends.stream()
                .filter(candidate -> candidate > start)
                .findFirst()
                .orElseThrow(() -> InitAgentException.blockUnfinished(file));

        return new Scan(Optional.of(new Span(start, end)), !fence.isEmpty());
    }

    /**
     * The run of backticks or tildes that a line opens a code fence with (CommonMark): up to three spaces, then at
     * least three of the same character, and no backtick in the rest of a line that opens with backticks; empty for
     * a line that opens none.
     */
    private static String opening(String line) {
        var rest = withoutIndent(line);
        if (rest.isEmpty() || line.length() - rest.length() > 3 || "`~".indexOf(rest.charAt(0)) < 0) {
            return "";
        }
        var length = 0;
        while (length < rest.length() && rest.charAt(length) == rest.charAt(0)) {
            length++;
        }
        var fence = rest.substring(0, length);

        return length < 3 || (fence.startsWith("`") && rest.substring(length).contains("`")) ? "" : fence;
    }

    /** Whether the line closes the fence: up to three spaces, then only its character, at least as many as it has. */
    private static boolean closes(String fence, String line) {
        var rest = withoutIndent(line);

        return line.length() - rest.length() <= 3
                && rest.length() >= fence.length()
                && rest.chars().allMatch(character -> character == fence.charAt(0));
    }

    /** The line without the spaces it opens with. */
    private static String withoutIndent(String line) {
        var indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }

        return line.substring(indent);
    }

    /** The folder of an absolute path that is not the root. */
    private static Path parentOf(Path path) {
        return Objects.requireNonNull(path.getParent());
    }
}
