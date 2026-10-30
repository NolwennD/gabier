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
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The daemon served by the fake language server, for the {@code native} stage of {@code bin/ci}: the same daemon as
 * {@link Main} (same sockets, same methods), with the fake of the core scripted by the diagnostics scenarios in place of
 * a jdtls. Its only argument is the folder of the project the scenarios play on: it writes their files there, so the
 * folder is new or empty, and a folder that holds anything is refused (its files would be overwritten).
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
        FakeJavaLanguageServer server;
        try {
            server = serving(root);
        } catch (IllegalArgumentException refusal) {
            System.err.println(refusal.getMessage());
            System.exit(2);
            return;
        }

        System.exit(Main.serve(System.getenv(), System.out, new Served(root, server)));
    }

    /**
     * Writes the project of the scenarios in the folder and gives the fake that plays them.
     *
     * @throws IllegalArgumentException when the folder exists and is not an empty folder
     */
    static FakeJavaLanguageServer serving(Path folder) throws IOException {
        var root = folder.toAbsolutePath().normalize();
        requireNewOrEmpty(root);
        var server = new FakeJavaLanguageServer(root);
        List<Scenario> scenarios = List.of(
                Scenarios.CLEAN_FILE,
                Scenarios.FILE_WITH_AN_ERROR,
                Scenarios.FILE_WITH_A_WARNING,
                Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT);
        for (var scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }

        return server;
    }

    private static void requireNewOrEmpty(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("The project folder " + root + " is not a folder");
        }
        try (Stream<Path> entries = Files.list(root)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalArgumentException(
                        "The project folder " + root + " is not empty: the scenarios would overwrite its files");
            }
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
        public void close() {
            // The fake holds no process.
        }
    }
}
