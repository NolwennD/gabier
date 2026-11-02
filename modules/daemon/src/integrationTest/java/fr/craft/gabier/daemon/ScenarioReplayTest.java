package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.ReferenceLocations;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.editing.fixtures.Sequel;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the core ({@link Scenarios}) against a real jdtls through the adapter, and fails
 * on any difference between what jdtls answers and what the scenario says: this is what makes the answers the fake of
 * the port is scripted with a promise about the real server and not a guess.
 *
 * <p>The scenarios are grouped by their project: one jdtls is started on each. Its project is written in a fresh
 * folder, with the two files of an Eclipse project ({@code .project}, {@code .classpath}) that jdtls imports without
 * network, which a build file of Maven or Gradle would not guarantee. A scenario whose answer is "not ready" is replayed
 * before jdtls has imported the project, which is how the adapter detects it: the import takes seconds, so asking at
 * once is deterministic. The other scenarios are replayed once it is ready. Not run by {@code check}: {@code
 * ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 *
 * <p>Only the requests the adapter implements are replayed; {@link #replayed} says which, and the test prints the
 * scenarios it leaves out. The answer of a rename is compared to the scenario exactly, and must compile once applied
 * ({@link #compile}); whatever is asked, the sources of the project stay as they were ({@link #assertUntouched}).
 */
class ScenarioReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @Test
    void replaysTheScenariosTheAdapterImplements() throws Exception {
        Map<Map<Path, String>, List<Scenario>> projects = new LinkedHashMap<>();
        List<String> left = new ArrayList<>();
        for (Scenario scenario : Scenarios.all()) {
            if (replayed(scenario)) {
                projects.computeIfAbsent(scenario.files(), files -> new ArrayList<>())
                        .add(scenario);
            } else {
                left.add(scenario.name());
            }
        }
        assertThat(projects).as("the scenarios to replay").isNotEmpty();
        System.out.println("scenarios not replayed (the adapter does not implement their request yet): " + left);

        Map<Map<Path, String>, List<Sequel>> sequels = new LinkedHashMap<>();
        for (Sequel sequel : Scenarios.sequels()) {
            assertThat(projects)
                    .as("the project of " + sequel.name())
                    .containsKey(sequel.first().files());
            sequels.computeIfAbsent(sequel.first().files(), files -> new ArrayList<>())
                    .add(sequel);
        }

        var differences = new SoftAssertions();
        for (var project : projects.entrySet()) {
            replay(project.getValue(), sequels.getOrDefault(project.getKey(), List.of()), differences);
        }
        differences.assertAll();
    }

    /** The requests the adapter implements. */
    private static boolean replayed(Scenario scenario) {
        return scenario.request() instanceof Request.Diagnostics
                || scenario.request() instanceof Request.Rename
                || scenario.request() instanceof Request.OrganizeImports
                || scenario.request() instanceof Request.CleanUpFile
                || scenario.request() instanceof Request.ExtractVariable
                || scenario.request() instanceof Request.ExtractMethod
                || scenario.request() instanceof Request.ExtractConstant
                || scenario.request() instanceof Request.ExtractField
                || scenario.request() instanceof Request.MoveTypeToFile
                || scenario.request() instanceof Request.MoveToPackage
                || scenario.request() instanceof Request.Inline
                || scenario.request() instanceof Request.CreateType
                || scenario.request() instanceof Request.References;
    }

    /**
     * Starts jdtls on the project the scenarios share, and replays them: first those that need it still importing, then
     * the sequels, which change the files on disk and so come last.
     */
    private static void replay(List<Scenario> scenarios, List<Sequel> sequels, SoftAssertions differences)
            throws Exception {
        Path root = Files.createTempDirectory("gabier-scenarios-").toRealPath();
        try {
            scenarios.getFirst().writeProject(root);
            EclipseProject.describe(root);
            var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
            try (var server = JdtlsLanguageServer.start(
                    RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, System.out::println)) {
                for (Scenario scenario : scenarios) {
                    if (needsJdtlsToImport(scenario)) {
                        replay(scenario.inside(root), root, server, differences);
                    }
                }
                server.awaitStarted();
                for (Scenario scenario : scenarios) {
                    if (!needsJdtlsToImport(scenario)) {
                        replay(scenario.inside(root), root, server, differences);
                    }
                }
                for (Sequel sequel : sequels) {
                    replay(sequel, root, server, differences);
                }
            }
        } finally {
            delete(root);
        }
    }

    /**
     * The first scenario, then the files change on disk as an agent changes them, then the second scenario on the same
     * jdtls: it answers for the new text only if it was told of the change. The project is first put back as the first
     * scenario writes it, which is a change on disk too. A rename is applied as the application of the core does; the
     * disk must then hold the files the second scenario expects, and an edit is written.
     */
    private static void replay(Sequel sequel, Path root, JdtlsLanguageServer server, SoftAssertions differences)
            throws IOException {
        Scenario first = sequel.first().inside(root);
        Scenario second = sequel.second().inside(root);
        bringTo(root, first.files());
        replay(first, root, server, differences);
        if (first.answer() instanceof Answer.Renamed renamed) {
            new ChangeApplier(new NioFileStore()).apply(renamed.changes());
            differences
                    .assertThat(sources(root))
                    .as("%s: the project once the change set of the first scenario is applied", sequel.name())
                    .isEqualTo(second.files());
        } else {
            bringTo(root, second.files());
        }
        replay(second, root, server, differences);
    }

    private static void replay(Scenario scenario, Path root, JdtlsLanguageServer server, SoftAssertions differences)
            throws IOException {
        replayAnswer(scenario, root, server, differences);
        assertUntouched(scenario, root, differences);
    }

    /** Writes the files of the project, and deletes the sources that are not part of it. */
    private static void bringTo(Path root, Map<Path, String> files) throws IOException {
        for (Path source : sources(root).keySet()) {
            if (!files.containsKey(source)) {
                Files.delete(source);
            }
        }
        for (var file : files.entrySet()) {
            Files.createDirectories(file.getKey().getParent());
            Files.writeString(file.getKey(), file.getValue());
        }
    }

    /** The sources of the project, by path, as they are on disk. */
    private static Map<Path, String> sources(Path root) throws IOException {
        Map<Path, String> found = new TreeMap<>();
        try (var files = Files.walk(root.resolve("src"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                found.put(file, Files.readString(file));
            }
        }
        return found;
    }

    private static boolean needsJdtlsToImport(Scenario scenario) {
        return scenario.answer() instanceof Answer.Refused refused && refused.key() == LanguageServerErrorKey.NOT_READY;
    }

    private static void replayAnswer(
            Scenario scenario, Path root, JdtlsLanguageServer server, SoftAssertions differences) throws IOException {
        Request request = scenario.request();
        switch (scenario.answer()) {
            case Answer.Diagnosed diagnosed -> {
                var answer = (List<?>) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the diagnostics jdtls answers", scenario.name())
                        .isEqualTo(diagnosed.diagnostics());
            }
            case Answer.Renamed renamed -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(renamed.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Extracted extracted -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(extracted.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Moved moved -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(moved.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.MovedToPackage moved -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                // jdtls does not answer the files of a move in a stable order (seen to change from one run to the next,
                // the temporary folder differing): the changes must be the same, not their order. The order that
                // matters, the edits of a file before its rename, is proved by applying the set in compile().
                differences
                        .assertThat(answer.changes())
                        .as("%s: the changes jdtls answers", scenario.name())
                        .containsExactlyInAnyOrderElementsOf(moved.changes().changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Inlined inlined -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(inlined.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Created created -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(created.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Referenced referenced -> {
                var answer = (ReferenceLocations) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the places jdtls answers", scenario.name())
                        .isEqualTo(referenced.locations());
            }
            case Answer.Organized organized -> {
                var answer = (Optional<?>) askWithoutWaitingForDiagnostics(server, scenario, differences);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(Optional.of(organized.changes()));
                if (answer.isPresent()) {
                    compile(scenario, root, (ChangeSet) answer.get(), differences);
                }
            }
            case Answer.AlreadyOrganized _ -> {
                var answer = (Optional<?>) askWithoutWaitingForDiagnostics(server, scenario, differences);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: jdtls has no edit to give", scenario.name())
                        .isEmpty();
            }
            case Answer.CleanedUp cleaned -> {
                var answer = (Optional<?>) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(Optional.of(cleaned.changes()));
                if (answer.isPresent()) {
                    compile(scenario, root, (ChangeSet) answer.get(), differences);
                }
            }
            case Answer.AlreadyClean _ -> {
                var answer = (Optional<?>) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: jdtls has no edit to give", scenario.name())
                        .isEmpty();
            }
            case Answer.Refused refused -> {
                LanguageServerException expected = request.refusal(refused.key(), refused.details());
                try {
                    var answer = ask(server, request);
                    differences.fail(
                            "%s: jdtls answered %s, the scenario expects %s", scenario.name(), answer, expected);
                } catch (LanguageServerException refusal) {
                    System.out.println(scenario.name() + ": jdtls refused with " + refusal.key());
                    differences
                            .assertThat(refusal.key())
                            .as("%s: the key of the refusal", scenario.name())
                            .isEqualTo(expected.key());
                    differences
                            .assertThat(refusal.parameters())
                            .as("%s: the parameters of the refusal", scenario.name())
                            .isEqualTo(expected.parameters());
                    differences
                            .assertThat(refusal.getMessage())
                            .as("%s: the message of the refusal", scenario.name())
                            .isEqualTo(expected.getMessage());
                }
            }
        }
    }

    /**
     * The organization of the imports of a file whose answer loses no code: jdtls answers at once, and the adapter does
     * not wait for the diagnostics of the file ({@link JdtlsLanguageServer#QUIET} of silence from jdtls), which it asks
     * only for an import that is removed and still used. The answer of an unused import that is removed must not take
     * that long.
     */
    private static Object askWithoutWaitingForDiagnostics(
            JdtlsLanguageServer server, Scenario scenario, SoftAssertions differences) {
        // The first request on a jdtls that has just started is slow for reasons of its own: it is asked twice, and
        // the second one is timed.
        ask(server, scenario.request());
        long start = System.nanoTime();
        Object answer = ask(server, scenario.request());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        System.out.println(scenario.name() + ": jdtls answered in " + elapsed.toMillis() + " ms");
        differences
                .assertThat(elapsed)
                .as("%s: the time to organize, with no diagnostics to wait for", scenario.name())
                .isLessThan(JdtlsLanguageServer.QUIET);

        return answer;
    }

    /** The diagnostics, the change set, or the change set that may be none, that jdtls answers to the request. */
    private static Object ask(JdtlsLanguageServer server, Request request) {
        return switch (request) {
            case Request.Diagnostics diagnostics -> server.diagnostics(diagnostics.file());
            case Request.Rename rename -> server.rename(rename.file(), rename.position(), rename.newName());
            case Request.OrganizeImports organize -> server.organizeImports(organize.file());
            case Request.CleanUpFile cleanUp -> server.cleanUp(cleanUp.file(), cleanUp.cleanUps());
            case Request.ExtractVariable extract ->
                server.extractVariable(extract.file(), extract.selection(), extract.name());
            case Request.ExtractMethod extract ->
                server.extractMethod(extract.file(), extract.selection(), extract.name());
            case Request.ExtractConstant extract ->
                server.extractConstant(extract.file(), extract.selection(), extract.name());
            case Request.ExtractField extract ->
                server.extractField(extract.file(), extract.selection(), extract.name());
            case Request.MoveTypeToFile move -> server.moveTypeToFile(move.file(), move.position());
            case Request.MoveToPackage move -> server.moveToPackage(move.files(), move.target());
            case Request.Inline inline -> server.inline(inline.file(), inline.position());
            case Request.CreateType create -> server.createType(create.file(), create.kind());
            case Request.References references ->
                server.references(references.file(), references.position(), references.includeDeclaration());
        };
    }

    /** Asking changes nothing: the files of the project are the ones the scenario wrote, and no other source is there. */
    private static void assertUntouched(Scenario scenario, Path root, SoftAssertions differences) throws IOException {
        differences
                .assertThat(sources(root))
                .as("%s: the sources of %s after the request", scenario.name(), root)
                .isEqualTo(scenario.files());
        if (scenario.request() instanceof Request.MoveToPackage) {
            differences
                    .assertThat(folders(root))
                    .as(
                            "%s: the folders of %s after the request (a request makes none, whatever its answer)",
                            scenario.name(), root)
                    .isEqualTo(foldersOf(root, scenario.files()));
        }
    }

    /** The folders under {@code src}, as they are on disk. */
    private static Set<Path> folders(Path root) throws IOException {
        try (var files = Files.walk(root.resolve("src"))) {
            return files.filter(Files::isDirectory).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** The folders under {@code src} that the files need. */
    private static Set<Path> foldersOf(Path root, Map<Path, String> files) {
        Set<Path> folders = new TreeSet<>();
        for (Path file : files.keySet()) {
            for (Path folder = file.getParent(); folder.startsWith(root.resolve("src")); folder = folder.getParent()) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /** The project with the change set applied (to a copy of it) is Java that compiles: every reference was changed. */
    private static void compile(Scenario scenario, Path root, ChangeSet answer, SoftAssertions differences)
            throws IOException {
        Path copy = Files.createTempDirectory("gabier-renamed-").toRealPath();
        try {
            copyProject(root, copy);
            ChangeSet onTheCopy = rebased(answer, root, copy);
            new ChangeApplier(new NioFileStore()).apply(onTheCopy);
            // The folders of a created file are made by the application of the set, not by the test.
            onTheCopy.changes().stream()
                    .filter(FileChange.CreateFile.class::isInstance)
                    .map(change -> ((FileChange.CreateFile) change).file())
                    .forEach(created -> differences
                            .assertThat(created)
                            .as("%s: the file the change set creates, and its folders", scenario.name())
                            .isRegularFile());
            differences
                    .assertThat(compilationErrors(copy))
                    .as("%s: the errors of the project once the change set of jdtls is applied", scenario.name())
                    .isEmpty();
        } finally {
            delete(copy);
        }
    }

    private static void copyProject(Path from, Path to) throws IOException {
        try (var files = Files.walk(from.resolve("src"))) {
            for (Path file : files.toList()) {
                Path target = to.resolve(from.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    /** The same changes on the copy of the project. */
    private static ChangeSet rebased(ChangeSet changes, Path root, Path copy) {
        return new ChangeSet(changes.changes().stream()
                .<FileChange>map(change -> switch (change) {
                    case FileChange.EditFile edit ->
                        new FileChange.EditFile(rebased(edit.file(), root, copy), edit.edits());
                    case FileChange.CreateFile create -> new FileChange.CreateFile(rebased(create.file(), root, copy));
                    case FileChange.RenameFile rename ->
                        new FileChange.RenameFile(rebased(rename.from(), root, copy), rebased(rename.to(), root, copy));
                    case FileChange.DeleteFile delete -> new FileChange.DeleteFile(rebased(delete.file(), root, copy));
                })
                .toList());
    }

    private static Path rebased(Path file, Path root, Path copy) {
        return copy.resolve(root.relativize(file));
    }

    /** What javac reports on every source of the project, none when it compiles. */
    private static List<String> compilationErrors(Path project) throws IOException {
        var javac = ToolProvider.getSystemJavaCompiler();
        var errors = new DiagnosticCollector<JavaFileObject>();
        try (var files = Files.walk(project.resolve("src"));
                var manager = javac.getStandardFileManager(errors, null, null)) {
            var sources = manager.getJavaFileObjectsFromPaths(
                    files.filter(file -> file.toString().endsWith(".java")).toList());
            Path classes = Files.createTempDirectory(project, "classes-");
            javac.getTask(null, manager, errors, List.of("-d", classes.toString()), null, sources)
                    .call();
        }
        return errors.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .toList();
    }

    private static void delete(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
