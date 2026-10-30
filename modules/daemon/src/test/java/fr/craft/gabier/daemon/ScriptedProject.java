package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.protocol.JdtlsState;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** The project of a daemon under test: a language server given by the test, a state the test sets, and a count of closes. */
final class ScriptedProject implements ServedProject {

    private final Path root;
    private final JavaLanguageServer server;
    private final AtomicInteger closes = new AtomicInteger();
    private volatile JdtlsState state = JdtlsState.READY;
    private volatile boolean interrupted;

    ScriptedProject(Path root, JavaLanguageServer server) {
        this.root = root;
        this.server = server;
    }

    ScriptedProject in(JdtlsState state) {
        this.state = state;

        return this;
    }

    /** Closing it is interrupted, as a thread that is told to stop while it waits for the end of jdtls is. */
    void interruptedWhenClosed() {
        interrupted = true;
    }

    int closes() {
        return closes.get();
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public JdtlsState state() {
        return state;
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
    public void close() throws InterruptedException {
        closes.incrementAndGet();
        if (interrupted) {
            throw new InterruptedException("told to stop while jdtls ends");
        }
    }
}
