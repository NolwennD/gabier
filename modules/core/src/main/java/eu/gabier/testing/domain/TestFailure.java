package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.Optional;

/**
 * A test that failed: where it is, what it threw, and where in the code it failed. The message and the place come from
 * the tests and the libraries they use: hostile text, whatever prints them for a person neutralises them.
 *
 * @param testClass the binary name of the test class ({@code app.GreeterTest}, {@code app.Outer$Inner})
 * @param method the name of the test method; none when the class itself failed (a {@code @BeforeAll}, a constructor)
 * @param exception the binary name of the class of what was thrown ({@code org.opentest4j.AssertionFailedError})
 * @param message what it said, empty when it said nothing
 * @param where the first frame of the stack that is in the project, as {@code app.Ledger.withdraw(Ledger.java:17)};
 *     none when the stack has no frame
 */
public record TestFailure(
        String testClass, Optional<String> method, String exception, String message, Optional<String> where) {

    public TestFailure {
        Assert.notBlank("testClass", testClass);
        Assert.notNull("method", method);
        Assert.notBlank("exception", exception);
        Assert.notNull("message", message);
        Assert.notNull("where", where);
    }
}
