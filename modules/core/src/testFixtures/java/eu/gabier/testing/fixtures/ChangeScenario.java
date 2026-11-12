package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What a language server is expected to report as the Java files that changed on disk, step by step: the contract
 * between the fake of the build ({@link FakeProjectBuild}) and the real server. In {@code check} the fake is played the
 * steps; an integration test writes them on the disk of a project the real server imported and fails if it reports
 * other files. What it states is the effect, the files reported, whatever the way they changed (by hand, by a text edit,
 * by a refactoring: the server is told of them all the same).
 *
 * @param name what the scenario shows, in kebab-case
 * @param files the files of the project at the start, as plain text: paths relative to its root
 * @param steps what happens to the disk, in order
 */
public record ChangeScenario(String name, Map<Path, String> files, List<Step> steps) {

    public ChangeScenario {
        Assert.notBlank("name", name);
        Assert.notNull("files", files);
        Assert.field("steps", steps).notNull().notEmpty().noNullElement();

        files = Map.copyOf(files);
        steps = List.copyOf(steps);
    }

    /** Writes the files of the project under the directory, creating the folders they need. */
    public void writeProject(Path directory) throws IOException {
        for (Map.Entry<Path, String> file : files.entrySet()) {
            Path path = directory.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }

    /**
     * One moment of the scenario: files are written and deleted, then the changes are asked for.
     *
     * @param writes the files created or rewritten, by their path relative to the root of the project
     * @param deletes the files deleted, relative to the root
     * @param reported the files the server must report as changed, relative to the root, in the order of the paths
     * @param answered whether the tests of the files reported are answered after this step, which forgets them
     */
    public record Step(Map<Path, String> writes, List<Path> deletes, List<Path> reported, boolean answered) {

        public Step {
            Assert.notNull("writes", writes);
            Assert.notNull("deletes", deletes);
            Assert.notNull("reported", reported);

            writes = Map.copyOf(writes);
            deletes = List.copyOf(deletes);
            reported = List.copyOf(reported);
        }
    }
}
