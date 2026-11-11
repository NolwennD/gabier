package eu.gabier.cli.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.infrastructure.primary.FakeDaemonScripts;
import eu.gabier.cli.infrastructure.primary.ScriptedDaemon;
import eu.gabier.cli.infrastructure.primary.ScriptedStreamingDaemon;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.shared.error.domain.ErrorKey;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The daemon of a cli: what the pause between two questions does when the wait is cut short, and a daemon that stops. */
class SocketDaemonTest {

    @TempDir
    Path directory;

    private Path socket() {
        return directory.resolve("gabier.sock");
    }

    private SocketDaemon daemon() {
        return daemon(Duration.ofSeconds(5));
    }

    private SocketDaemon daemon(Duration stoppingWait) {
        return new SocketDaemon(
                Map.of(
                        "GABIER_SOCKET",
                        socket().toString(),
                        "PATH",
                        directory.resolve("bin").toString()),
                new PrintStream(PrintStream.nullOutputStream()),
                Duration.ofSeconds(1),
                stoppingWait);
    }

    @AfterEach
    void endTheDaemon() throws IOException {
        FakeDaemonScripts.end(directory.resolve("gabier.sock.log"));
    }

    @Test
    void aPauseThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption() {
        var daemon = daemon();
        Thread.currentThread().interrupt();

        try {
            // The interruption is there before the pause begins, so the pause ends at once, whatever the machine does.
            var failure = catchThrowableOfType(DaemonException.class, () -> daemon.statusAfter(Duration.ofMinutes(1)));

            assertThat(failure.getMessage())
                    .isEqualTo("The connection to the daemon failed: the wait for jdtls was interrupted");
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void followingGivesTheAnswerThenWhatTheDaemonTellsNextInTheOrderItWroteItAndFailsWhenTheDaemonGoesAway()
            throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), JsonRpc.success("1", "{\"files\":[]}"))) {
            daemon.says(JsonRpc.notice("first"));
            daemon.says(JsonRpc.notice("second"));
            daemon.hangsUp();

            try (var following = daemon().follow("watch-all", Map.of())) {
                assertThat(following.reply().result()).containsEntry("files", java.util.List.of());
                assertThat(following.next().params()).containsEntry("message", "first");
                assertThat(following.next().params()).containsEntry("message", "second");
                var failure = catchThrowableOfType(DaemonException.class, following::next);

                assertThat(failure.getMessage()).contains("the daemon stopped: nothing is followed any more");
                assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @Test
    void followingEndsItsConnectionWhenClosed() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), JsonRpc.success("1", "{\"files\":[]}"))) {
            daemon().follow("watch-all", Map.of()).close();

            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void followingRelaysTheRefusalOfTheDaemonAndLeavesNothingOpen() throws Exception {
        var refusal = JsonRpc.failure(
                "1",
                new eu.gabier.protocol.Problem(
                        "urn:gabier:error:test-refused",
                        "Refused",
                        "no",
                        "test-refused",
                        Map.of(),
                        ErrorStatus.BAD_REQUEST));
        try (var daemon = new ScriptedStreamingDaemon(socket(), refusal)) {
            var failure = catchThrowableOfType(DaemonRefusal.class, () -> daemon().follow("watch-all", Map.of()));

            assertThat(failure.key()).isEqualTo("test-refused");
            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void followingFailsWhenTheDaemonAnswersAgainInsteadOfTellingWhatChanged() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), JsonRpc.success("1", "{\"files\":[]}"))) {
            daemon.says(JsonRpc.success("2", "{}"));

            try (var following = daemon().follow("watch-all", Map.of())) {
                var failure = catchThrowableOfType(DaemonException.class, following::next);

                assertThat(failure.getMessage()).contains("answered again");
            }
        }
    }

    @Test
    void asksAgainOnANewDaemonWhenTheDaemonItReachedAnswersThatItIsStoppingAndIsStillListening() throws IOException {
        FakeDaemonScripts.installListening(directory.resolve("bin"));
        try (var stopping = new StoppingDaemon(socket(), true)) {
            var reply = daemon().ask("diagnostics", Map.of());

            assertThat(reply.result()).isNotNull();
            assertThat(stopping.requests()).isEqualTo(1);
            assertThat(Files.readString(directory.resolve("gabier.sock.log"))).startsWith("listening ");
        }
    }

    @Test
    void askingToStartAnswersFromANewDaemonAndSaysItStartedOneWhenTheDaemonItReachedIsStopping() throws IOException {
        FakeDaemonScripts.installListening(directory.resolve("bin"));
        try (var stopping = new StoppingDaemon(socket(), true)) {
            var started = daemon().askStarting("start");

            assertThat(started.daemonStarted()).isTrue();
            assertThat(stopping.requests()).isEqualTo(1);
        }
    }

    @Test
    void isAConnectionFailureWhenTheStoppingDaemonDoesNotGoAwayInTime() throws IOException {
        try (var stopping = new StoppingDaemon(socket(), false)) {
            var failure = catchThrowableOfType(
                    DaemonException.class, () -> daemon(Duration.ofMillis(200)).ask("diagnostics", Map.of()));

            assertThat(failure.getMessage()).contains("daemon is stopping");
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(stopping.requests()).isEqualTo(1);
        }
    }

    @Test
    void isAConnectionFailureAndAsksNoThirdTimeWhenTheNewDaemonIsStoppingToo() throws IOException {
        FakeDaemonScripts.installRefusing(directory.resolve("bin"), "daemon-stopping");
        try (var stopping = new StoppingDaemon(socket(), true)) {
            var failure = catchThrowableOfType(DaemonException.class, () -> daemon().ask("diagnostics", Map.of()));

            assertThat(failure.getMessage()).contains("daemon is stopping");
            assertThat(stopping.requests()).isEqualTo(1);
        }
    }

    @Test
    void relaysTheRefusalOfTheNewDaemonWhenItIsNotThatItIsStopping() throws IOException {
        FakeDaemonScripts.installRefusing(directory.resolve("bin"), "test-refused");
        try (var stopping = new StoppingDaemon(socket(), true)) {
            var failure = catchThrowableOfType(DaemonRefusal.class, () -> daemon().ask("diagnostics", Map.of()));

            assertThat(failure.key()).isEqualTo("test-refused");
            assertThat(stopping.requests()).isEqualTo(1);
        }
    }

    @Test
    void neverAsksAgainForARequestTheDaemonReadAndHungUpOn() throws IOException {
        try (var hangingUp = new ScriptedDaemon(socket(), null)) {
            var failure = catchThrowableOfType(DaemonException.class, () -> daemon().ask("diagnostics", Map.of()));

            assertThat(failure.getMessage()).contains("hung up");
            assertThat(hangingUp.requests()).hasSize(1);
        }
    }

    @Test
    void doesNotAskAgainWhenTheDaemonRefusesForAnotherReason() throws IOException {
        var refusal = JsonRpc.failure("1", new Refused());
        try (var daemon = new ScriptedDaemon(socket(), refusal)) {
            var failure = catchThrowableOfType(DaemonRefusal.class, () -> daemon().ask("diagnostics", Map.of()));

            assertThat(failure.key()).isEqualTo("test-refused");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    /**
     * A daemon that has decided to stop and has not yet closed its listener, as the real one is for an instant: it answers
     * every request with the refusal {@code daemon-stopping}. When it goes away, it does it at the second connection it
     * accepts, which is the client that waits for it to be gone: it gives its socket back, as the real one does when it
     * closes.
     */
    private static final class StoppingDaemon implements AutoCloseable {

        private final ServerSocketChannel listener;
        private final boolean goesAway;
        private final AtomicInteger requests = new AtomicInteger();

        StoppingDaemon(Path socket, boolean goesAway) throws IOException {
            this.goesAway = goesAway;
            listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> accept(socket));
        }

        private void accept(Path socket) {
            try {
                var accepted = 0;
                while (true) {
                    var client = listener.accept();
                    accepted++;
                    if (goesAway && accepted == 2) {
                        listener.close();
                        Files.deleteIfExists(socket);
                        client.close();

                        return;
                    }
                    Thread.ofVirtual().start(() -> answer(client));
                }
            } catch (IOException closed) {
                // The test is over.
            }
        }

        private void answer(SocketChannel client) {
            try (client) {
                var reader = new BufferedReader(
                        new InputStreamReader(Channels.newInputStream(client), StandardCharsets.UTF_8));
                while (reader.readLine() != null) {
                    requests.incrementAndGet();
                    client.write(ByteBuffer.wrap(
                            (JsonRpc.failure("1", new Stopping()) + "\n").getBytes(StandardCharsets.UTF_8)));
                }
            } catch (IOException gone) {
                // The client is gone.
            }
        }

        int requests() {
            return requests.get();
        }

        @Override
        public void close() throws IOException {
            listener.close();
        }
    }

    private enum Keys implements ErrorKey {
        STOPPING("daemon-stopping"),
        REFUSED("test-refused");

        private final String key;

        Keys(String key) {
            this.key = key;
        }

        @Override
        public String get() {
            return key;
        }
    }

    private static final class Stopping extends GeneratorException {

        Stopping() {
            super(GeneratorException.internalServerError(Keys.STOPPING).message("The daemon is stopping"));
        }
    }

    private static final class Refused extends GeneratorException {

        Refused() {
            super(GeneratorException.badRequest(Keys.REFUSED).message("No, not this"));
        }
    }
}
