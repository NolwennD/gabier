package eu.gabier.daemon;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A process whose handle never reports its end the way a process does: the real child it wraps is killed as asked,
 * but {@code onExit()} of its handle stays pending or fails, as for a process that survives the kill signal.
 */
final class StubbornProcess extends Process {

    private final Process real;
    private final ProcessHandle handle;

    /**
     * Wraps a real process whose handle answers to {@code onExit()} as the test says.
     *
     * @param onExit what the handle of this process answers to {@code onExit()}
     */
    StubbornProcess(Process real, CompletableFuture<ProcessHandle> onExit) {
        this.real = real;
        this.handle = new StubbornHandle(real.toHandle(), onExit);
    }

    @Override
    public ProcessHandle toHandle() {
        return handle;
    }

    @Override
    public OutputStream getOutputStream() {
        return real.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return real.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return real.getErrorStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return real.waitFor();
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return real.waitFor(timeout, unit);
    }

    @Override
    public int exitValue() {
        return real.exitValue();
    }

    @Override
    public void destroy() {
        real.destroy();
    }

    @Override
    public Process destroyForcibly() {
        real.destroyForcibly();
        return this;
    }

    @Override
    public boolean isAlive() {
        return real.isAlive();
    }

    @Override
    public long pid() {
        return real.pid();
    }

    @Override
    public CompletableFuture<Process> onExit() {
        return real.onExit();
    }

    private record StubbornHandle(ProcessHandle real, CompletableFuture<ProcessHandle> onExit)
            implements ProcessHandle {

        @Override
        public long pid() {
            return real.pid();
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return real.parent();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return real.children();
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return real.descendants();
        }

        @Override
        public Info info() {
            return real.info();
        }

        @Override
        public boolean supportsNormalTermination() {
            return real.supportsNormalTermination();
        }

        @Override
        public boolean destroy() {
            return real.destroy();
        }

        @Override
        public boolean destroyForcibly() {
            return real.destroyForcibly();
        }

        @Override
        public boolean isAlive() {
            return real.isAlive();
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return real.compareTo(other);
        }
    }
}
