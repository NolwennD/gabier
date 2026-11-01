package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.CleanUp;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.ReferenceLocations;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.editing.fixtures.CleanUpScenarios;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.PackageMoveScenarios;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.protocol.JdtlsState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The daemon served by the fake language server, for the {@code native} stage of {@code bin/ci}: the same daemon as
 * {@link Main} (same sockets, same methods), with the fake of the core scripted by the diagnostics and rename scenarios
 * in place of a jdtls. Its only argument is the folder of the project the scenarios play on: it writes their files
 * there, so the folder is new or empty, and a folder that holds anything is refused (its files would be overwritten). A
 * rename writes the files of that folder, as the real one does.
 *
 * <p>It is its own distribution ({@code :daemon:installFakeServed}), never part of the one a user installs: the fake is
 * test code, and nothing a client sends can switch a daemon to it.
 */
public final class FakeServedMain {

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

        System.exit(Main.serve(System.getenv(), System.out, new Served(root, serving(root))));
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
                Scenarios.RENAME_METHOD,
                Scenarios.RENAME_TYPE,
                Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL,
                Scenarios.RENAME_OUTSIDE_THE_PROJECT,
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
                Scenarios.REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT);
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
    private record Served(Path root, FakeJavaLanguageServer server) implements ServedProject {

        @Override
        public JdtlsState state() {
            return JdtlsState.READY;
        }

        @Override
        public Optional<String> jdtlsFailure() {
            return Optional.empty();
        }

        @Override
        public List<Diagnostic> diagnostics(Path file) {
            return server.diagnostics(file);
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
        public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
            return server.references(file, position, includeDeclaration);
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
