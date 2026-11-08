package eu.gabier.daemon;

import eu.gabier.protocol.JdtlsProgress;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * What jdtls is doing while it starts, kept from what it says with {@code language/status} (checked on the real jdtls:
 * it needs no capability of the client, and the {@code $/progress} that a client may ask for carries the same
 * messages). jdtls sends {@code Starting} statuses all along its start, {@code <n>% Starting Java Language Server}
 * and, once it imports the project, {@code <n>% Starting Java Language Server - <task>} (the number is not a
 * percentage: it goes beyond 1000, so it is not read). A status with a task after the dash is the import and the task
 * is the message; the others are the launch, with the status as the message. A stage is never left for the one before
 * it, and the time in a stage counts from the first status that put jdtls in it. The statuses come from the reader
 * thread of LSP4J and the state is read by whichever thread answers a {@code status}.
 */
final class StartProgress {

    private static final String TASK_SEPARATOR = " - ";

    private final LongSupplier nanoClock;
    private JdtlsProgress.Phase phase = JdtlsProgress.Phase.LAUNCHING;
    private String message = "";
    private long since;

    StartProgress(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.since = nanoClock.getAsLong();
    }

    /** The progress of a start that begins now, on the system clock. */
    StartProgress() {
        this(System::nanoTime);
    }

    /** Takes in a status of jdtls: only the {@code Starting} ones tell where the start is. */
    synchronized void status(String type, String text) {
        if (!"Starting".equals(type)) {
            return;
        }
        int dash = text.indexOf(TASK_SEPARATOR);
        if (dash >= 0) {
            enter(JdtlsProgress.Phase.IMPORTING);
            message = text.substring(dash + TASK_SEPARATOR.length());
        } else if (phase == JdtlsProgress.Phase.LAUNCHING) {
            message = text;
        }
    }

    private void enter(JdtlsProgress.Phase next) {
        if (phase != next) {
            phase = next;
            since = nanoClock.getAsLong();
        }
    }

    /** Where the start is now, and for how long it has been there. */
    synchronized JdtlsProgress current() {
        return new JdtlsProgress(phase, message, Duration.ofNanos(nanoClock.getAsLong() - since));
    }
}
