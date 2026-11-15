package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** The reflection that reaches the JUnit Platform of a project, in what it does when a call does not go through. */
class JUnitPlatformTest {

    @Test
    void throwsAnIllegalStateExceptionThatKeepsWhatTheCalledMethodThrew() throws NoSuchMethodException {
        var method = Callable.class.getMethod("call");
        Callable<Object> failing = () -> {
            throw new IllegalArgumentException("refused");
        };

        assertThatIllegalStateException()
                .isThrownBy(() -> JUnitPlatform.call(method, failing))
                .withMessage("refused")
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapsWhatIsNotARuntimeExceptionAndTheMethodThatMayNotBeCalled() throws NoSuchMethodException {
        var method = Callable.class.getMethod("call");
        Callable<Object> failing = () -> {
            throw new IOException("closed");
        };
        var protectedMethod = Object.class.getDeclaredMethod("clone");

        assertThatIllegalStateException()
                .isThrownBy(() -> JUnitPlatform.call(method, failing))
                .withCauseInstanceOf(IOException.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> JUnitPlatform.call(protectedMethod, new Object()))
                .withCauseInstanceOf(IllegalAccessException.class);
    }

    @Test
    void saysWhichClassHasNotTheMethodItIsAskedFor() {
        assertThat(JUnitPlatform.method("java.lang.String", "length")).isNotNull();
        assertThatIllegalStateException()
                .isThrownBy(() -> JUnitPlatform.method("java.lang.String", "nonexistent"))
                .withMessage("java.lang.String has no method nonexistent");
    }
}
