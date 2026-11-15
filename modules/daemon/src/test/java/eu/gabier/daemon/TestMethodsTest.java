package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.RanTests;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.testing.application.RunTests;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.TestingException;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.fixtures.FakeTestRunner;
import eu.gabier.testing.fixtures.TestRunScenarios;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The methods {@code test} and {@code test-changed} through the handler, against the fake runner and a compiled project. */
class TestMethodsTest {

    private static final Path GREETER = Path.of("src/main/java/app/Greeter.java");

    @TempDir
    Path project;

    private final FakeTestRunner runner =
            new FakeTestRunner().reporting(TestRunScenarios.ONE_PASSING_AND_ONE_FAILING_TEST);

    private FakeProjectBuild build;

    private RequestHandler handler(BuiltProject built) {
        build = new FakeProjectBuild(project).scripted(new BuildScenario("compiled", Map.of(), built));
        var runTests = new RunTests(build, new NioProjectFiles(), project, runner);

        return new RequestHandler(List.of(new TestMethod(runTests), new TestChangedMethod(runTests)));
    }

    private RequestHandler handler() throws IOException {
        return handler(CompiledProject.of(
                project,
                Map.of("app/Greeter.java", "package app; public class Greeter {}"),
                Map.of("app/GreeterTest.java", """
                    package app;

                    public class GreeterTest {
                        @org.junit.Test
                        public void greets() {
                            new Greeter();
                        }
                    }
                    """)));
    }

    private static String test(RequestHandler handler, String classes, String timeout) {
        return handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"test","params":{"classes":"%s","timeout":"%s"}}\
            """.formatted(classes, timeout)).line();
    }

    private static String testChanged(RequestHandler handler, String timeout) {
        return handler.handle("""
            {"jsonrpc":"2.0","id":1,"method":"test-changed","params":{"timeout":"%s"}}\
            """.formatted(timeout)).line();
    }

    private static RpcResponse.Failure refusal(String line) {
        return (RpcResponse.Failure) RpcResponse.parse(line);
    }

    @Test
    void areNamedTestAndTestChangedAndTakeTheTimeLimit() throws IOException {
        var runTests = new RunTests(new FakeProjectBuild(project), new NioProjectFiles(), project, runner);

        assertThat(new TestMethod(runTests).name()).isEqualTo("test");
        assertThat(new TestMethod(runTests).parameters()).containsExactlyInAnyOrder("classes", "timeout");
        assertThat(new TestChangedMethod(runTests).name()).isEqualTo("test-changed");
        assertThat(new TestChangedMethod(runTests).parameters()).containsExactly("timeout");
    }

    @Test
    void answersWhatTheRunGaveWithItsFailureAndRunsTheClassesGivenWithTheTimeLimitGiven() throws IOException {
        var handler = handler();

        var response = (RpcResponse.Success) RpcResponse.parse(test(handler, "app.GreeterTest", "90"));

        var ran = RanTests.of(response.result());
        assertThat(ran.passed()).isEqualTo(1);
        assertThat(ran.skipped()).isEqualTo(1);
        assertThat(ran.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.testClass()).isEqualTo("app.CounterTest");
            assertThat(failure.method()).contains("resetsACounter");
            assertThat(failure.where()).contains("app.Counter.reset(Counter.java:11)");
        });
        assertThat(runner.runs()).singleElement().satisfies(run -> {
            assertThat(run.classes()).containsExactly("app.GreeterTest");
            assertThat(run.timeLimit()).isEqualTo(Duration.ofSeconds(90));
        });
    }

    @Test
    void refusesATimeLimitThatIsNotAWholeNumberOfSecondsAboveZero() throws IOException {
        var handler = handler();

        for (var timeout : List.of("0", "-3", "soon", "1.5", "")) {
            var failure = refusal(test(handler, "app.GreeterTest", timeout));

            assertThat(failure.problem().key()).as(timeout).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).containsEntry("parameter", "timeout");
        }
        assertThat(refusal(testChanged(handler, "0")).problem().parameters()).containsEntry("parameter", "timeout");
        assertThat(runner.runs()).isEmpty();
    }

    @Test
    void refusesAListOfClassesThatIsEmptyOrHasABlankLine() throws IOException {
        var handler = handler();

        for (var classes : List.of("", "app.GreeterTest\\n ")) {
            var failure = refusal(test(handler, classes, "30"));

            assertThat(failure.problem().key()).as(classes).isEqualTo("invalid-parameter");
            assertThat(failure.problem().parameters()).containsEntry("parameter", "classes");
        }
    }

    @Test
    void refusesAClassOfNoBuildAsAProblemObject() throws IOException {
        var failure = refusal(test(handler(), "app.Gone", "30"));

        assertThat(failure.problem().key()).isEqualTo("testing-no-such-test-class");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void refusesWithTheRefusalOfTheRunner() throws IOException {
        runner.refusing(TestingException.timedOut(Duration.ofSeconds(30)));

        var failure = refusal(test(handler(), "app.GreeterTest", "30"));

        assertThat(failure.problem().key()).isEqualTo("testing-test-timeout");
    }

    @Test
    void runsTheTestsOfWhatChangedAndAnswersNoTestAfterwards() throws IOException {
        var handler = handler();
        build.edited(GREETER);

        var first = (RpcResponse.Success) RpcResponse.parse(testChanged(handler, "30"));
        var second = (RpcResponse.Success) RpcResponse.parse(testChanged(handler, "30"));

        assertThat(RanTests.of(first.result()).failed()).isEqualTo(1);
        assertThat(RanTests.of(second.result()).ran()).isZero();
        assertThat(runner.runs()).hasSize(1);
    }

    @Test
    void doesNothingAfterTheReply() throws IOException {
        handler().handle("""
            {"jsonrpc":"2.0","id":1,"method":"test-changed","params":{"timeout":"30"}}\
            """).afterReply().run();
    }
}
