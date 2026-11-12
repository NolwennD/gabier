package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a connection does for a client that asked to follow something: the daemon writes lines that are not answers, on
 * the connection, through one writer, and a client that hangs up or does not read ends or holds up nothing else.
 */
class ConnectionFollowingTest {

    private static final Problem BROKEN = new Problem(
            "urn:gabier:error:jdtls-down", "Jdtls is down", "It died", "jdtls-down", Map.of(), ErrorStatus.BAD_REQUEST);

    private final AtomicReference<Peer.Listener<List<DiagnosedFile>>> listener = new AtomicReference<>();
    private final CountDownLatch gone = new CountDownLatch(1);
    private final LiveInput in = new LiveInput();
    private final CapturedOutput out = new CapturedOutput();
    private final List<CompletableFuture<Void>> serving = new ArrayList<>();

    /** Listens when asked and writes one notification per offered state, saying how many files it holds. */
    private final class Listening implements StreamingMethod {

        @Override
        public String name() {
            return "follow";
        }

        @Override
        public Outcome call(Map<String, String> params, Peer peer) {
            listener.set(peer.<List<DiagnosedFile>>listen(
                    files -> files.isEmpty()
                            ? List.of()
                            : List.of(JsonRpc.notification("problems", "{\"count\":" + files.size() + "}")),
                    (waiting, offered) -> offered,
                    ended -> gone.countDown()));

            return Outcome.of("{}");
        }
    }

    /** Offers a state while the request is still running, before its answer is written. */
    private final class OfferingAtOnce implements StreamingMethod {

        @Override
        public String name() {
            return "follow-at-once";
        }

        @Override
        public Outcome call(Map<String, String> params, Peer peer) {
            peer.<List<DiagnosedFile>>listen(
                            files -> List.of(JsonRpc.notification("problems", "{\"count\":" + files.size() + "}")),
                            (waiting, offered) -> offered,
                            ended -> gone.countDown())
                    .offer(files(1));

            return Outcome.of("{}");
        }
    }

    private final RequestHandler handler =
            new RequestHandler(List.of(new Listening(), new OfferingAtOnce(), new StatusMethod(() -> {
                throw new AssertionError("not asked");
            })));

    @AfterEach
    void hangUpAndWait() {
        in.hangUp();
        out.release();
        for (var running : serving) {
            running.orTimeout(10, TimeUnit.SECONDS).join();
        }
    }

    private void serve() {
        serving.add(CompletableFuture.runAsync(() -> Connection.serve(in, out, handler)));
    }

    private static String request(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}\n";
    }

    private static List<DiagnosedFile> files(int count) {
        var files = new ArrayList<DiagnosedFile>();
        for (int index = 0; index < count; index++) {
            files.add(new DiagnosedFile(Path.of("/project/A" + index + ".java"), List.of()));
        }

        return files;
    }

    @Test
    void writesWhatIsOfferedAfterTheAnswerToTheRequestThatAskedForIt() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        listener.get().offer(files(2));

        assertThat(out.awaitLines(2)).hasSize(2);
        assertThat(out.lines().get(0)).contains("\"id\":1").contains("\"result\"");
        assertThat(out.lines().get(1))
                .isEqualTo("{\"jsonrpc\":\"2.0\",\"method\":\"problems\",\"params\":{\"count\":2}}");
    }

    @Test
    void writesWhatIsOfferedInTheOrderItIsOffered() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        for (int count = 1; count <= 3; count++) {
            listener.get().offer(files(count));
            out.awaitLines(count + 1);
        }

        assertThat(out.lines().subList(1, 4))
                .extracting(line -> line.substring(line.indexOf("\"count\"")))
                .containsExactly("\"count\":1}}", "\"count\":2}}", "\"count\":3}}");
    }

    @Test
    void doesNotWriteWhatIsOfferedBeforeTheAnswerBeforeTheAnswer() {
        serve();
        in.feed(request(1, "follow-at-once"));

        assertThat(out.awaitLines(2)).hasSize(2);
        assertThat(out.lines().get(0)).contains("\"id\":1");
        assertThat(out.lines().get(1)).contains("\"method\":\"problems\"");
    }

    @Test
    void writesNothingForAStateThatTellsNothing() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        listener.get().offer(files(0));
        listener.get().offer(files(1));

        assertThat(out.awaitLines(2)).hasSize(2);
        assertThat(out.lines().get(1)).contains("\"count\":1");
    }

    @Test
    void goesOnAnsweringTheRequestsOfAConnectionThatIsFollowing() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        in.feed(request(2, "nope"));

        assertThat(out.awaitLines(2).get(1)).contains("\"id\":2").contains("\"error\"");
    }

    @Test
    void refusesASecondSubscriptionOfTheSameConnectionAndKeepsTheFirst() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        in.feed(request(2, "follow"));

        assertThat(out.awaitLines(2).get(1)).contains("\"id\":2").contains("\"key\":\"already-watching\"");
        listener.get().offer(files(1));
        assertThat(out.awaitLines(3).get(2)).contains("\"method\":\"problems\"");
    }

    @Test
    void endsTheSubscriptionWhenTheClientHangsUpAndLeavesNoThread() throws InterruptedException {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        in.hangUp();

        assertThat(gone.await(10, TimeUnit.SECONDS))
                .as("the subscription was told it is over")
                .isTrue();
        serving.getFirst().orTimeout(10, TimeUnit.SECONDS).join();
        assertThat(writerThreads()).as("the writer of the connection ended").isEqualTo(0L);
    }

    @Test
    void doesNotWriteWhatIsOfferedAfterTheConnectionEnded() throws InterruptedException {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);
        in.hangUp();
        gone.await(10, TimeUnit.SECONDS);
        serving.getFirst().orTimeout(10, TimeUnit.SECONDS).join();

        listener.get().offer(files(1));
        listener.get().end(BROKEN);

        assertThat(out.lines()).hasSize(1);
    }

    @Test
    void aClientThatDoesNotReadHoldsUpNoOneThatOffersAndGetsTheLatestStateWhenItReads() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);
        out.blockWrites();
        listener.get().offer(files(1));

        var offered = CompletableFuture.runAsync(() -> {
            for (int count = 2; count <= 5_000; count++) {
                listener.get().offer(files(count));
            }
        });
        offered.orTimeout(30, TimeUnit.SECONDS).join();
        out.release();

        var lines = out.awaitLinesEndingWith("\"count\":5000}}");
        assertThat(lines.size())
                .as("the states nobody could read in between are not written")
                .isLessThan(1_000);
    }

    @Test
    void writesTheNoticesInTheOrderTheyAreGivenBeforeTheStateOffered() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);

        listener.get().say("first");
        listener.get().say("second");
        out.awaitLines(3);
        listener.get().offer(files(1));

        var lines = out.awaitLines(4);
        assertThat(lines.subList(1, 3)).containsExactly(JsonRpc.notice("first"), JsonRpc.notice("second"));
        assertThat(lines.get(3)).contains("\"method\":\"problems\"");
    }

    @Test
    void tellsWhyTheSubscriptionEndedAfterWhatWasOfferedAndNothingAfterwards() {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);
        listener.get().offer(files(1));
        out.awaitLines(2);

        listener.get().end(BROKEN);
        out.awaitLines(3);
        listener.get().offer(files(2));
        in.feed(request(2, "nope"));

        var lines = out.awaitLines(4);
        assertThat(lines.get(2)).isEqualTo(JsonRpc.ended(BROKEN));
        assertThat(lines.get(3)).contains("\"id\":2");
    }

    @Test
    void endsTheConnectionWhenTheClientCannotBeWrittenToAndTellsTheSubscriptionItIsOver() throws InterruptedException {
        serve();
        in.feed(request(1, "follow"));
        out.awaitLines(1);
        out.failWrites();

        listener.get().offer(files(1));

        assertThat(gone.await(10, TimeUnit.SECONDS)).isTrue();
        serving.getFirst().orTimeout(10, TimeUnit.SECONDS).join();
    }

    @Test
    void refusesToListenWhenThereIsNoClientToWriteTo() {
        assertThatThrownBy(() -> Peer.nobody()
                        .<List<DiagnosedFile>>listen(files -> List.of(), (waiting, offered) -> offered, ended -> {}))
                .isInstanceOf(IllegalStateException.class);
    }

    private static long writerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(Outbox.WRITER) && thread.isAlive())
                .count();
    }

    /** What the client sends: the lines it feeds, then the end of its input once it hangs up. */
    private static final class LiveInput extends InputStream {

        private static final int HUNG_UP = -1;
        private static final java.util.function.Predicate<Integer> HUNG_UP_NEXT = next -> next == HUNG_UP;

        private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();

        void feed(String text) {
            for (byte one : text.getBytes(StandardCharsets.UTF_8)) {
                bytes.add(one & 0xff);
            }
        }

        void hangUp() {
            bytes.add(HUNG_UP);
        }

        /** Closing the input ends what waits on it, as closing the channel of a socket does. */
        @Override
        public void close() {
            hangUp();
        }

        /** Waits for a byte, then gives what is there: the default would wait for the buffer to be full. */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int first = read();
            if (first == HUNG_UP) {
                return -1;
            }
            buffer[offset] = (byte) first;
            int count = 1;
            while (count < length && !bytes.isEmpty() && !HUNG_UP_NEXT.test(bytes.peek())) {
                buffer[offset + count++] = (byte) bytes.poll().intValue();
            }

            return count;
        }

        @Override
        public int read() throws IOException {
            try {
                var next = bytes.take();
                if (next == HUNG_UP) {
                    bytes.add(HUNG_UP);
                }

                return next;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new IOException("interrupted", interrupted);
            }
        }
    }

    /** What the client receives, written by the connection: its writes can be held up or made to fail. */
    private static final class CapturedOutput extends OutputStream {

        private final StringBuilder written = new StringBuilder();
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile boolean failing;

        void blockWrites() {
            blocked = new CountDownLatch(1);
        }

        void release() {
            blocked.countDown();
        }

        void failWrites() {
            failing = true;
        }

        @Override
        public void write(int one) throws IOException {
            write(new byte[] {(byte) one}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                blocked.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new IOException("interrupted", interrupted);
            }
            if (failing) {
                throw new IOException("the client is gone");
            }
            synchronized (written) {
                written.append(new String(bytes, offset, length, StandardCharsets.UTF_8));
            }
        }

        List<String> lines() {
            synchronized (written) {
                return written.toString().lines().toList();
            }
        }

        List<String> awaitLines(int count) {
            return await(() -> lines().size() >= count, "lines: " + count);
        }

        List<String> awaitLinesEndingWith(String end) {
            return await(() -> lines().stream().anyMatch(line -> line.endsWith(end)), "a line ending with " + end);
        }

        private List<String> await(java.util.function.BooleanSupplier done, String what) {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!done.getAsBoolean()) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("Waited 10 s for " + what + "; got " + lines());
                }
                Thread.onSpinWait();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    throw new AssertionError("interrupted", interrupted);
                }
            }

            return lines();
        }
    }
}
