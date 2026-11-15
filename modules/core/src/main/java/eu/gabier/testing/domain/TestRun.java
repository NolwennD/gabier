package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What to run: test classes, on the class path the build gives them.
 *
 * @param classes the binary names of the test classes, at least one
 * @param classpath the class folders and the jars the tests run with, in this order
 * @param workingDirectory the folder the tests run in: the root of the project
 * @param timeLimit how long the tests may take in all, more than zero
 */
public record TestRun(List<String> classes, List<Path> classpath, Path workingDirectory, Duration timeLimit) {

    public TestRun {
        Assert.field("classes", classes).notNull().notEmpty().noNullElement();
        Assert.field("classpath", classpath).notNull().noNullElement();
        Assert.notNull("workingDirectory", workingDirectory);
        Assert.notNull("timeLimit", timeLimit);
        Assert.field("timeLimit", timeLimit.toMillis()).min(1);

        classes = List.copyOf(classes);
        classpath = List.copyOf(classpath);
    }
}
