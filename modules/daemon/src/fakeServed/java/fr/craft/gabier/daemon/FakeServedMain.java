package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenarios;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The daemon served by the fake language server, for the {@code native} stage of {@code bin/ci}: the same daemon as
 * {@link Main} (same sockets, same methods), with the fake of the core scripted by the diagnostics scenarios in place of
 * a jdtls. Its only argument is the folder of the project the scenarios play on: it writes their files there.
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

        System.exit(Main.serve(System.getenv(), System.out, server));
    }
}
