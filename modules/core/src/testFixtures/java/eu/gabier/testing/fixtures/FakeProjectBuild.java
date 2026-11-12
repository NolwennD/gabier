package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ProjectBuild;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A hand-written fake of {@link ProjectBuild}: no process, no protocol. It reports what a {@link BuildScenario} says the
 * real server reports, under the root of the project it plays, and fails with an {@link AssertionError} when no scenario
 * was scripted, instead of answering with something that looks plausible. The class files are not the fake's: a test
 * that needs them compiles the sources (see {@link CompiledProject}).
 */
public final class FakeProjectBuild implements ProjectBuild {

    private final Path root;
    private final List<BuildScenario> script = new ArrayList<>();
    private final Map<Path, Long> changes = new HashMap<>();
    private long revision;
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

    /**
     * Plays an edit or a creation on disk of Java files, which the server is told of: they are reported by {@link
     * #builtWithChanges()} until the call that reported them is answered.
     *
     * @param files the paths of the files, under the root of the project
     * @return this fake
     */
    public FakeProjectBuild edited(Path... files) {
        for (Path file : files) {
            changes.put(root.resolve(file).normalize(), ++revision);
        }

        return this;
    }

    /**
     * Plays the deletion of Java files: a deleted file is not among the changes.
     *
     * @param files the paths of the files, under the root of the project
     * @return this fake
     */
    public FakeProjectBuild deleted(Path... files) {
        for (Path file : files) {
            changes.remove(root.resolve(file).normalize());
        }

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

    @Override
    public ChangedBuild builtWithChanges() {
        BuiltProject built = built();
        Map<Path, Long> told = new TreeMap<>(changes);

        return new ChangedBuild(
                built,
                List.copyOf(told.keySet()),
                () -> told.forEach((file, version) -> changes.remove(file, version)));
    }
}
