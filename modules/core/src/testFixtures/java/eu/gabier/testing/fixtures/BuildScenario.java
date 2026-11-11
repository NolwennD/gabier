package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a language server is expected to report of the build of a small project: where it writes the classes of each
 * source folder and which files it found an error in. It is the contract between the fake of the build ({@link
 * FakeProjectBuild}) and the real server: the fake is scripted with it in {@code check}, and an integration test starts
 * the server on the files and fails if it reports another build. The selection of tests reads the classes it finds
 * there, so a build that is not as the scenario says is a selection that reads nothing.
 *
 * @param name what the scenario shows, in kebab-case
 * @param files the files of the project, as plain text: paths relative to its root
 * @param built the build the server is expected to report, with paths relative to the root
 */
public record BuildScenario(String name, Map<Path, String> files, BuiltProject built) {

    public BuildScenario {
        Assert.notBlank("name", name);
        Assert.notNull("files", files);
        Assert.notNull("built", built);

        files = Map.copyOf(files);
    }

    /** The same scenario with every path put under the root and normalized, the project files included. */
    public BuildScenario inside(Path root) {
        return new BuildScenario(
                name,
                files.entrySet().stream()
                        .collect(Collectors.toMap(entry -> resolve(root, entry.getKey()), Map.Entry::getValue)),
                new BuiltProject(
                        built.folders().stream()
                                .map(folder -> new ClassFolder(
                                        resolve(root, folder.sources()), resolve(root, folder.classes())))
                                .toList(),
                        built.libraries().stream()
                                .map(library -> resolve(root, library))
                                .toList(),
                        built.filesWithErrors().stream()
                                .map(file -> resolve(root, file))
                                .toList()));
    }

    /** Writes the files of the project under the directory, creating the folders they need. */
    public void writeProject(Path directory) throws IOException {
        for (Map.Entry<Path, String> file : files.entrySet()) {
            Path path = directory.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }

    private static Path resolve(Path root, Path path) {
        return root.resolve(path).normalize();
    }
}
