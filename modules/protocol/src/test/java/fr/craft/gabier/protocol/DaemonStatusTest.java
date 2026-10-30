package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import fr.craft.gabier.shared.error.domain.NumberValueTooLowException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DaemonStatusTest {

    private static final DaemonStatus STATUS =
            new DaemonStatus("0.1", 4242, Duration.ofMillis(65_432), Path.of("/work/demo"), JdtlsState.READY);

    private static Map<String, Object> result() {
        var result = new HashMap<String, Object>();
        result.put("version", "0.1");
        result.put("pid", new BigDecimal("4242"));
        result.put("uptimeMillis", new BigDecimal("65432"));
        result.put("project", "/work/demo");
        result.put("jdtls", "ready");

        return result;
    }

    private static Map<String, Object> result(String member, Object value) {
        var result = result();
        result.put(member, value);

        return result;
    }

    private static Map<String, Object> resultWithout(String member) {
        var result = result();
        result.remove(member);

        return result;
    }

    @Test
    void isWrittenAsTheResultOfTheStatusMethod() {
        assertThat(STATUS.toJson()).isEqualTo("""
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready"}\
            """);
        StrictJson.parse(STATUS.toJson());
    }

    @Test
    void isReadFromTheResult() {
        assertThat(DaemonStatus.of(result())).isEqualTo(STATUS);
    }

    @Test
    void isReadFromWhatItWrites() {
        var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", STATUS.toJson()))).result();

        assertThat(DaemonStatus.of(result)).isEqualTo(STATUS);
    }

    @Test
    void saysEachStateOfJdtlsInItsOwnWords() {
        for (var state : JdtlsState.values()) {
            var status = new DaemonStatus("0.1", 4242, Duration.ofMillis(65_432), Path.of("/work/demo"), state);

            assertThat(DaemonStatus.of(result("jdtls", state.text()))).isEqualTo(status);
            assertThat(status.toJson()).contains("\"jdtls\":\"" + state.text() + "\"");
        }
        assertThat(List.of(JdtlsState.values()).stream().map(JdtlsState::text))
                .containsExactly("not-started", "starting", "ready", "failed");
    }

    @Test
    void isRefusedWhenAMemberIsMissingOrOfAnotherType() {
        var results = List.of(
                resultWithout("version"),
                resultWithout("pid"),
                result("uptimeMillis", "soon"),
                result("pid", new BigDecimal("1.5")),
                resultWithout("project"),
                result("project", 3),
                resultWithout("jdtls"),
                result("jdtls", "asleep"));

        for (var result : results) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> DaemonStatus.of(result));
        }
    }

    @Test
    void isRefusedWhenTheProjectIsNotAPath() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DaemonStatus.of(result("project", "a\u0000b")));
    }

    @Test
    void refusesABlankVersion() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus(" ", 1, Duration.ZERO, Path.of("/p"), JdtlsState.READY));
    }

    @Test
    void refusesAPidThatIsNotPositive() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 0, Duration.ZERO, Path.of("/p"), JdtlsState.READY));
    }

    @Test
    void refusesAMissingUptime() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, null, Path.of("/p"), JdtlsState.READY));
    }

    @Test
    void refusesANegativeUptime() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, Duration.ofMillis(-1), Path.of("/p"), JdtlsState.READY));
    }

    @Test
    void refusesAMissingProject() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, Duration.ZERO, null, JdtlsState.READY));
    }

    @Test
    void refusesAMissingStateOfJdtls() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, Duration.ZERO, Path.of("/p"), null));
    }
}
