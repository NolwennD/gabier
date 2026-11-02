package fr.craft.gabier.daemon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The two files of an Eclipse project ({@code .project}, {@code .classpath}) that jdtls imports without network, which
 * a build file of Maven or Gradle would not guarantee: the harness writes them next to the sources of a small project.
 */
final class EclipseProject {

    private static final String PROJECT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>scenario</name>
          <comment></comment>
          <projects></projects>
          <buildSpec>
            <buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments></arguments></buildCommand>
          </buildSpec>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
        </projectDescription>
        """;

    private static final String CLASSPATH = """
        <?xml version="1.0" encoding="UTF-8"?>
        <classpath>
          <classpathentry kind="src" path="src/main/java"/>
        %s%s  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
          <classpathentry kind="output" path="bin"/>
        </classpath>
        """;

    /** The test sources, marked as such: only for a project that has them, as a source folder that is not there is an error. */
    private static final String TESTS = """
          <classpathentry kind="src" path="src/test/java">
            <attributes><attribute name="test" value="true"/></attributes>
          </classpathentry>
        """;

    private static final String LIBRARY = "  <classpathentry kind=\"lib\" path=\"%s\"/>\n";

    private EclipseProject() {}

    /**
     * Makes the folder, whose sources are under {@code src/main/java} and, if there are any, {@code src/test/java}, a
     * project jdtls imports.
     */
    static void describe(Path root) throws IOException {
        describe(root, List.of());
    }

    /** The same, with the jars on the class path (external libraries, which jdtls indexes). */
    static void describe(Path root, List<Path> libraries) throws IOException {
        Files.writeString(root.resolve(".project"), PROJECT);
        Files.writeString(
                root.resolve(".classpath"),
                CLASSPATH.formatted(
                        Files.isDirectory(root.resolve("src/test/java")) ? TESTS : "",
                        libraries.stream().map(LIBRARY::formatted).collect(Collectors.joining())));
    }
}
