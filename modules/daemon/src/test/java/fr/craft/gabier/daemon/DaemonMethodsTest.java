package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.DaemonStatus;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DaemonMethodsTest {

    private static final DaemonStatus STATUS = new DaemonStatus("0.1", 4242, Duration.ofSeconds(5));

    @Test
    void statusIsNamedStatusAndGivesTheStatusAsJson() {
        var method = new StatusMethod(() -> STATUS);

        assertThat(method.name()).isEqualTo("status");
        assertThat(method.call().resultJson()).isEqualTo(STATUS.toJson());
    }

    @Test
    void statusDoesNothingAfterTheReply() {
        new StatusMethod(() -> STATUS).call().afterReply().run();
    }

    @Test
    void stopIsNamedStopAndSaysItIsStopping() {
        var method = new StopMethod(() -> {});

        assertThat(method.name()).isEqualTo("stop");
        assertThat(method.call().resultJson()).isEqualTo("{\"stopping\":true}");
    }

    @Test
    void stopStopsTheDaemonAfterTheReplyAndNotBefore() {
        var stops = new AtomicInteger();
        var outcome = new StopMethod(stops::incrementAndGet).call();

        assertThat(stops).hasValue(0);

        outcome.afterReply().run();

        assertThat(stops).hasValue(1);
    }
}
