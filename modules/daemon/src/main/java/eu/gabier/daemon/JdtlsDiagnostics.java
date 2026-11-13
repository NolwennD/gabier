package eu.gabier.daemon;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.jspecify.annotations.Nullable;

/**
 * The diagnostics and the build of {@link JdtlsLanguageServer}: what jdtls published for files, for the project, and
 * what it built. The state they read ({@link Diagnostics}, {@link Builds}) and the lock stay with the facade, which
 * calls these with the lock held.
 */
final class JdtlsDiagnostics {

    private final JdtlsLanguageServer server;
    private final Diagnostics diagnostics;
    private final Builds builds;
    private final Duration quiet;

    JdtlsDiagnostics(JdtlsLanguageServer server, Diagnostics diagnostics, Builds builds, Duration quiet) {
        this.server = server;
        this.diagnostics = diagnostics;
        this.builds = builds;
        this.quiet = quiet;
    }

    /**
     * The diagnostics of several files with one wait for jdtls to be quiet: the cost of a request is that wait, so a
     * caller that has many files asks them together. Each file is opened once, whatever the number of times it is
     * given, and a file given twice is answered twice.
     *
     * @return the diagnostics of each file, in the order given
     * @throws LanguageServerException if jdtls is not ready or one of the files is not in its project (nothing is
     *     opened then)
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    List<List<Diagnostic>> diagnostics(List<Path> files) {
        return published(files).stream().map(JdtlsDiagnostics::domain).toList();
    }

    /**
     * The problems jdtls has published for the Java files of the project, once it has built it: no file is opened and
     * none is read, so the time does not grow with the number of files. jdtls publishes the problems of the whole
     * workspace as its builds go, the files that are not open included (checked on the real jdtls, with Gradle, Maven
     * and no build tool), and the daemon keeps the last publication of each file ({@link Diagnostics#published}). The
     * answer is read once the changes on disk are told to jdtls and its build is finished ({@link Builds}), so a file
     * edited since the last request is answered as it is now, and a build that is running is waited for, not taken for
     * a clean project.
     *
     * <p>The files are those jdtls published a problem for that are {@code .java} files under a folder of a project it
     * imported (a jar, a {@code pom.xml} or a file of another directory is left out), in the order of the paths: a clean
     * file is not in the answer, since jdtls says nothing of a file that never had a problem.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies, does not answer in time or fails the build
     */
    List<DiagnosedFile> projectDiagnostics() {
        JdtlsSession session = server.ready();
        try {
            afterTheBuild(session);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the diagnostics of the project", interrupted);
        }

        return projectFilesWithProblems(session)
                .map(published -> new DiagnosedFile(published.getKey(), domain(published.getValue())))
                .toList();
    }

    /**
     * Tells jdtls of the changes on disk and waits until its build is finished: what is read after it is the project as
     * it is now.
     *
     * @return the watch of the wait, for the requests that follow
     */
    private JdtlsWatch afterTheBuild(JdtlsSession session) throws InterruptedException {
        JdtlsWatch watch = session.newWatch();
        server.takeInChangesOnDisk(session, watch);
        builds.awaitFinished(session, watch);

        return watch;
    }

    /** What jdtls last published for the {@code .java} files of the projects it imported that have a problem. */
    private Stream<Map.Entry<Path, List<org.eclipse.lsp4j.Diagnostic>>> projectFilesWithProblems(JdtlsSession session) {
        return diagnostics.published().entrySet().stream()
                .filter(published -> !published.getValue().isEmpty())
                .filter(published -> published.getKey().getFileName().toString().endsWith(".java"))
                .filter(published -> session.projects().stream().anyMatch(published.getKey()::startsWith));
    }

    /**
     * What jdtls built: for each project it imported, the source folders and the folders it compiled them into
     * ({@code java.project.getSettings}), the jars of the class path of the tests ({@code java.project.getClasspaths},
     * scope {@code test}), and the Java files it published an error for. It is read once the changes on disk are told
     * to jdtls and its build is finished, as {@link #projectDiagnostics()} is, so the classes are those of the files as
     * they are now.
     *
     * <p>jdtls writes a class even for a file that does not compile (a stub whose faulty methods throw), so the files
     * with an error are told apart from the errors it published, not from the classes. No {@code java.*} setting is
     * needed: both commands answer for Eclipse, Maven and Gradle projects as they are.
     *
     * @throws LanguageServerException if jdtls is not ready
     * @throws JdtlsException if jdtls dies, does not answer in time, fails the build or answers a shape that is not
     *     the documented one
     */
    BuiltProject built() {
        JdtlsSession session = server.ready();
        try {
            JdtlsWatch watch = afterTheBuild(session);
            List<ClassFolder> folders = new ArrayList<>();
            Set<Path> classpath = new LinkedHashSet<>();
            for (Path project : session.projects()) {
                String uri = project.toUri().toString();
                folders.addAll(JdtlsBuildAnswers.classFolders(ask(
                        session,
                        watch,
                        "the settings of " + project + " (java.project.getSettings)",
                        "java.project.getSettings",
                        List.of(uri, JdtlsBuildAnswers.SETTINGS))));
                classpath.addAll(JdtlsBuildAnswers.classpath(ask(
                        session,
                        watch,
                        "the class path of " + project + " (java.project.getClasspaths)",
                        "java.project.getClasspaths",
                        List.of(uri, Map.of("scope", "test")))));
            }
            Set<Path> classFolders = folders.stream().map(ClassFolder::classes).collect(Collectors.toSet());
            List<Path> libraries = classpath.stream()
                    .filter(entry -> !classFolders.contains(entry))
                    .toList();
            List<Path> withErrors = projectFilesWithProblems(session)
                    .filter(published -> published.getValue().stream()
                            .anyMatch(found -> found.getSeverity() == DiagnosticSeverity.Error))
                    .map(Map.Entry::getKey)
                    .toList();

            return new BuiltProject(folders.stream().distinct().toList(), libraries, withErrors);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the build of the project", interrupted);
        }
    }

    private static @Nullable Object ask(
            JdtlsSession session, JdtlsWatch watch, String what, String command, List<Object> arguments)
            throws InterruptedException {
        var params = new ExecuteCommandParams(command, arguments);

        return watch.await(what, () -> session.server().getWorkspaceService().executeCommand(params));
    }

    /** What {@link #diagnostics(List)} asks of jdtls, as jdtls publishes it for each file, in the order given. */
    List<List<org.eclipse.lsp4j.Diagnostic>> published(List<Path> files) {
        JdtlsSession session = server.ready();
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path file : files) {
            texts.put(
                    JdtlsProject.normalized(file), JdtlsProject.textInProject(session, JdtlsProject.normalized(file)));
        }
        JdtlsWatch watch = session.newWatch();
        try {
            server.takeInChangesOnDisk(session, watch);
            var publications = server.open(session, texts);
            for (var publication : publications.entrySet()) {
                watch.await("the diagnostics of " + publication.getKey(), publication::getValue);
            }
            QuietWait.await(watch, "the diagnostics of " + texts.keySet(), quiet, diagnostics::quietFor);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException("Interrupted while waiting for the diagnostics of " + texts.keySet(), interrupted);
        } finally {
            JdtlsLanguageServer.close(session, texts.keySet());
        }
        return files.stream()
                .map(file -> diagnostics.latest(JdtlsProject.normalized(file)))
                .toList();
    }

    private static List<Diagnostic> domain(List<org.eclipse.lsp4j.Diagnostic> published) {
        return published.stream()
                .map(found -> new Diagnostic(
                        severity(found.getSeverity()),
                        new Position(
                                found.getRange().getStart().getLine(),
                                found.getRange().getStart().getCharacter()),
                        Diagnostics.text(found)))
                .toList();
    }

    /** A diagnostic without a severity is up to the client to interpret: it is not taken for an error. */
    private static Severity severity(@Nullable DiagnosticSeverity severity) {
        if (severity == null) {
            return Severity.INFORMATION;
        }
        return switch (severity) {
            case Error -> Severity.ERROR;
            case Warning -> Severity.WARNING;
            case Information -> Severity.INFORMATION;
            case Hint -> Severity.HINT;
        };
    }
}
