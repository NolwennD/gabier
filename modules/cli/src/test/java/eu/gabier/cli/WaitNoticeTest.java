package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.JdtlsState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaitNoticeTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private static DaemonStatus status(JdtlsState state) {
        return new DaemonStatus("0.1", 4242, Duration.ofSeconds(5), Path.of("/work/demo"), state);
    }

    private String written() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private void awaitLines(int count) throws InterruptedException {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (written().lines().count() < count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void repeatsItsLineWhileJdtlsIsStarting() throws InterruptedException {
        try (var notice = WaitNotice.during(
                Duration.ofMillis(10), Duration.ofMillis(10), () -> Optional.of(status(JdtlsState.STARTING)), err)) {
            awaitLines(3);
        }

        assertThat(written().lines())
                .hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(line -> assertThat(line).startsWith("gabier: waiting for jdtls (starting, "));
    }

    @Test
    void saysNothingOnceItIsClosed() throws InterruptedException {
        var notice = WaitNotice.during(
                Duration.ofMillis(10), Duration.ofMillis(10), () -> Optional.of(status(JdtlsState.STARTING)), err);
        awaitLines(1);
        notice.close();
        var atTheClose = written();
        Thread.sleep(100);

        assertThat(written()).isEqualTo(atTheClose);
    }

    @Test
    void saysNothingOfAStatusThatCameAfterTheClose() throws InterruptedException {
        var asking = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var notice = WaitNotice.during(
                Duration.ofMillis(5),
                Duration.ofMinutes(1),
                () -> {
                    asking.countDown();
                    try {
                        closed.await();
                    } catch (InterruptedException closing) {
                        Thread.currentThread().interrupt();
                    }

                    return Optional.of(status(JdtlsState.STARTING));
                },
                err);
        asking.await();
        notice.close();
        closed.countDown();
        Thread.sleep(100);

        assertThat(written()).isEmpty();
    }

    @Test
    void saysNothingAboutAJdtlsThatIsNotStarting() throws InterruptedException {
        for (var state : new JdtlsState[] {JdtlsState.NOT_STARTED, JdtlsState.READY, JdtlsState.FAILED}) {
            try (var notice = WaitNotice.during(
                    Duration.ofMillis(5), Duration.ofMillis(5), () -> Optional.of(status(state)), err)) {
                Thread.sleep(60);
            }
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void saysNothingWhenTheDaemonDoesNotAnswer() throws InterruptedException {
        try (var notice = WaitNotice.during(Duration.ofMillis(5), Duration.ofMillis(5), Optional::empty, err)) {
            Thread.sleep(60);
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void saysNothingBeforeItsDelay() throws InterruptedException {
        try (var notice = WaitNotice.during(
                Duration.ofMinutes(1), Duration.ofMinutes(1), () -> Optional.of(status(JdtlsState.STARTING)), err)) {
            Thread.sleep(60);
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void asksTheDaemonOfTheSocketAndSaysNothingWhenNoneAnswers(@TempDir Path directory) throws InterruptedException {
        try (var notice = WaitNotice.during(directory.resolve("nobody.sock"), Duration.ofMillis(10), err)) {
            Thread.sleep(100);
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void asksTheDaemonOfTheSocketAndSaysNothingWhenItAnswersAFailure(@TempDir Path directory)
            throws IOException, InterruptedException {
        var failure = eu.gabier.protocol.JsonRpc.failure("1", CliFixtures.REFUSAL);
        try (var daemon = new ScriptedDaemon(directory.resolve("d.sock"), failure);
                var notice = WaitNotice.during(directory.resolve("d.sock"), Duration.ofMillis(10), err)) {
            Thread.sleep(100);
            assertThat(daemon.requests()).isNotEmpty();
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void saysNothingWhenTheDaemonHangsUpWithoutAnswering(@TempDir Path directory)
            throws IOException, InterruptedException {
        try (var daemon = new ScriptedDaemon(directory.resolve("d.sock"), null);
                var notice = WaitNotice.during(directory.resolve("d.sock"), Duration.ofMillis(10), err)) {
            Thread.sleep(100);
            assertThat(daemon.requests()).isNotEmpty();
        }

        assertThat(written()).isEmpty();
    }

    @Test
    void theLineSaysWhereJdtlsIsAndForHowLong() {
        assertThat(WaitNotice.line(status(JdtlsState.STARTING), Duration.ofSeconds(12)))
                .isEqualTo("gabier: waiting for jdtls (starting, 12s so far); the first command of a session waits for"
                        + " it to start and import the project, the next ones are quick");
    }
}
