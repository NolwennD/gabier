package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.assertError;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modern;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modernCall;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.notification;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.number;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.object;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A call that waits for the daemon (its start, the import of the project by jdtls, a slow answer) does not stop the
 * server from reading: what an agent asks meanwhile is answered, the agent that asked for it is told where jdtls is,
 * and the answer of the call comes when the daemon has answered, or, after the bound, as a failed call. The daemon of
 * these tests is held by a latch and the time is moved by the test, so nothing waits for a given time.
 */
class WaitingCallsTest {

    private static final String CLEAN = new FileDiagnostics(List.of()).toJson();
    private static final String DIAGNOSE = "{\"file\":\"/work/A.java\"}";

    /** A daemon whose jdtls is importing the project, as it says it while a request waits for it. */
    private static final String IMPORTING = new DaemonStatus(
                    "0.1",
                    4242,
                    Duration.ofSeconds(20),
                    Path.of("/work/demo"),
                    JdtlsState.STARTING,
                    Optional.empty(),
                    ImportSettings.DEFAULTS,
                    Optional.of(
                            new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "Build model", Duration.ofSeconds(15))))
            .toJson();

    private final ManualTime time = new ManualTime();
    private final CountDownLatch release = new CountDownLatch(1);
    private final StubDaemon daemon =
            new StubDaemon().answering(CLEAN).reportingStatus(IMPORTING).holding(release);

    private McpSession session() {
        return new McpSession(McpExchange.serverOf(daemon, time));
    }

    private static Object idOf(Object message) {
        return at(message, "id");
    }

    @Nested
    class Reading {

        @Test
        void answersAPingWhileACallWaitsForTheDaemonThenTheCall() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                session.send(modern("2", "ping"));

                assertThat(idOf(session.next())).isEqualTo(number(2));
                release.countDown();
                var call = session.next();
                assertThat(idOf(call)).isEqualTo(number(1));
                assertThat(at(call, "result", "isError")).isEqualTo(false);
            }
        }

        @Test
        void answersTheStatusAtOnceWithWhereJdtlsIsWhileACallWaits() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                session.send(modernCall("2", "status", "{}"));

                var status = session.next();
                assertThat(idOf(status)).isEqualTo(number(2));
                assertThat((String) at(status, "result", "content", 0, "text"))
                        .contains("jdtls starting (importing the project for 15s: Build model)");
                assertThat(at(status, "result", "structuredContent", "jdtls")).isEqualTo("starting");
                assertThat(at(status, "result", "structuredContent", "jdtlsProgress"))
                        .isEqualTo(object(Json.parse(
                                "{\"phase\":\"importing\",\"message\":\"Build model\"," + "\"spentMillis\":15000}")));
                release.countDown();
            }
        }

        @Test
        void answersTheCallsInTheOrderTheyEndWhateverTheOrderTheyCame() throws Exception {
            var first = new CountDownLatch(1);
            var later = new StubDaemon().answering(CLEAN).holding(first);
            try (var session = new McpSession(McpExchange.serverOf(later, time))) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                session.send(modernCall("2", "references", "{\"file\":\"/work/A.java\",\"line\":1,\"column\":1}"));
                later.awaitAsked();

                // The daemon of the test answers all that are held at once: the answers come in whatever order.
                first.countDown();
                var answers = List.of(session.next(), session.next());

                assertThat(answers).extracting(WaitingCallsTest::idOf).containsExactlyInAnyOrder(number(1), number(2));
            }
        }

        @Test
        void refusesARequestWhoseIdIsTheOneOfACallThatIsStillRunning() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                session.send(modernCall("1.0", "diagnostics", DIAGNOSE));

                var refused = session.next();
                assertError(refused, -32600, "Invalid Request: the id is the one of a request that");
                assertThat(daemon.asked()).hasSize(1);
                release.countDown();
                assertThat(at(session.next(), "result", "isError")).isEqualTo(false);
            }
        }

        @Test
        void waitsForTheCallsThatHaveNotEndedWhenTheInputEnds() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();

                session.endInput();
                assertThat(session.hasLine()).isFalse();
                assertThat(session.isServing()).isTrue();
                release.countDown();

                assertThat(idOf(session.next())).isEqualTo(number(1));
                session.awaitEnd();
                assertThat(time.stopped()).isTrue();
            }
        }

        @Test
        void stopsWaitingWhenItIsInterrupted() throws Exception {
            var out = new ByteArrayOutputStream();
            var input = new ByteArrayInputStream(
                    modernCall("1", "diagnostics", DIAGNOSE).getBytes(UTF_8));
            var server = McpExchange.serverOf(daemon, time);
            var interrupted = Thread.ofPlatform().start(() -> {
                Thread.currentThread().interrupt();
                server.serve(input, new PrintStream(out, true, UTF_8));
            });

            interrupted.join(10_000);

            assertThat(interrupted.isAlive()).isFalse();
            assertThat(out.toString(UTF_8)).isEmpty();
            release.countDown();
        }
    }

    @Nested
    class TellingHowACallGoes {

        @Test
        void tellsAClientThatAskedWhereJdtlsIsAtEachTickWithAProgressThatGrows() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "\"token-1\""));
                daemon.awaitAsked();
                time.advance(Duration.ofSeconds(5));
                time.tick();
                time.advance(Duration.ofSeconds(5));
                time.tick();

                var first = session.next();
                var second = session.next();
                assertThat(at(first, "method")).isEqualTo("notifications/progress");
                assertThat(object(first)).doesNotContainKey("id");
                assertThat(at(first, "params", "progressToken")).isEqualTo("token-1");
                assertThat(at(first, "params", "progress")).isEqualTo(number(1));
                assertThat(at(first, "params", "message"))
                        .isEqualTo("jdtls is starting: importing the project for 15s: Build model (waiting 5s so far)");
                assertThat(at(second, "params", "progress")).isEqualTo(number(2));
                assertThat((String) at(second, "params", "message")).endsWith("(waiting 10s so far)");
                release.countDown();
                assertThat(idOf(session.next())).isEqualTo(number(1));
            }
        }

        @Test
        void sendsTheTokenBackAsItWasWritten() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "1e2"));
                daemon.awaitAsked();
                time.tick();

                assertThat(session.nextRaw()).contains("\"progressToken\":1e2,");
                release.countDown();
            }
        }

        @Test
        void tellsNothingToAClientThatDidNotAsk() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                time.tick();
                release.countDown();

                assertThat(idOf(session.next())).isEqualTo(number(1));
                assertThat(session.hasLine()).isFalse();
            }
        }

        @Test
        void takesATokenThatIsNeitherTextNorAWholeNumberForNoToken() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "1.5"));
                daemon.awaitAsked();
                time.tick();
                release.countDown();

                assertThat(idOf(session.next())).isEqualTo(number(1));
                assertThat(session.hasLine()).isFalse();
            }
        }

        @Test
        void sendsNothingOnceTheCallHasEnded() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "7"));
                daemon.awaitAsked();
                release.countDown();
                assertThat(idOf(session.next())).isEqualTo(number(1));

                session.finish();
                assertThat(session.hasLine()).isFalse();
            }
        }

        @Test
        void saysItWaitsForTheDaemonWhenTheDaemonDoesNotSayWhereJdtlsIs() throws Exception {
            var silent = new StubDaemon()
                    .answering(CLEAN)
                    .failing(new IllegalStateException("no daemon"))
                    .holding(release);
            try (var session = new McpSession(McpExchange.serverOf(silent, time))) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "7"));
                silent.awaitAsked();
                time.advance(Duration.ofSeconds(12));
                time.tick();

                assertThat(at(session.next(), "params", "message")).isEqualTo("waiting for the daemon (12s so far)");
                release.countDown();
            }
        }

        @Test
        void saysItWaitsForTheDaemonWhenTheQuestionEndsWithAnErrorOfTheProcess() throws Exception {
            var broken = new StubDaemon()
                    .crashing(new NoClassDefFoundError("missing"))
                    .holding(release);
            try (var session = new McpSession(McpExchange.serverOf(broken, time))) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "7"));
                broken.awaitAsked();
                time.advance(Duration.ofSeconds(5));
                time.tick();

                assertThat(at(session.next(), "params", "message")).isEqualTo("waiting for the daemon (5s so far)");
                release.countDown();
            }
        }

        @Test
        void keepsTheTextOfJdtlsFromWritingAControlCharacterInTheMessage() throws Exception {
            var hostile = new DaemonStatus(
                            "0.1",
                            4242,
                            Duration.ofSeconds(20),
                            Path.of("/work/demo"),
                            JdtlsState.STARTING,
                            Optional.empty(),
                            ImportSettings.DEFAULTS,
                            Optional.of(new JdtlsProgress(
                                    JdtlsProgress.Phase.LAUNCHING, "task\u001b[2Jdone", Duration.ofSeconds(1))))
                    .toJson();
            var other =
                    new StubDaemon().answering(CLEAN).reportingStatus(hostile).holding(release);
            try (var session = new McpSession(McpExchange.serverOf(other, time))) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "7"));
                other.awaitAsked();
                time.tick();

                assertThat((String) at(session.next(), "params", "message")).doesNotContain("\u001b");
                release.countDown();
            }
        }
    }

    @Nested
    class GivingUp {

        @Test
        void answersACallThatHasWaitedForTheBoundAsAFailedCallThatSaysTheDaemonDidNotAnswer() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                time.advance(Pacing.BOUND);
                time.tick();

                var answer = session.next();
                assertThat(idOf(answer)).isEqualTo(number(1));
                assertThat(at(answer, "result", "isError")).isEqualTo(true);
                assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("daemon-timeout");
                assertThat(at(answer, "result", "structuredContent", "parameters", "timeoutSeconds"))
                        .isEqualTo("660");
                assertThat((String) at(answer, "result", "content", 0, "text"))
                        .contains("The daemon did not answer within 660 seconds");
                release.countDown();
            }
        }

        @Test
        void givesUpACallAtTheBoundWhenTheDaemonDoesNotAnswerTheQuestionOfItsNoticesEither() throws Exception {
            var neverAnswers = new CountDownLatch(1);
            var deaf = new StubDaemon().answering(CLEAN).holding(release).holdingStatus(neverAnswers);
            try (var session = new McpSession(McpExchange.serverOf(deaf, time))) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE, "\"token\""));
                deaf.awaitAsked();
                // Two notices: the first waits the limit for a status that does not come, the second finds the question
                // still pending and asks no other.
                time.advance(Duration.ofSeconds(5));
                time.tick();
                time.advance(Duration.ofSeconds(5));
                time.tick();
                assertThat(at(session.next(), "params", "message")).isEqualTo("waiting for the daemon (5s so far)");
                assertThat(at(session.next(), "params", "message")).isEqualTo("waiting for the daemon (10s so far)");

                time.advance(Pacing.BOUND);
                time.tick();

                var answer = session.next();
                assertThat(idOf(answer)).isEqualTo(number(1));
                assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("daemon-timeout");
                // The calls and the question are still held by the daemon: the end of the input does not wait for them.
                session.finish();
                assertThat(session.hasLine()).isFalse();
                assertThat(deaf.asked()).filteredOn(StubDaemon.Asked::running).hasSize(1);
            } finally {
                neverAnswers.countDown();
                release.countDown();
            }
        }

        @Test
        void doesNotGiveUpACallBeforeTheBound() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                time.advance(Pacing.BOUND.minusNanos(1));
                time.tick();
                release.countDown();

                assertThat(at(session.next(), "result", "isError")).isEqualTo(false);
            }
        }

        @Test
        void saysNothingMoreWhenTheDaemonAnswersAfterTheCallWasGivenUpAndDoesNotWaitForIt() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                time.advance(Pacing.BOUND);
                time.tick();
                assertThat(at(session.next(), "result", "isError")).isEqualTo(true);

                // The thread of the call is still held by the daemon: the end of the input does not wait for it.
                session.finish();
                release.countDown();
                assertThat(session.hasLine()).isFalse();
            }
        }

        @Test
        void keepsAnAnswerThatCameFirstWhenTheBoundRunsOutAfterwards() throws Exception {
            try (var session = session()) {
                session.send(modernCall("1", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                release.countDown();
                assertThat(at(session.next(), "result", "isError")).isEqualTo(false);

                session.finish();
                assertThat(session.hasLine()).isFalse();
            }
        }
    }

    @Nested
    class Cancelling {

        @Test
        void endsACallThatTheClientCancelsWithNoAnswerAndNoNoticeAfterIt() throws Exception {
            try (var session = session()) {
                session.send(modernCall("5", "diagnostics", DIAGNOSE, "9"));
                daemon.awaitAsked();
                session.send(cancellation("5"));
                session.send(modern("6", "ping"));
                assertThat(idOf(session.next())).isEqualTo(number(6));

                time.tick();
                session.finish();
                release.countDown();

                assertThat(session.hasLine()).isFalse();
                assertThat(time.stopped()).isTrue();
            }
        }

        @Test
        void matchesTheRequestByTheValueOfItsIdWhateverItsNotation() throws Exception {
            try (var session = session()) {
                session.send(modernCall("5", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                session.send(cancellation("5.0"));

                session.finish();
                release.countDown();
                assertThat(session.hasLine()).isFalse();
            }
        }

        @Test
        void ignoresACancellationOfAnotherRequestOrOfNoRequestOrThatIsMalformed() throws Exception {
            try (var session = session()) {
                session.send(modernCall("5", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                session.send(cancellation("6"));
                session.send(cancellation("\"5\""));
                session.send(cancellation("null"));
                session.send(notification("notifications/cancelled"));
                session.send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":[]}");
                session.send(notification("notifications/something-else"));
                release.countDown();

                assertThat(idOf(session.next())).isEqualTo(number(5));
            }
        }

        @Test
        void ignoresACancellationOfACallThatHasEnded() throws Exception {
            try (var session = session()) {
                session.send(modernCall("5", "diagnostics", DIAGNOSE));
                daemon.awaitAsked();
                release.countDown();
                assertThat(idOf(session.next())).isEqualTo(number(5));

                session.send(cancellation("5"));
                session.finish();

                assertThat(session.hasLine()).isFalse();
            }
        }

        private static String cancellation(String requestId) {
            return """
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":%s,"reason":"no longer needed"}}\
                """.formatted(requestId);
        }
    }
}
