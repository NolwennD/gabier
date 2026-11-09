package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NumberValueTooHighException;
import eu.gabier.shared.error.domain.NumberValueTooLowException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DaemonStatusTest {

    private static final DaemonStatus STATUS =
            new DaemonStatus("0.1", 4242, Duration.ofMillis(65_432), Path.of("/work/demo"), JdtlsState.READY);

    private static final String DEFAULT_IMPORTS_JSON = """
        {"importOrder":{"value":["java","javax","org","com"],"source":"default"},\
        "starThreshold":{"value":99,"source":"default"},\
        "staticStarThreshold":{"value":99,"source":"default"}}\
        """;

    private static Map<String, Object> setting(Object value) {
        return Map.of("value", value, "source", "default");
    }

    private static Map<String, Object> defaultImports() {
        return Map.of(
                "importOrder", setting(List.of("java", "javax", "org", "com")),
                "starThreshold", setting(new BigDecimal("99")),
                "staticStarThreshold", setting(new BigDecimal("99")));
    }

    private static Map<String, Object> result() {
        var result = new HashMap<String, Object>();
        result.put("version", "0.1");
        result.put("pid", new BigDecimal("4242"));
        result.put("uptimeMillis", new BigDecimal("65432"));
        result.put("project", "/work/demo");
        result.put("jdtls", "ready");
        result.put("imports", defaultImports());

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
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready",\
            "imports":%s}\
            """.formatted(DEFAULT_IMPORTS_JSON));
        StrictJson.parse(STATUS.toJson());
    }

    @Test
    void saysWhichImportSettingsTheProjectFollowsAndWhereTheyComeFrom() {
        var imports = new ImportSettings(
                new ImportSettings.Setting<>(List.of("#", ""), ImportSettings.Source.VSCODE),
                new ImportSettings.Setting<>(7, ImportSettings.Source.SETTINGS),
                ImportSettings.DEFAULTS.staticStarThreshold());
        var status = new DaemonStatus(
                "0.1",
                4242,
                Duration.ofMillis(65_432),
                Path.of("/work/demo"),
                JdtlsState.READY,
                Optional.empty(),
                imports);
        var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", status.toJson()))).result();

        assertThat(status.imports()).isEqualTo(imports);
        assertThat(STATUS.imports()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(status.toJson()).contains("\"imports\":" + imports.toJson());
        assertThat(DaemonStatus.of(result)).isEqualTo(status);
    }

    @Test
    void refusesMissingImportSettings() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus(
                        "0.1", 1, Duration.ZERO, Path.of("/p"), JdtlsState.READY, Optional.empty(), null));
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
    void saysWhyJdtlsFailedInAMemberOfItsOwnThatIsOnlyThereWhenItIsKnown() {
        var failed = new DaemonStatus(
                "0.1",
                4242,
                Duration.ofMillis(65_432),
                Path.of("/work/demo"),
                JdtlsState.FAILED,
                Optional.of("jdtls exited with code 137"));

        assertThat(failed.toJson()).isEqualTo("""
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
            "imports":%s,"jdtlsFailure":"jdtls exited with code 137"}\
            """.formatted(DEFAULT_IMPORTS_JSON));
        StrictJson.parse(failed.toJson());
        assertThat(STATUS.jdtlsFailure()).isEmpty();
        assertThat(STATUS.toJson()).doesNotContain("jdtlsFailure");
    }

    @Test
    void carriesTheReasonExactlyAsJdtlsGaveIt() {
        var reason = "jdtls said \"no\"\n\u001b[2J and \\ left";
        var failed = new DaemonStatus(
                "0.1", 4242, Duration.ofMillis(65_432), Path.of("/work/demo"), JdtlsState.FAILED, Optional.of(reason));
        var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", failed.toJson()))).result();

        assertThat(DaemonStatus.of(result)).isEqualTo(failed);
        assertThat(DaemonStatus.of(result).jdtlsFailure()).contains(reason);
    }

    @Test
    void readsTheReasonWhenTheResultHasOne() {
        var result = result("jdtls", "failed");
        result.put("jdtlsFailure", "no build");

        assertThat(DaemonStatus.of(result).jdtlsFailure()).contains("no build");
        assertThat(DaemonStatus.of(result()).jdtlsFailure()).isEmpty();
    }

    @Test
    void isRefusedWhenTheReasonIsNotText() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DaemonStatus.of(result("jdtlsFailure", 3)));
    }

    @Test
    void refusesAMissingReasonHolder() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus("0.1", 1, Duration.ZERO, Path.of("/p"), JdtlsState.FAILED, null));
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
                result("jdtls", "asleep"),
                resultWithout("imports"),
                result("imports", "none"));

        for (var result : results) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> DaemonStatus.of(result));
        }
    }

    @Test
    void isRefusedWhenTheProjectIsNotAPath() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DaemonStatus.of(result("project", "a\u0000b")));
    }

    private static DaemonStatus starting(JdtlsProgress progress) {
        return new DaemonStatus(
                "0.1",
                4242,
                Duration.ofMillis(65_432),
                Path.of("/work/demo"),
                JdtlsState.STARTING,
                Optional.empty(),
                ImportSettings.DEFAULTS,
                Optional.of(progress));
    }

    @Test
    void saysWhatJdtlsDoesWhileItStartsInAMemberThatIsOnlyThereThen() {
        var importing = starting(
                new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "Configure project :core", Duration.ofMillis(47_250)));

        assertThat(importing.toJson()).isEqualTo("""
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"starting",\
            "imports":%s,"jdtlsProgress":{"phase":"importing","message":"Configure project :core","spentMillis":47250}}\
            """.formatted(DEFAULT_IMPORTS_JSON));
        StrictJson.parse(importing.toJson());
        assertThat(STATUS.jdtlsProgress()).isEmpty();
        assertThat(STATUS.toJson()).doesNotContain("jdtlsProgress");
    }

    @Test
    void readsTheProgressItWrites() {
        for (var phase : JdtlsProgress.Phase.values()) {
            var status = starting(new JdtlsProgress(phase, "said \"no\"\n\u001b[2J", Duration.ofSeconds(3)));
            var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", status.toJson()))).result();

            assertThat(DaemonStatus.of(result)).isEqualTo(status);
        }
        assertThat(JdtlsProgress.Phase.values())
                .extracting(JdtlsProgress.Phase::text)
                .containsExactly("launching", "importing");
    }

    @Test
    void isRefusedWhenTheProgressIsNotWhatItIsWritten() {
        var members = List.of(
                Map.of("message", "m", "spentMillis", new BigDecimal("1")),
                Map.of("phase", "asleep", "message", "m", "spentMillis", new BigDecimal("1")),
                Map.of("phase", "importing", "spentMillis", new BigDecimal("1")),
                Map.of("phase", "importing", "message", 3, "spentMillis", new BigDecimal("1")),
                Map.of("phase", "importing", "message", "m"),
                Map.of("phase", "importing", "message", "m", "spentMillis", "soon"));

        for (var progress : members) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .isThrownBy(() -> DaemonStatus.of(result("jdtlsProgress", progress)));
        }
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DaemonStatus.of(result("jdtlsProgress", "importing")));
    }

    @Test
    void refusesAMissingProgressHolder() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus(
                        "0.1",
                        1,
                        Duration.ZERO,
                        Path.of("/p"),
                        JdtlsState.STARTING,
                        Optional.empty(),
                        ImportSettings.DEFAULTS,
                        null));
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

    private static DaemonStatus idling(IdleStop idle) {
        return new DaemonStatus(
                "0.1",
                4242,
                Duration.ofMillis(65_432),
                Path.of("/work/demo"),
                JdtlsState.READY,
                Optional.empty(),
                ImportSettings.DEFAULTS,
                Optional.empty(),
                Optional.of(idle));
    }

    @Test
    void saysTheIdleDelayAndWhatIsLeftOfItInAMemberThatIsOnlyThereWhenTheDaemonStopsWhenIdle() {
        var idle = idling(new IdleStop(Duration.ofMinutes(60), Duration.ofMillis(3_541_250)));

        assertThat(idle.toJson()).isEqualTo("""
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready",\
            "imports":%s,"idle":{"timeoutMillis":3600000,"leftMillis":3541250}}\
            """.formatted(DEFAULT_IMPORTS_JSON));
        StrictJson.parse(idle.toJson());
        assertThat(STATUS.idle()).isEmpty();
        assertThat(STATUS.toJson()).doesNotContain("idle");
    }

    @Test
    void readsTheIdleDelayItWrites() {
        var status = idling(new IdleStop(Duration.ofMinutes(60), Duration.ZERO));
        var result = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", status.toJson()))).result();

        assertThat(DaemonStatus.of(result)).isEqualTo(status);
    }

    @Test
    void isRefusedWhenTheIdleDelayIsNotWhatItIsWritten() {
        var members = List.of(
                Map.of("leftMillis", new BigDecimal("1")),
                Map.of("timeoutMillis", new BigDecimal("1")),
                Map.of("timeoutMillis", "soon", "leftMillis", new BigDecimal("1")));

        for (var idle : members) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .isThrownBy(() -> DaemonStatus.of(result("idle", idle)));
        }
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> DaemonStatus.of(result("idle", "60")));
    }

    @Test
    void refusesAMissingIdleHolder() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new DaemonStatus(
                        "0.1",
                        1,
                        Duration.ZERO,
                        Path.of("/p"),
                        JdtlsState.READY,
                        Optional.empty(),
                        ImportSettings.DEFAULTS,
                        Optional.empty(),
                        null));
    }

    @Test
    void refusesAnIdleDelayThatIsNotPositiveOrWhoseTimeLeftIsOutsideIt() {
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new IdleStop(Duration.ZERO, Duration.ZERO));
        assertThatExceptionOfType(NumberValueTooLowException.class)
                .isThrownBy(() -> new IdleStop(Duration.ofMinutes(1), Duration.ofMillis(-1)));
        assertThatExceptionOfType(NumberValueTooHighException.class)
                .isThrownBy(() -> new IdleStop(Duration.ofMinutes(1), Duration.ofMinutes(2)));
    }
}
