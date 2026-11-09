package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import java.time.Duration;

/** {@code start --wait} gave up on jdtls: it failed, or it was not ready in time. The daemon goes on either way. */
final class JdtlsWaitException extends GeneratorException {

    private JdtlsWaitException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /**
     * jdtls failed while the command waited. The key and the message are those of a request that waits for the same
     * failure ({@code jdtls-start-failed}); the reason comes from jdtls, and is neutralised where the problem is
     * printed as text.
     */
    static JdtlsWaitException failed(DaemonStatus status) {
        var reason = status.jdtlsFailure().map(why -> ": " + why).orElse(" (the daemon does not know why)");

        return new JdtlsWaitException(GeneratorException.internalServerError(CliErrorKey.JDTLS_START_FAILED)
                .message("jdtls did not start" + reason)
                .addParameter("project", status.project().toString()));
    }

    /** jdtls was still not ready after the time given: the problem says where it was and that it goes on. */
    static JdtlsWaitException timedOut(DaemonStatus status, Duration timeout) {
        var doing =
                status.jdtlsProgress().map(progress -> ", " + progress.text()).orElse("");
        var builder = GeneratorException.internalServerError(CliErrorKey.JDTLS_START_TIMEOUT)
                .message("jdtls was not ready after " + timeout.toSeconds() + " seconds ("
                        + status.jdtls().text()
                        + doing + "); it keeps starting in the daemon: run gabier start --wait again, or look"
                        + " at gabier status")
                .addParameter("jdtls", status.jdtls().text())
                .addParameter("project", status.project().toString())
                .addParameter("timeoutSeconds", String.valueOf(timeout.toSeconds()));
        status.jdtlsProgress().ifPresent(progress -> builder.addParameter("progress", progress.text()));

        return new JdtlsWaitException(builder);
    }
}
