package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.editing.fixtures.CleanUpScenarios;
import eu.gabier.editing.fixtures.CodeActionScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.PackageMoveScenarios;
import eu.gabier.editing.fixtures.PackageRenameScenarios;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.CompiledProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The daemon served by the fake language server, for the {@code native} stage of {@code bin/ci}: the same daemon as
 * {@link Main} (same sockets, same methods), with the fake of the core scripted by the diagnostics and rename scenarios
 * in place of a jdtls. Its only argument is the folder of the project the scenarios play on: it writes their files
 * there, so the folder is new or empty, and a folder that holds anything is refused (its files would be overwritten). A
 * rename writes the files of that folder, as the real one does. The tests of a small shop are written there too and
 * compiled with javac, for the selection of the tests that a change affects ({@link #building}).
 *
 * <p>The disk of the project changes when a file named {@value #EDIT} appears in its folder: from then on the problems
 * of the whole project are those of {@code DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS} (an error fixed, another
 * made), and a client that follows the disk is told so. So is it each time the file named {@value #CHANGED} is written
 * (it holds the Java files that changed). It is how the {@code native} stage makes a change happen with no jdtls to
 * edit files for.
 *
 * <p>It is its own distribution ({@code :daemon:installFakeServed}), never part of the one a user installs: the fake is
 * test code, and nothing a client sends can switch a daemon to it.
 */
public final class FakeServedMain {

    /** The file whose appearance in the folder of the project is the edit of the files of the project. */
    static final String EDIT = ".gabier-fake-edit";

    /**
     * The file whose lines (paths of Java files, relative to the folder of the project) are the files that changed since
     * the last answer of {@code affected-tests-changed}: the fake has no jdtls to be told of edits. The answer deletes it.
     */
    static final String CHANGED = ".gabier-fake-changed";

    private FakeServedMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: gabier-daemon-fake <project folder>");
            System.exit(2);
        }
        var root = Path.of(args[0]).toAbsolutePath().normalize();
        var refusal = refusal(root);
        if (refusal.isPresent()) {
            System.err.println(refusal.get());
            System.exit(2);
            return;
        }

        var server = serving(root);
        var built = building(root);
        var edited = new FakeJavaLanguageServer(root)
                .scripted(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS);

        System.exit(Main.serve(
                System.getenv(), Path.of("").toAbsolutePath(), System.out, new Served(root, server, edited, built)));
    }

    /**
     * Writes the shop of the selection of tests in the folder, with its filters, and compiles it with javac in the place
     * of the language server, as the build of the project the fake serves: the classes are in {@code bin} and {@code
     * test-bin}, the jar of the test frameworks in {@code lib}.
     */
    static BuiltProject building(Path folder) throws IOException {
        var root = folder.toAbsolutePath().normalize();
        for (var file : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(root.resolve(file.getKey()), file.getValue());
        }

        return CompiledProject.of(root, AffectedTestsCases.mainSources(), AffectedTestsCases.testSources());
    }

    /**
     * Writes the project of the scenarios in the folder, which {@link #refusal} accepted, and gives the fake that plays
     * them.
     */
    static FakeJavaLanguageServer serving(Path folder) throws IOException {
        var root = folder.toAbsolutePath().normalize();
        var server = new FakeJavaLanguageServer(root);
        List<Scenario> scenarios = List.of(
                Scenarios.CLEAN_FILE,
                Scenarios.FILE_WITH_AN_ERROR,
                Scenarios.FILE_WITH_A_WARNING,
                Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT,
                ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT,
                Scenarios.RENAME_METHOD,
                Scenarios.RENAME_TYPE,
                Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL,
                Scenarios.RENAME_OUTSIDE_THE_PROJECT,
                PackageRenameScenarios.RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER,
                Scenarios.ORGANIZE_IMPORTS,
                Scenarios.ORGANIZE_IMPORTS_IN_ORDER,
                Scenarios.ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT,
                CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK,
                CleanUpScenarios.CLEAN_UP_WITH_TWO_CLEAN_UPS,
                CleanUpScenarios.CLEAN_UP_A_FILE_THAT_IS_CLEAN,
                CleanUpScenarios.CLEAN_UP_OUTSIDE_THE_PROJECT,
                Scenarios.EXTRACT_VARIABLE,
                Scenarios.EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                Scenarios.EXTRACT_VARIABLE_TO_AN_INVALID_NAME,
                Scenarios.EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT,
                Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE,
                Scenarios.EXTRACT_METHOD_FROM_HALF_A_STATEMENT,
                Scenarios.EXTRACT_METHOD_TO_AN_INVALID_NAME,
                Scenarios.EXTRACT_CONSTANT,
                Scenarios.EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE,
                Scenarios.EXTRACT_CONSTANT_TO_AN_INVALID_NAME,
                Scenarios.EXTRACT_FIELD,
                Scenarios.EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                Scenarios.EXTRACT_FIELD_TO_AN_INVALID_NAME,
                Scenarios.MOVE_A_STATIC_NESTED_TYPE,
                Scenarios.MOVE_A_TOP_LEVEL_TYPE,
                PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE,
                PackageMoveScenarios.MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN,
                Scenarios.INLINE_A_CONSTANT,
                Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE,
                Scenarios.CREATE_A_CLASS,
                Scenarios.CREATE_A_TYPE_OUTSIDE_THE_PROJECT,
                Scenarios.REFERENCES_OF_A_TYPE,
                Scenarios.REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION,
                Scenarios.REFERENCES_OF_A_SYMBOL_NOTHING_USES,
                Scenarios.REFERENCES_WHERE_THERE_IS_NO_SYMBOL,
                Scenarios.REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT,
                CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED,
                CodeActionScenarios.CODE_ACTIONS_ON_A_CONCATENATION,
                CodeActionScenarios.CODE_ACTIONS_ON_THE_NAME_OF_A_METHOD,
                CodeActionScenarios.CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED,
                CodeActionScenarios.CODE_ACTIONS_OUTSIDE_THE_PROJECT,
                CodeActionScenarios.APPLY_THE_IMPORT_OF_A_TYPE,
                CodeActionScenarios.APPLY_A_REWRITE_OF_A_CONCATENATION,
                CodeActionScenarios.APPLY_AN_ACTION_THAT_IS_A_COMMAND);
        for (var scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }

        return server;
    }

    /**
     * Why the folder cannot take the project of the scenarios, or nothing when it is new or an empty folder. A link that
     * leads nowhere is there: it is looked at as itself, not through what it points to, and is not a folder.
     */
    static Optional<String> refusal(Path folder) throws IOException {
        var root = folder.toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isDirectory(root)) {
            return Optional.of("The project folder " + root + " is not a folder");
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries.findAny().isPresent()
                    ? Optional.of(
                            "The project folder " + root + " is not empty: the scenarios would overwrite its files")
                    : Optional.empty();
        }
    }

    /** The project of the fake: always ready, nothing to end. */
    private record Served(Path root, FakeJavaLanguageServer server, FakeJavaLanguageServer edited, BuiltProject built)
            implements ServedProject {

        private boolean isEdited() {
            return Files.exists(root.resolve(EDIT));
        }

        @Override
        public JdtlsState state() {
            return JdtlsState.READY;
        }

        @Override
        public Optional<JdtlsProgress> jdtlsProgress() {
            return Optional.empty();
        }

        @Override
        public Optional<String> jdtlsFailure() {
            return Optional.empty();
        }

        @Override
        public void warmUp() {
            // Always ready: there is nothing to start.
        }

        @Override
        public List<List<Diagnostic>> diagnostics(List<Path> files) {
            return server.diagnostics(files);
        }

        @Override
        public List<DiagnosedFile> projectDiagnostics() {
            return isEdited() ? edited.projectDiagnostics() : server.projectDiagnostics();
        }

        @Override
        public DiskChanges watchDisk() {
            return new DiskChanges() {
                private boolean told = isEdited();
                private long toldChanged = -1;

                @Override
                public Signal await(Duration timeout) throws InterruptedException {
                    Thread.sleep(Math.min(timeout.toMillis(), 50));
                    long changed = whenChanged();
                    if (changed != -1 && changed != toldChanged) {
                        toldChanged = changed;

                        return Signal.CHANGED;
                    }
                    if (told || !isEdited()) {
                        return Signal.QUIET;
                    }
                    told = true;

                    return Signal.CHANGED;
                }

                @Override
                public void close() {
                    // The fake holds no registration.
                }
            };
        }

        /** When the file of the changed Java files was written, so that a new one is a change of the disk; -1 when none. */
        private long whenChanged() {
            try {
                return Files.getLastModifiedTime(root.resolve(CHANGED)).toMillis();
            } catch (IOException noFile) {
                return -1;
            }
        }

        @Override
        public BuiltProject built() {
            return built;
        }

        /** The files of the lines of {@value #CHANGED}, as the files changed; answering for them deletes the file. */
        @Override
        public ChangedBuild builtWithChanges() {
            Path changed = root.resolve(CHANGED);
            try {
                List<Path> sources = Files.exists(changed)
                        ? Files.readAllLines(changed).stream()
                                .filter(line -> !line.isBlank())
                                .map(root::resolve)
                                .toList()
                        : List.of();

                return new ChangedBuild(built, sources, () -> {
                    try {
                        Files.deleteIfExists(changed);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override
        public ChangeSet rename(Path file, Position position, String newName) {
            return server.rename(file, position, newName);
        }

        @Override
        public ChangeSet extractVariable(Path file, Range selection, String name) {
            return server.extractVariable(file, selection, name);
        }

        @Override
        public ChangeSet extractConstant(Path file, Range selection, String name) {
            return server.extractConstant(file, selection, name);
        }

        @Override
        public ChangeSet extractField(Path file, Range selection, String name) {
            return server.extractField(file, selection, name);
        }

        @Override
        public ChangeSet extractMethod(Path file, Range selection, String name) {
            return server.extractMethod(file, selection, name);
        }

        @Override
        public ChangeSet moveTypeToFile(Path file, Position position) {
            return server.moveTypeToFile(file, position);
        }

        @Override
        public ChangeSet moveToPackage(List<Path> files, PackageName target) {
            return server.moveToPackage(files, target);
        }

        @Override
        public ChangeSet inline(Path file, Position position) {
            return server.inline(file, position);
        }

        @Override
        public ChangeSet createType(Path file, TypeKind kind) {
            return server.createType(file, kind);
        }

        @Override
        public ChangeSet createPackageInfo(Path file) {
            return server.createPackageInfo(file);
        }

        @Override
        public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
            return server.references(file, position, includeDeclaration);
        }

        @Override
        public OfferedActions codeActions(Path file, Range range) {
            return server.codeActions(file, range);
        }

        @Override
        public ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
            return server.codeActionEdit(file, range, action);
        }

        @Override
        public Optional<ChangeSet> organizeImports(Path file) {
            return server.organizeImports(file);
        }

        @Override
        public Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
            return server.cleanUp(file, cleanUps);
        }

        @Override
        public void close() {
            // The fake holds no process.
        }
    }
}
