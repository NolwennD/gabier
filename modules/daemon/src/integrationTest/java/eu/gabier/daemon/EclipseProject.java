package eu.gabier.daemon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The two files of an Eclipse project ({@code .project}, {@code .classpath}) that jdtls imports without network, which
 * a build file of Maven or Gradle would not guarantee: the harness writes them next to the sources of a small project.
 */
final class EclipseProject {

    private static final String PROJECT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>%s</name>
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
        %s%s%s
          <classpathentry kind="output" path="bin"/>
        </classpath>
        """;

    /**
     * The test sources, marked as such: only for a project that has them, as a source folder that is not there is an
     * error. They have an output folder of their own: jdtls refuses to build a project whose test sources share the
     * output of the main ones ("must have a separate output folder"), and publishes that error for the project instead
     * of the problems of its files.
     */
    private static final String TESTS = """
          <classpathentry kind="src" path="src/test/java" output="test-bin">
            <attributes><attribute name="test" value="true"/></attributes>
          </classpathentry>
        """;

    private static final String LIBRARY = "  <classpathentry kind=\"lib\" path=\"%s\"/>\n";

    private static final String JRE =
            "  <classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/>\n";

    /** The JDK of a modular project, on its module path. */
    private static final String MODULAR_JRE = """
          <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER">
            <attributes><attribute name="module" value="true"/></attributes>
          </classpathentry>
        """;

    /** Another project of the workspace whose module this one requires, on its module path. */
    private static final String REQUIRED_PROJECT = """
          <classpathentry kind="src" path="/%s" combineaccessrules="false">
            <attributes><attribute name="module" value="true"/></attributes>
          </classpathentry>
        """;

    private static final Path MODULE_INFO = Path.of("src/main/java/module-info.java");
    private static final Pattern MODULE = Pattern.compile("\\bmodule\\s+([\\w.]+)");
    private static final Pattern REQUIRES = Pattern.compile("\\brequires\\s+(?:transitive\\s+)?([\\w.]+)\\s*;");

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
        writeFiles(root, "scenario", libraries, List.of());
    }

    /**
     * Makes the folder a project, as {@link #describe(Path)} does, when it holds {@code src}; otherwise each folder of
     * it that holds {@code src} is a project of its own, named after its folder, and linked to the projects whose module
     * its {@code module-info.java} requires, as a build of several modules links them.
     *
     * @return the folders of the projects, which jdtls must import
     */
    static List<Path> describeProjects(Path root) throws IOException {
        if (Files.isDirectory(root.resolve("src"))) {
            describe(root);

            return List.of(root);
        }
        List<Path> projects;
        try (Stream<Path> folders = Files.list(root)) {
            projects = folders.filter(folder -> Files.isDirectory(folder.resolve("src")))
                    .sorted()
                    .toList();
        }
        Map<String, Path> byModule = new TreeMap<>();
        for (Path project : projects) {
            moduleOf(project).ifPresent(module -> byModule.put(module, project));
        }
        for (Path project : projects) {
            List<String> required = new ArrayList<>();
            for (String module : requiredModules(project)) {
                Path other = byModule.get(module);
                if (other != null) {
                    required.add(other.getFileName().toString());
                }
            }
            writeFiles(project, project.getFileName().toString(), List.of(), required);
        }
        return projects;
    }

    private static void writeFiles(Path root, String name, List<Path> libraries, List<String> requiredProjects)
            throws IOException {
        boolean modular = Files.isRegularFile(root.resolve(MODULE_INFO));
        Files.writeString(root.resolve(".project"), PROJECT.formatted(name));
        Files.writeString(
                root.resolve(".classpath"),
                CLASSPATH.formatted(
                        Files.isDirectory(root.resolve("src/test/java")) ? TESTS : "",
                        Stream.concat(
                                        libraries.stream().map(LIBRARY::formatted),
                                        requiredProjects.stream().map(REQUIRED_PROJECT::formatted))
                                .collect(Collectors.joining()),
                        modular ? MODULAR_JRE : JRE));
    }

    private static Optional<String> moduleOf(Path project) throws IOException {
        Path descriptor = project.resolve(MODULE_INFO);
        if (!Files.isRegularFile(descriptor)) {
            return Optional.empty();
        }
        Matcher module = MODULE.matcher(Files.readString(descriptor));
        return module.find() ? Optional.of(module.group(1)) : Optional.empty();
    }

    private static List<String> requiredModules(Path project) throws IOException {
        Path descriptor = project.resolve(MODULE_INFO);
        if (!Files.isRegularFile(descriptor)) {
            return List.of();
        }
        return REQUIRES.matcher(Files.readString(descriptor))
                .results()
                .map(requires -> requires.group(1))
                .toList();
    }
}
