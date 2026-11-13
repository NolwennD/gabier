package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.NotMovableReason;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * The moves of {@link JdtlsLanguageServer}: a nested type to a file of its own, and classes to a package. The facade
 * holds the lock.
 */
final class JdtlsMoves {

    /** The module descriptor, at the root of a source folder. */
    private static final String MODULE_DESCRIPTOR = "module-info.java";

    private final JdtlsLanguageServer server;

    JdtlsMoves(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Makes a top-level type of the nested type that encloses the position, with {@code java/move} and the move kind
     * {@code moveTypeToNewFile}.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project or jdtls cannot move the
     *     type
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    ChangeSet moveTypeToFile(Path file, Position position) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        JdtlsProject.textInProject(session, path);
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var context = new CodeActionParams(
                new TextDocumentIdentifier(path.toUri().toString()),
                new org.eclipse.lsp4j.Range(at, at),
                new CodeActionContext(List.of()));
        var move = JdtlsServer.MoveParams.typeToNewFile(context);
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            var proposal = watch.await(
                    "the move of the type at " + position + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(session.server().move(move)));
            var refactor = proposal.orElseThrow(() -> LanguageServerException.notMovable(path, position));
            var edit = refactor.edit();
            if (edit == null) {
                throw LanguageServerException.notMovable(path, position);
            }
            List<FileChange> changes = WorkspaceEdits.of(edit, JdtlsProject::textOnDisk);
            if (changes.isEmpty()) {
                throw LanguageServerException.notMovable(path, position);
            }

            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the move of the type at " + position + " of " + path, interrupted);
        }
    }

    /**
     * Moves files to a package with {@code java/move} and the move kind {@code moveResource} (jdtls reads the files, the
     * URI of the folder of the package as the destination, and whether to update the references, as {@code
     * MoveHandler.moveCU} does). The destination is a package of the model of jdtls: it takes it from the folder, under
     * the source folder of the files. What jdtls does not do, the adapter decides before it asks, as jdtls skips a file
     * it does not know ({@code moveCU} ignores it), moves a file onto a file of its name and answers an empty move for a
     * file already in the package: each file must be a Java source of a source folder ({@code
     * java.project.listSourcePaths}), the files must share it (jdtls would put a test class in the main sources), none
     * may be in the package already or take the name of a file there.
     *
     * <p>jdtls answers no change, without a word, for a package whose folder it does not know, and does not see a
     * folder made on disk until it is told of it (an event of {@code workspace/didChangeWatchedFiles} for the folder,
     * which it refreshes): there is no setting and no capability for it, and the protocol has no operation to make a
     * folder (the client does, as for the {@code .temp} file jdtls itself creates to rename a package). So for a package
     * with no folder the adapter makes the missing folders for the time of the request, tells jdtls, asks, and removes
     * them, telling jdtls again, whatever the answer: a dry run leaves no folder behind. The change set moves the files
     * into the folder that does not exist, which applying it makes.
     *
     * @throws LanguageServerException if jdtls is not ready, a file is not in its project, or the files cannot be moved
     *     to the package (see {@link NotMovableReason})
     * @throws JdtlsException if jdtls dies, does not answer in time, cannot move the files for a reason the checks did
     *     not foresee, or the folder made for the request cannot be removed
     */
    ChangeSet moveToPackage(List<Path> files, PackageName target) {
        JdtlsSession session = server.ready();
        List<Path> paths = files.stream().map(JdtlsProject::normalized).toList();
        paths.forEach(path -> JdtlsProject.textInProject(session, path));
        String move = "the move of " + paths.size() + (paths.size() == 1 ? " file" : " files") + " to the package "
                + target.value();
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            Destination destination = destinationOf(paths, target, JdtlsProject.sourceFolders(session, watch));
            Path folder = destination.folder();
            List<Path> made = makeFolders(paths, folder, target);
            try {
                if (!made.isEmpty()) {
                    server.queue(new FileEvent(made.getFirst().toUri().toString(), FileChangeType.Created));
                    server.takeInChangesOnDisk(session, watch);
                }

                return askToMove(session, watch, paths, destination, target, move);
            } finally {
                removeFolders(made, target);
                if (!made.isEmpty()) {
                    server.queue(new FileEvent(made.getFirst().toUri().toString(), FileChangeType.Deleted));
                    server.takeInChangesOnDisk(session, watch);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for " + move, interrupted);
        }
    }

    private ChangeSet askToMove(
            JdtlsSession session,
            JdtlsWatch watch,
            List<Path> paths,
            Destination destination,
            PackageName target,
            String move)
            throws InterruptedException {
        var request = JdtlsServer.MoveParams.resources(
                paths.stream().map(path -> path.toUri().toString()).toList(),
                destination.folder().toUri().toString());
        var proposal = watch.await(
                move, () -> JdtlsRequests.unlessDeclined(session.server().move(request)));
        var refactor = proposal.orElseThrow(() -> nothingToMove(paths, target));
        if (refactor.errorMessage() != null) {
            throw new JdtlsException(
                    "jdtls cannot move the files to the package " + target.value() + ": " + refactor.errorMessage());
        }
        var edit = refactor.edit();
        if (edit == null) {
            throw nothingToMove(paths, target);
        }
        List<FileChange> changes = new ArrayList<>(WorkspaceEdits.of(edit, JdtlsProject::textOnDisk));
        if (changes.isEmpty()) {
            throw nothingToMove(paths, target);
        }
        followInTheModuleDescriptor(paths, destination.sourceFolder(), target).ifPresent(changes::add);

        return new ChangeSet(WorkspaceEdits.inOrderOfAMove(changes));
    }

    /**
     * The edit of {@code module-info.java}, at the root of the source folder of the files, that makes the {@code
     * exports} and {@code opens} of a package follow its classes when the move takes the last of them (no Java file is
     * left in its folder, a {@code package-info.java} counting as one). jdtls rewrites what uses the moved classes but
     * leaves the descriptor as it is (observed on the real server; no setting known changes it), so the adapter edits it (see
     * {@link ModuleExports}).
     */
    private static Optional<FileChange> followInTheModuleDescriptor(
            List<Path> paths, Path sourceFolder, PackageName target) {
        Path descriptor = sourceFolder.resolve(MODULE_DESCRIPTOR);
        Optional<String> text = JdtlsProject.textOnDisk(descriptor);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> emptied = new HashMap<>();
        for (Path folder : paths.stream().map(Path::getParent).distinct().toList()) {
            if (!folder.equals(sourceFolder) && noJavaFileLeft(folder, paths)) {
                List<String> names = new ArrayList<>();
                sourceFolder.relativize(folder).forEach(name -> names.add(name.toString()));
                emptied.put(String.join(".", names), target.value());
            }
        }
        List<eu.gabier.editing.domain.TextEdit> edits = ModuleExports.following(text.get(), emptied);

        return edits.isEmpty() ? Optional.empty() : Optional.of(new FileChange.EditFile(descriptor, edits));
    }

    private static boolean noJavaFileLeft(Path folder, List<Path> moved) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.noneMatch(file -> file.getFileName().toString().endsWith(".java") && !moved.contains(file));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static LanguageServerException nothingToMove(List<Path> paths, PackageName target) {
        return LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.NOTHING_TO_MOVE);
    }

    /**
     * The source folder of the files and the folder of the package under it, once each file is known to be movable
     * there.
     *
     * @throws LanguageServerException for the first file that is not a Java source, is under no source folder, is in the
     *     package or has the name of a file of the package or of an earlier file, and for files of several source
     *     folders
     */
    private static Destination destinationOf(List<Path> paths, PackageName target, List<Path> sourceFolders) {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path path : paths) {
            if (isNotAJavaSource(path)) {
                throw LanguageServerException.notMovableToPackage(
                        List.of(path), target, NotMovableReason.NOT_A_JAVA_SOURCE);
            }
            roots.add(sourceFolders.stream()
                    .filter(path::startsWith)
                    .max(Comparator.comparingInt(Path::getNameCount))
                    .orElseThrow(() -> LanguageServerException.notMovableToPackage(
                            List.of(path), target, NotMovableReason.OUTSIDE_SOURCE_FOLDERS)));
        }
        if (roots.size() > 1) {
            throw LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.SEVERAL_SOURCE_FOLDERS);
        }
        Path sourceFolder = roots.iterator().next();
        Path folder = sourceFolder.resolve(target.folder());
        Set<Path> names = new HashSet<>();
        for (Path path : paths) {
            if (isAlreadyIn(folder, path)) {
                throw LanguageServerException.notMovableToPackage(
                        List.of(path), target, NotMovableReason.ALREADY_IN_PACKAGE);
            }
            if (takesTheNameOfAFileIn(folder, path) || !names.add(path.getFileName())) {
                throw LanguageServerException.notMovableToPackage(List.of(path), target, NotMovableReason.NAME_TAKEN);
            }
        }

        return new Destination(sourceFolder, folder);
    }

    private static boolean takesTheNameOfAFileIn(Path folder, Path path) {
        return Files.exists(folder.resolve(path.getFileName()));
    }

    private static boolean isAlreadyIn(Path folder, Path path) {
        return folder.equals(path.getParent());
    }

    private static boolean isNotAJavaSource(Path path) {
        return !path.getFileName().toString().endsWith(".java");
    }

    /** Where a move puts the files: the source folder they are in and the folder of the package under it. */
    private record Destination(Path sourceFolder, Path folder) {}

    /**
     * Makes the folders of the package that are not there, from the highest down.
     *
     * @return the folders made, highest first, none if the package had its folder
     * @throws LanguageServerException if a folder cannot be made (a file is where it goes); those made before are removed
     */
    private static List<Path> makeFolders(List<Path> paths, Path folder, PackageName target) {
        Deque<Path> missing = new ArrayDeque<>();
        Path candidate = folder;
        while (!Files.isDirectory(candidate)) {
            missing.push(candidate);
            candidate = Objects.requireNonNull(candidate.getParent(), "the source folder is a folder");
        }
        List<Path> made = new ArrayList<>();
        try {
            for (Path missed : missing) {
                Files.createDirectory(missed);
                made.add(missed);
            }
        } catch (IOException failure) {
            removeFolders(made, target);
            throw LanguageServerException.notMovableToPackage(paths, target, NotMovableReason.FOLDER_NOT_MADE);
        }

        return made;
    }

    /** Removes the folders made for a request, the lowest first. */
    private static void removeFolders(List<Path> made, PackageName target) {
        for (Path folder : made.reversed()) {
            try {
                Files.delete(folder);
            } catch (IOException failure) {
                throw new JdtlsException(
                        "gabier could not remove the folder " + folder + " made for the move to the package "
                                + target.value() + ": " + failure,
                        failure);
            }
        }
    }
}
