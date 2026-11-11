package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a client that follows the problems of the project has been told, so that it is told only what changed: the files
 * whose list of problems is not the one it has, a file that became clean included (its list is empty, so that the
 * client sees an error go away). It follows the whole project or the files it was given; a file the project does not
 * list is clean. Each call of {@link #changesIn} updates what the client has been told, so a change is told once.
 *
 * <p>The state is not shared: each client has its own watch, and a call is not safe for two threads at once.
 */
public final class ProblemWatch {

    private final Map<Path, List<Diagnostic>> told = new HashMap<>();

    /** The files the watch is about, none to follow the whole project. */
    private final Set<Path> files;

    private ProblemWatch(Set<Path> files, List<DiagnosedFile> told) {
        this.files = files;
        told.forEach(file -> this.told.put(file.file(), file.diagnostics()));
    }

    /**
     * Follows the whole project from what the client was told of it.
     *
     * @param told the files that have a problem, as {@link JavaLanguageServer#projectDiagnostics()} gives them
     */
    public static ProblemWatch ofProject(List<DiagnosedFile> told) {
        Assert.field("told", told).notNull().noNullElement();

        return new ProblemWatch(Set.of(), told);
    }

    /**
     * Follows the files the client named, from what it was told of each.
     *
     * @param told one element per file named, a clean one included, as {@link JavaLanguageServer#diagnostics} gives
     *     them
     */
    public static ProblemWatch ofFiles(List<DiagnosedFile> told) {
        Assert.field("told", told).notNull().noNullElement();

        return new ProblemWatch(
                new HashSet<>(told.stream().map(DiagnosedFile::file).toList()), told);
    }

    /**
     * The files whose problems are not what the client was told, in the order of the paths, and from then on what the
     * client has been told.
     *
     * @param project the files of the project that have a problem now, as {@link
     *     JavaLanguageServer#projectDiagnostics()} gives them: a file that is not in it is clean
     */
    public List<DiagnosedFile> changesIn(List<DiagnosedFile> project) {
        Assert.field("project", project).notNull().noNullElement();

        Map<Path, List<Diagnostic>> now = new HashMap<>();
        project.stream()
                .filter(file -> files.isEmpty() || files.contains(file.file()))
                .forEach(file -> now.put(file.file(), file.diagnostics()));
        Set<Path> candidates = new TreeSet<>(now.keySet());
        candidates.addAll(told.keySet());
        List<DiagnosedFile> changes = new ArrayList<>();
        for (Path file : candidates) {
            List<Diagnostic> before = told.getOrDefault(file, List.of());
            List<Diagnostic> after = now.getOrDefault(file, List.of());
            if (!before.equals(after)) {
                changes.add(new DiagnosedFile(file, after));
            }
        }
        told.clear();
        told.putAll(now);

        return changes;
    }
}
