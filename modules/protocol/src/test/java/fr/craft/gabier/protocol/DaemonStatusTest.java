package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import fr.craft.gabier.shared.error.domain.NumberValueTooLowException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DaemonStatusTest {

    private static final DaemonStatus STATUS = new DaemonStatus("0.1", 4242, Duration.ofMillis(65_432));

    @Test
    void isWrittenAsTheResultOfTheStatusMethod() {
        assertThat(STATUS.toJson()).isEqualTo("{\"version\":\"0.1\",\"pid\":4242,\"uptimeMillis\":65432}");
        StrictJson.parse(STATUS.toJson());
    }

    @Test
    void isReadFromTheResult() {
        var result = Map.<String, Object>of(
                "version", "0.1", "pid", new BigDecimal("4242"), "uptimeMillis", new BigDecimal("65432"));

        assertThat(DaemonStatus.of(result)).isEqualTo(STATUS);
    }

    @Test
    void isReadFromWhatItWrites() {
        var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", STATUS.toJson()))).result();

        assertThat(DaemonStatus.of(result)).isEqualTo(STATUS);
    }

    @Test
    void isRefusedWhenAMemberIsMissingOrOfAnotherType() {
        var version = Map.<String, Object>of("pid", new BigDecimal("1"), "uptimeMillis", new BigDecimal("1"));
        var pid = Map.<String, Object>of("version", "0.1", "uptimeMillis", new BigDecimal("1"));
        var uptime = Map.<String, Object>of("version", "0.1", "pid", new BigDecimal("1"), "uptimeMillis", "soon");
        var fractional = Map.<String, Object>of(
                "version", "0.1", "pid", new BigDecimal("1.5"), "uptimeMillis", new BigDecimal("1"));

        for (var result : java.util.List.of(version, pid, uptime, fractional)) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> DaemonStatus.of(result));
        }
    }

    @Test
    void refusesABlankVersion() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus(" ", 1, Duration.ZERO));
    }

    @Test
    void refusesAPidThatIsNotPositive() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 0, Duration.ZERO));
    }

    @Test
    void refusesAMissingUptime() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, null));
    }

    @Test
    void refusesANegativeUptime() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, Duration.ofMillis(-1)));
    }
}
