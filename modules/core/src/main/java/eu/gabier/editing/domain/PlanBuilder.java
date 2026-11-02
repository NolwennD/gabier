package eu.gabier.editing.domain;

import eu.gabier.editing.domain.ChangeReport.Renamed;
import eu.gabier.editing.domain.FileChange.CreateFile;
import eu.gabier.editing.domain.FileChange.DeleteFile;
import eu.gabier.editing.domain.FileChange.EditFile;
import eu.gabier.editing.domain.FileChange.RenameFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a {@link ChangeSet} on a picture of the files held in memory, change after change, so that every refusal comes
 * before anything is written. The picture starts as the files of the {@link FileStore} and is read from it only for the
 * files the changes touch.
 */
final class PlanBuilder {

    private static final String NOTHING = "/dev/null";

    private final FileStore store;
    /** The files the changes have read or written: their bytes, or empty for a file that is not there any more. */
    private final Map<Path, Optional<byte[]>> files = new HashMap<>();

    /** The folders the changes make, so that a second file in a new folder does not plan it again. */
    private final Set<Path> folders = new HashSet<>();

    private final List<Step> steps = new ArrayList<>();
    private final List<Path> created = new ArrayList<>();
    private final List<Renamed> renamed = new ArrayList<>();
    private final List<Path> deleted = new ArrayList<>();
    private final Set<Path> modified = new LinkedHashSet<>();
    private final List<DiffBlock> diff = new ArrayList<>();
    /** Where the diff block of each file created by the changes is, so that a later edit completes it. */
    private final Map<Path, Integer> createdBlocks = new HashMap<>();

    PlanBuilder(FileStore store) {
        this.store = store;
    }

    ChangePlan plan(ChangeSet changes) {
        changes.changes().forEach(this::plan);

        return new ChangePlan(
                steps, new ChangeReport(created, renamed, deleted, List.copyOf(modified), renderedDiff()));
    }

    private void plan(FileChange change) {
        switch (change) {
            case EditFile edit -> edit(edit);
            case CreateFile create -> create(normalized(create.file()));
            case RenameFile rename -> rename(normalized(rename.from()), normalized(rename.to()));
            case DeleteFile delete -> delete(normalized(delete.file()));
        }
    }

    private String renderedDiff() {
        StringBuilder rendered = new StringBuilder();
        diff.forEach(block ->
                rendered.append(UnifiedDiff.of(block.fromLabel(), block.fromText(), block.toLabel(), block.toText())));

        return rendered.toString();
    }

    private void edit(EditFile change) {
        Path file = normalized(change.file());
        byte[] bytes = content(file);
        TextFile before = TextFile.decode(file, bytes);
        TextFile after = before.edit(file, change.edits());
        if (after.equals(before)) {
            return;
        }
        byte[] edited = after.encode(file);
        steps.add(new Step.Write(file, edited, bytes));
        files.put(file, Optional.of(edited));
        Integer createdBlock = createdBlocks.get(file);
        if (createdBlock == null) {
            modified.add(file);
            diff.add(new DiffBlock(file.toString(), before.text(), file.toString(), after.text()));
        } else {
            // The file is new: it is reported once, as created, and its diff shows its text as it ends up.
            diff.set(createdBlock, new DiffBlock(NOTHING, "", file.toString(), after.text()));
        }
    }

    private void create(Path file) {
        if (exists(file)) {
            throw EditingException.fileExists(file);
        }
        makeFolders(file);
        byte[] empty = new byte[0];
        steps.add(new Step.Create(file, empty));
        files.put(file, Optional.of(empty));
        created.add(file);
        createdBlocks.put(file, diff.size());
        diff.add(new DiffBlock(NOTHING, "", file.toString(), ""));
    }

    private void rename(Path from, Path to) {
        byte[] bytes = content(from);
        if (exists(to)) {
            throw EditingException.fileExists(to);
        }
        makeFolders(to);
        steps.add(new Step.Move(from, to));
        files.put(from, Optional.empty());
        files.put(to, Optional.of(bytes));
        renamed.add(new Renamed(from, to));
        diff.add(new DiffBlock(from.toString(), "", to.toString(), ""));
    }

    private void delete(Path file) {
        byte[] bytes = content(file);
        steps.add(new Step.Delete(file, bytes));
        files.put(file, Optional.empty());
        deleted.add(file);
        // The lines of a file that is not text cannot be shown: its headers will do.
        String text = TextFile.tryDecode(bytes).map(TextFile::text).orElse("");
        diff.add(new DiffBlock(file.toString(), text, NOTHING, ""));
    }

    /**
     * A path as the one name of its file: absolute, without {@code .} nor {@code ..} segments, so that {@code ./x},
     * {@code x} and {@code a/../x} are one entry of the picture. The cleaning is on the text of the path: a link in it
     * is not followed.
     */
    private static Path normalized(Path file) {
        return file.toAbsolutePath().normalize();
    }

    /**
     * The folder of a file that is to be created, or to which a file is moved, is made when it is not there: every
     * missing folder, from the first that exists down to the folder of the file, is a step before the one that creates
     * or moves the file. What is in the way
     * (a file, a link to nothing, where a folder would go) cannot be made a folder and is refused.
     */
    private void makeFolders(Path file) {
        Deque<Path> missing = new ArrayDeque<>();
        // A file that is to be created is not the root, which exists: it has a folder.
        Path folder = Objects.requireNonNull(file.getParent());
        while (!folders.contains(folder) && !store.isFolder(folder)) {
            if (exists(folder)) {
                throw EditingException.folderMissing(file, folder);
            }
            missing.push(folder);
            folder = Objects.requireNonNull(folder.getParent());
        }
        missing.forEach(this::makeFolder);
    }

    private void makeFolder(Path folder) {
        steps.add(new Step.MakeFolder(folder));
        folders.add(folder);
    }

    /** One file header of the unified diff, with the two texts it compares. */
    private record DiffBlock(String fromLabel, String fromText, String toLabel, String toText) {}

    private boolean exists(Path file) {
        Optional<byte[]> known = files.get(file);

        return known != null ? known.isPresent() : folders.contains(file) || store.exists(file);
    }

    private byte[] content(Path file) {
        if (!exists(file)) {
            throw EditingException.fileMissing(file);
        }

        return files.computeIfAbsent(file, this::read).orElseThrow();
    }

    private Optional<byte[]> read(Path file) {
        try {
            return Optional.of(store.read(file));
        } catch (IOException failure) {
            throw EditingException.readFailed(file, failure);
        }
    }
}
