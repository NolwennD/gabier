import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** What the checks of the daemon use of the session they run in: its paths and its helpers. */
abstract class Checks extends Support {

    final Session session;
    final Path cli;
    final Path daemon;
    final Path fakeDaemon;
    final Path directory;
    final Path socket;
    final List<Process> started;

    Checks(Session session) {
        this.session = session;
        this.cli = session.cli;
        this.daemon = session.daemon;
        this.fakeDaemon = session.fakeDaemon;
        this.directory = session.directory;
        this.socket = session.socket;
        this.started = session.started;
    }

    void failed() {
        session.failures++;
    }

    void expect(boolean condition, String what) {
        session.expect(condition, what);
    }

    Run gabier(String... args) throws IOException, InterruptedException {
        return session.gabier(args);
    }

    Run gabierWith(Path binary, List<String> environment, String... args) throws IOException, InterruptedException {
        return session.gabierWith(binary, environment, args);
    }

    Process startDaemon() throws IOException, InterruptedException {
        return session.startDaemon();
    }

    Process startDaemon(Path executable, String... args) throws IOException, InterruptedException {
        return session.startDaemon(executable, args);
    }

    boolean ends(Process process) throws InterruptedException {
        return session.ends(process);
    }
}
