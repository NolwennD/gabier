package eu.gabier.daemon;

import eu.gabier.protocol.TerminalText;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * What the libraries of the daemon log with {@code java.util.logging} (LSP4J, for a message of jdtls it does not know)
 * goes to the log of the daemon as one line each, written as text, in the place of the two lines the console handler
 * of the runtime writes on the standard error, the second of them with no time.
 */
final class JulLog extends Handler {

    private final Consumer<String> lines;

    private JulLog(Consumer<String> lines) {
        this.lines = lines;
    }

    /** Replaces the handlers of the root logger by one that gives each record to the consumer. */
    static void install(Consumer<String> lines) {
        var root = Logger.getLogger("");
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.addHandler(new JulLog(lines));
    }

    @Override
    public void publish(LogRecord record) {
        var failure = record.getThrown();
        var message = new SimpleFormatter().formatMessage(record);
        var text = record.getLevel() + ": " + message + (failure == null ? "" : ": " + failure);

        lines.accept(TerminalText.neutralise(text));
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
