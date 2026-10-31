package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
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
                Scenarios.ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT);
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
        public Optional<ChangeSet> organizeImports(Path file) {
            return server.organizeImports(file);
        }

        @Override
        public void close() {
            // The fake holds no process.
        }
    }
}
