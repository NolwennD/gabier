package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.RanTests;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunningTestsTest extends CliTest {

    private static final RanTests.Failure FAILURE = new RanTests.Failure(
            "demo.LedgerTest",
            Optional.of("withdrawsMoreThanTheBalance"),
            "org.opentest4j.AssertionFailedError",
            "expected: 10\nbut was: 5",
            Optional.of("demo.Ledger.withdraw(Ledger.java:17)"));

    private static final String TEST_REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"test","params":{"classes":"%s","timeout":"%d"}}\
        """;

    private static String ran(int passed, int skipped, RanTests.Failure... failures) {
        return new RanTests(passed, skipped, List.of(failures)).toJson();
    }

    private static String testRequest(String classes, int timeout) {
        return TEST_REQUEST.formatted(classes, timeout);
    }

    @Test
    void asksTheDaemonToRunTheClassesGivenOneToALineWithTheDefaultTimeLimit() throws IOException {
        try (var daemon = daemonAnswering(ran(3, 0))) {
            assertThat(runWithSocket("test", "demo.LedgerTest", "demo.app.BankTest$Nested"))
                    .isZero();

            assertThat(daemon.requests())
                    .containsExactly(testRequest("demo.LedgerTest\\ndemo.app.BankTest$Nested", 300));
        }
    }

    @Test
    void sendsTheTimeLimitGivenWhereverItStands() throws IOException {
        try (var daemon = daemonAnswering(ran(3, 0))) {
            assertThat(runWithSocket("test", "--timeout=45", "demo.LedgerTest")).isZero();
            assertThat(runWithSocket("test", "demo.LedgerTest", "--timeout=7")).isZero();

            assertThat(daemon.requests())
                    .containsExactly(testRequest("demo.LedgerTest", 45), testRequest("demo.LedgerTest", 7));
        }
    }

    @Test
    void printsTheCountsAndExitsWithZeroWhenNoTestFailed() throws IOException {
        try (var daemon = daemonAnswering(ran(2, 1))) {
            assertThat(runWithSocket("test", "demo.LedgerTest")).isZero();

            assertThat(out().lines()).containsExactly("ran 2 tests: 2 passed, 0 failed, 1 skipped");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void saysOneTestInTheSingular() throws IOException {
        try (var daemon = daemonAnswering(ran(1, 0))) {
            assertThat(runWithSocket("test", "demo.LedgerTest")).isZero();

            assertThat(out().lines()).containsExactly("ran 1 test: 1 passed, 0 failed, 0 skipped");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void printsEachFailureWithItsMessageAndPlaceAndExitsWithThreeWhenATestFailed() throws IOException {
        try (var daemon = daemonAnswering(ran(2, 0, FAILURE))) {
            assertThat(runWithSocket("test", "demo.LedgerTest")).isEqualTo(3);

            assertThat(out().lines())
                    .containsExactly(
                            "ran 3 tests: 2 passed, 1 failed, 0 skipped",
                            "FAILED demo.LedgerTest withdrawsMoreThanTheBalance",
                            "  org.opentest4j.AssertionFailedError: expected: 10",
                            "  but was: 5",
                            "  at demo.Ledger.withdraw(Ledger.java:17)");
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void printsAClassThatFailedWithNoMethodNoMessageAndNoPlace() throws IOException {
        var broken = new RanTests.Failure(
                "demo.BrokenTest", Optional.empty(), "java.lang.IllegalStateException", "", Optional.empty());
        try (var daemon = daemonAnswering(ran(0, 0, broken))) {
            assertThat(runWithSocket("test", "demo.BrokenTest")).isEqualTo(3);

            assertThat(out().lines())
                    .containsExactly(
                            "ran 1 test: 0 passed, 1 failed, 0 skipped",
                            "FAILED demo.BrokenTest",
                            "  java.lang.IllegalStateException");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void showsAnEscapeSequenceOfAMessageAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
        var hostile = new RanTests.Failure("demo.T", Optional.empty(), "E", "\u001b[2J", Optional.empty());
        try (var daemon = daemonAnswering(ran(0, 0, hostile))) {
            assertThat(runWithSocket("test", "demo.T")).isEqualTo(3);

            assertThat(out()).contains("E: \\u001b[2J").doesNotContain("\u001b");
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void givesTheResultAsJsonAsTheDaemonGaveItAndExitsWithThreeWhenATestFailed() throws IOException {
        try (var daemon = daemonAnswering(ran(2, 0, FAILURE))) {
            assertThat(runWithSocket("--json", "test", "demo.LedgerTest")).isEqualTo(3);

            assertThat(out()).isEqualTo(ran(2, 0, FAILURE) + System.lineSeparator());
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void refusesATimeLimitThatIsNotAWholeNumberOfSecondsFromOne() throws IOException {
        try (var daemon = daemonAnswering(ran(0, 0))) {
            for (var timeout : List.of("0", "-1", "soon", "1.5", "")) {
                assertThat(runWithSocket("test", "--timeout=" + timeout, "demo.LedgerTest"))
                        .as(timeout)
                        .isEqualTo(2);
            }

            assertThat(err()).contains("usage-error", "Invalid timeout");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Test
    void refusesWhatNoClassIsNamedByAsAUsageError() throws IOException {
        try (var daemon = daemonAnswering(ran(0, 0))) {
            assertThat(runWithSocket("test", "--dry-run")).isEqualTo(2);
            assertThat(runWithSocket("test", "demo.A", "demo.B\ndemo.C")).isEqualTo(2);
            assertThat(runWithSocket("test", "demo.A\r")).isEqualTo(2);

            assertThat(err()).contains("usage-error");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Nested
    class WithNoClass {

        private static final String REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"test-changed","params":{"timeout":"300"}}\
            """;

        @Test
        void asksTheDaemonForTheTestsOfWhatChangedWithTheTimeLimitOnly() throws IOException {
            try (var daemon = daemonAnswering(ran(1, 0))) {
                assertThat(runWithSocket("test")).isZero();

                assertThat(daemon.requests()).containsExactly(REQUEST);
                assertThat(out().lines()).containsExactly("ran 1 test: 1 passed, 0 failed, 0 skipped");
            }
        }

        @Test
        void sendsTheTimeLimitGiven() throws IOException {
            try (var daemon = daemonAnswering(ran(0, 0))) {
                assertThat(runWithSocket("test", "--timeout=12")).isZero();

                assertThat(daemon.requests()).containsExactly("""
                    {"jsonrpc":"2.0","id":1,"method":"test-changed","params":{"timeout":"12"}}\
                    """);
            }
        }

        @Test
        void saysThatNoTestRanWhenNoneIsAffectedAndExitsWithZero() throws IOException {
            try (var daemon = daemonAnswering(ran(0, 0))) {
                assertThat(runWithSocket("test")).isZero();

                assertThat(out().lines()).containsExactly("no tests ran");
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void exitsWithThreeWhenATestFailed() throws IOException {
            try (var daemon = daemonAnswering(ran(0, 0, FAILURE))) {
                assertThat(runWithSocket("test")).isEqualTo(3);

                assertThat(out()).startsWith("ran 1 test: 0 passed, 1 failed, 0 skipped");
                assertThat(daemon.requests()).hasSize(1);
            }
        }
    }
}
