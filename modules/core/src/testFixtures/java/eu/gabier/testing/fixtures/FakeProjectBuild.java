package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ProjectBuild;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written fake of {@link ProjectBuild}: no process, no protocol. It reports what a {@link BuildScenario} says the
 * real server reports, under the root of the project it plays, and fails with an {@link AssertionError} when no scenario
 * was scripted, instead of answering with something that looks plausible. The class files are not the fake's: a test
 * that needs them compiles the sources (see {@link CompiledProject}).
 */
public final class FakeProjectBuild implements ProjectBuild {

    private final Path root;
    private final List<BuildScenario> script = new ArrayList<>();
    private int asked;

    /**
     * Creates a fake that knows no build yet.
     *
     * @param root the root of the project the scenarios play on: the directory where a test writes their files
     */
    public FakeProjectBuild(Path root) {
        Assert.notNull("root", root);

        this.root = root;
    }

    /**
     * Scripts the build a scenario says the server reports. The last scenario scripted is the one reported.
     *
     * @return this fake
     */
    public FakeProjectBuild scripted(BuildScenario scenario) {
        Assert.notNull("scenario", scenario);
        script.add(scenario.inside(root));

        return this;
    }

    /** How many times the build was asked for. */
    public int asked() {
        return asked;
    }

    @Override
    public BuiltProject built() {
        asked++;
        if (script.isEmpty()) {
            throw new AssertionError(
                    "The fake build was not scripted: add the scenario that says what the real server" + " reports");
        }

        return script.getLast().built();
    }
}
