package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.AssertionException;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RanTestsTest {

    private static final RanTests.Failure FAILURE = new RanTests.Failure(
            "app.GreeterTest",
            Optional.of("greets"),
            "java.lang.AssertionError",
            "expected \"hello\"\nbut was nothing",
            Optional.of("app.Greeter.greet(Greeter.java:7)"));

    private static final RanTests.Failure CLASS_FAILURE = new RanTests.Failure(
            "app.BrokenTest", Optional.empty(), "java.lang.IllegalStateException", "", Optional.empty());

    private static RanTests readBack(RanTests ran) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", ran.toJson()));

        return RanTests.of(response.result());
    }

    @Test
    void countsWhatRanAndWhatFailed() {
        var ran = new RanTests(3, 1, List.of(FAILURE, CLASS_FAILURE));

        assertThat(ran.failed()).isEqualTo(2);
        assertThat(ran.ran()).isEqualTo(5);
    }

    @Test
    void isWrittenWithItsCountsAndItsFailuresAsValidJson() {
        var json = new RanTests(3, 1, List.of(FAILURE, CLASS_FAILURE)).toJson();

        assertThat(json).isEqualTo("""
            {"ran":5,"passed":3,"failed":2,"skipped":1,"failures":[\
            {"class":"app.GreeterTest","method":"greets","exception":"java.lang.AssertionError",\
            "message":"expected \\"hello\\"\\nbut was nothing","where":"app.Greeter.greet(Greeter.java:7)"},\
            {"class":"app.BrokenTest","exception":"java.lang.IllegalStateException","message":""}]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void isReadBackAsItWasWritten() {
        var ran = new RanTests(3, 1, List.of(FAILURE, CLASS_FAILURE));

        assertThat(readBack(ran)).isEqualTo(ran);
        assertThat(readBack(new RanTests(0, 0, List.of()))).isEqualTo(new RanTests(0, 0, List.of()));
    }

    @Test
    void isRefusedWhenAMemberIsMissingOrOfTheWrongType() {
        var valid = Map.<String, Object>of(
                "ran", BigDecimal.ONE,
                "passed", BigDecimal.ONE,
                "failed", BigDecimal.ZERO,
                "skipped", BigDecimal.ZERO,
                "failures", List.of());
        assertThat(RanTests.of(valid).ran()).isEqualTo(1);
        var invalid = List.of(
                without(valid, "failures"),
                with(valid, "passed", "one"),
                with(valid, "passed", new BigDecimal("1.5")),
                without(valid, "ran"),
                with(valid, "failed", BigDecimal.ONE),
                with(valid, "ran", BigDecimal.TEN),
                with(valid, "failures", List.of("a failure")),
                with(valid, "failures", List.of(Map.of("exception", "E", "message", ""))),
                with(
                        valid,
                        "failures",
                        List.of(Map.of("class", "app.A", "exception", "E", "message", "", "method", 3))));
        for (var result : invalid) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> RanTests.of(result));
        }
    }

    private static Map<String, Object> without(Map<String, Object> map, String name) {
        var copy = new java.util.HashMap<>(map);
        copy.remove(name);

        return copy;
    }

    private static Map<String, Object> with(Map<String, Object> map, Object... pairs) {
        var copy = new java.util.HashMap<>(map);
        for (int index = 0; index < pairs.length; index += 2) {
            copy.put((String) pairs[index], pairs[index + 1]);
        }

        return copy;
    }

    @Test
    void isRefusedWhenItsMembersAreMissingOrNegative() {
        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> new RanTests(-1, 0, List.of()));
        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> new RanTests(0, -1, List.of()));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new RanTests(0, 0, null));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RanTests.Failure(" ", Optional.empty(), "E", "", Optional.empty()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RanTests.Failure("a.B", Optional.empty(), " ", "", Optional.empty()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new RanTests.Failure("a.B", Optional.empty(), "E", null, Optional.empty()));
    }
}
