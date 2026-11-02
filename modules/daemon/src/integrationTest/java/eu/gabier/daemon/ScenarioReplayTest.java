package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.fixtures.Effect;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.editing.fixtures.Sequel;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * network, which a build file of Maven or Gradle would not guarantee; a scenario whose files are in several folders that
 * each hold a {@code src} is several projects, linked as their {@code module-info.java} require each other ({@link
 * EclipseProject#describeProjects}). A scenario whose answer is "not ready" is replayed
 * before jdtls has imported the project, which is how the adapter detects it: the import takes seconds, so asking at
 * once is deterministic. The other scenarios are replayed once it is ready. Not run by {@code check}: {@code
 * ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 *
 * <p>Only the requests the adapter implements are replayed; {@link #replayed} says which, and the test prints the
 * scenarios it leaves out. The answer that is a change set is compared by its effect ({@link #compareEffect}: the sources
 * once it is applied to a copy of the project, which must compile), a diagnostic by its severity and its position, the
 * places of a reference and the key of a refusal as they are; only the titles of the offered actions are compared as
 * jdtls words them. Whatever is asked, the sources of the project stay as they were ({@link #assertUntouched}).
 */
class ScenarioReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Pattern MODULE = Pattern.compile("\\bmodule\\s+([\\w.]+)");

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
                || scenario.request() instanceof Request.References
                || scenario.request() instanceof Request.CodeActions
                || scenario.request() instanceof Request.ApplyCodeAction;
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
            var settings = JdtlsSettings.of(root, EclipseProject.describeProjects(root), TIMEOUT);
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

    /** The sources of the project, or of the projects, by path, as they are on disk. */
    private static Map<Path, String> sources(Path root) throws IOException {
        Map<Path, String> found = new TreeMap<>();
        for (Path folder : Effect.sourceFolders(root)) {
            try (var files = Files.walk(folder)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    found.put(file, Files.readString(file));
                }
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
                // The text of a message is the compiler's own: it is printed, not compared.
                differences
                        .assertThat(located(answer))
                        .as("%s: the severity and the position of the diagnostics jdtls answers", scenario.name())
                        .isEqualTo(located(diagnosed.diagnostics()));
            }
            case Answer.Changing changing -> {
                Object asked = changing instanceof Answer.Organized
                        ? askWithoutWaitingForDiagnostics(server, scenario, differences)
                        : ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + asked);
                compareEffect(scenario, root, changesIn(asked), changing.changes(), differences);
            }
            case Answer.Offered offered -> {
                var answer = (OfferedActions) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                // The titles are jdtls's text, the one comparison that stays exact (Scenario.Comparison#EXACT): the
                // scenario names what it must offer, and it may offer more. Nothing offered is exact.
                if (offered.offered().actions().isEmpty()) {
                    differences
                            .assertThat(answer.actions())
                            .as("%s: jdtls offers nothing", scenario.name())
                            .isEmpty();
                } else {
                    differences
                            .assertThat(answer.actions())
                            .as("%s: what jdtls offers", scenario.name())
                            .containsAll(offered.offered().actions());
                }
            }
            case Answer.Referenced referenced -> {
                var answer = (ReferenceLocations) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the places jdtls answers", scenario.name())
                        .isEqualTo(referenced.locations());
            }
            case Answer.AlreadyOrganized _ -> {
                var answer = (Optional<?>) askWithoutWaitingForDiagnostics(server, scenario, differences);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: jdtls has no edit to give", scenario.name())
                        .isEmpty();
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

    /** What matters of a diagnostic, whatever the words of the message. */
    private record Located(Severity severity, Position position) {}

    private static List<Located> located(List<?> diagnostics) {
        return diagnostics.stream()
                .map(Diagnostic.class::cast)
                .map(diagnostic -> new Located(diagnostic.severity(), diagnostic.position()))
                .toList();
    }

    /** The change set of an answer that is one, or of one that may be none. */
    private static Optional<ChangeSet> changesIn(Object answer) {
        return answer instanceof Optional<?> optional
                ? optional.map(ChangeSet.class::cast)
                : Optional.of((ChangeSet) answer);
    }

    /**
     * The one comparison of the answers that are a change set: the sources of the project once the set jdtls answered is
     * applied to a copy of it are the ones the scenario's set leaves ({@link Effect}), whatever edits each of them is
     * made of, and they compile. The set of jdtls that cannot be applied is a difference.
     */
    private static void compareEffect(
            Scenario scenario, Path root, Optional<ChangeSet> answer, ChangeSet expected, SoftAssertions differences)
            throws IOException {
        if (answer.isEmpty()) {
            differences.fail("%s: jdtls has no edit to give, the scenario expects %s", scenario.name(), expected);

            return;
        }
        Map<Path, String> actual;
        try {
            actual = Effect.of(root, answer.get());
        } catch (EditingException cannotApply) {
            differences.fail("%s: the change set jdtls answers cannot be applied: %s", scenario.name(), cannotApply);

            return;
        }
        differences
                .assertThat(actual)
                .as("%s: the sources of the project once the change set jdtls answers is applied", scenario.name())
                .isEqualTo(Effect.of(root, expected));
        differences
                .assertThat(compilationErrors(actual))
                .as("%s: the errors of the project once the change set of jdtls is applied", scenario.name())
                .isEmpty();
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
            case Request.CodeActions actions -> server.codeActions(actions.file(), actions.range());
            case Request.ApplyCodeAction apply -> server.codeActionEdit(apply.file(), apply.range(), apply.action());
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

    /** The folders under the {@code src} of the project, or of each project, as they are on disk. */
    private static Set<Path> folders(Path root) throws IOException {
        Set<Path> found = new TreeSet<>();
        for (Path sources : Effect.sourceFolders(root)) {
            try (var files = Files.walk(sources)) {
                files.filter(Files::isDirectory).forEach(found::add);
            }
        }
        return found;
    }

    /** The folders under the {@code src} of the project, or of each project, that the files need. */
    private static Set<Path> foldersOf(Path root, Map<Path, String> files) throws IOException {
        List<Path> sourceFolders = Effect.sourceFolders(root);
        Set<Path> folders = new TreeSet<>();
        for (Path file : files.keySet()) {
            for (Path folder = file.getParent();
                    sourceFolders.stream().anyMatch(folder::startsWith);
                    folder = folder.getParent()) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /** What javac reports on the sources, by path under the root of the project, none when they compile. */
    private static List<String> compilationErrors(Map<Path, String> sources) throws IOException {
        Path project = Files.createTempDirectory("gabier-compiled-").toRealPath();
        try {
            for (var source : sources.entrySet()) {
                Path file = project.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
            }

            return compilationErrors(project);
        } finally {
            delete(project);
        }
    }

    /**
     * What javac reports on every source under the project, or the projects, none when it compiles. Projects that are
     * modules are compiled together, each module from its source folder ({@code --module-source-path}).
     */
    private static List<String> compilationErrors(Path project) throws IOException {
        var javac = ToolProvider.getSystemJavaCompiler();
        var errors = new DiagnosticCollector<JavaFileObject>();
        List<Path> javaFiles = new ArrayList<>();
        for (Path folder : Effect.sourceFolders(project)) {
            try (var files = Files.walk(folder)) {
                files.filter(file -> file.toString().endsWith(".java")).forEach(javaFiles::add);
            }
        }
        Path classes = Files.createTempDirectory(project, "classes-");
        List<String> options = new ArrayList<>(List.of("-d", classes.toString()));
        List<Path> descriptors = javaFiles.stream()
                .filter(file -> file.endsWith("module-info.java"))
                .toList();
        if (descriptors.size() > 1) {
            for (Path descriptor : descriptors) {
                Matcher module = MODULE.matcher(Files.readString(descriptor));
                assertThat(module.find())
                        .as("the name of the module of " + descriptor)
                        .isTrue();
                options.add("--module-source-path");
                options.add(module.group(1) + "=" + descriptor.getParent());
            }
        }
        try (var manager = javac.getStandardFileManager(errors, null, null)) {
            javac.getTask(null, manager, errors, options, null, manager.getJavaFileObjectsFromPaths(javaFiles))
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
