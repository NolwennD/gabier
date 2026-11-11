package eu.gabier.testing.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Real class files for the tests of the testing context: the sources are compiled with the javac of the JDK that runs
 * the tests, which stands in, in {@code check}, for the language server that compiles the project.
 */
public final class CompiledSources {

    private CompiledSources() {}

    /**
     * Compiles the sources into a folder of classes.
     *
     * @param sources the text of each source, by its path under the source folder ({@code app/Greeter.java})
     * @param classes the folder the classes are written to, created when it is missing
     * @param classpath the folders and jars the sources are compiled against
     * @throws IllegalStateException if the sources do not compile: the message holds what javac reports
     */
    public static void compile(Map<String, String> sources, Path classes, List<Path> classpath) throws IOException {
        Files.createDirectories(classes);
        if (sources.isEmpty()) {
            return;
        }
        Path work = Files.createTempDirectory("gabier-compiled-");
        try {
            List<Path> files = new ArrayList<>();
            for (var source : sources.entrySet()) {
                Path file = work.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                files.add(Files.writeString(file, source.getValue()));
            }
            run(files, classes, classpath);
        } finally {
            delete(work);
        }
    }

    /**
     * Compiles the sources and packs the classes in a jar, as a library of the project is.
     *
     * @return the jar
     */
    public static Path jar(Path jar, Map<String, String> sources) throws IOException {
        Path classes = Files.createTempDirectory("gabier-jarred-");
        try {
            compile(sources, classes, List.of());
            try (var output = new JarOutputStream(Files.newOutputStream(jar));
                    Stream<Path> files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    output.putNextEntry(new JarEntry(classes.relativize(file).toString()));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
        } finally {
            delete(classes);
        }

        return jar;
    }

    private static void run(List<Path> files, Path classes, List<Path> classpath) throws IOException {
        var javac = ToolProvider.getSystemJavaCompiler();
        var errors = new DiagnosticCollector<JavaFileObject>();
        List<String> options = new ArrayList<>(List.of("-d", classes.toString(), "-proc:none"));
        if (!classpath.isEmpty()) {
            options.add("-classpath");
            options.add(classpath.stream().map(Path::toString).collect(Collectors.joining(":")));
        }
        try (var manager = javac.getStandardFileManager(errors, null, null)) {
            boolean compiled = javac.getTask(
                            null, manager, errors, options, null, manager.getJavaFileObjectsFromPaths(files))
                    .call();
            if (!compiled) {
                throw new IllegalStateException("The sources do not compile: " + errors.getDiagnostics());
            }
        }
    }

    private static void delete(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.delete(file);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        }
    }
}
