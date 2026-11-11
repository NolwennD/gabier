package eu.gabier.daemon;

import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What jdtls answers about where a project is built, read as the layout the selection of tests needs. The answers are
 * those of {@code java.project.getSettings} (asked for {@link #SETTINGS}) and {@code java.project.getClasspaths}; they
 * are the generic JSON values LSP4J hands over (maps, lists, numbers, strings). A shape that is not the one jdtls is
 * documented and observed to give is a {@link JdtlsException}, never a guess.
 */
final class JdtlsBuildAnswers {

    /** The setting that gives the folder the classes of a source folder go to when it names none. */
    static final String OUTPUT_PATH = "org.eclipse.jdt.ls.core.outputPath";

    /** The setting that lists the entries of the class path of the project, the source folders among them. */
    static final String CLASSPATH_ENTRIES = "org.eclipse.jdt.ls.core.classpathEntries";

    /** The settings that {@code java.project.getSettings} is asked for. */
    static final List<String> SETTINGS = List.of(OUTPUT_PATH, CLASSPATH_ENTRIES);

    /** The kind of a class path entry that is a source folder ({@code IClasspathEntry.CPE_SOURCE}). */
    private static final int SOURCE_FOLDER = 3;

    private JdtlsBuildAnswers() {}

    /**
     * The source folders of a project and the folder each is built into: the {@code output} of the entry, else the
     * output path of the project.
     *
     * @param answer what {@code java.project.getSettings} answered
     * @throws JdtlsException if it is not the settings asked for, or an entry names a path that is not absolute
     */
    static List<ClassFolder> classFolders(@Nullable Object answer) {
        if (!(answer instanceof Map<?, ?> settings && settings.get(CLASSPATH_ENTRIES) instanceof List<?> entries)) {
            throw new JdtlsException(
                    "java.project.getSettings answered " + answer + ", which is not the class path of a project");
        }
        List<ClassFolder> folders = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> classpathEntry)) {
                throw new JdtlsException(
                        "java.project.getSettings listed " + entry + ", which is not a class path entry");
            }
            if (classpathEntry.get("kind") instanceof Number kind && kind.intValue() == SOURCE_FOLDER) {
                Path sources = absolute(classpathEntry.get("path"));
                Object output =
                        classpathEntry.get("output") != null ? classpathEntry.get("output") : settings.get(OUTPUT_PATH);
                folders.add(new ClassFolder(sources, absolute(output)));
            }
        }

        return folders.stream().distinct().toList();
    }

    /**
     * The entries of the class path of the tests of a project: its class folders and the jars it is compiled against.
     *
     * @param answer what {@code java.project.getClasspaths} answered for the scope {@code test}
     * @throws JdtlsException if it is not a list of classpaths, or one is not an absolute path
     */
    static List<Path> classpath(@Nullable Object answer) {
        if (!(answer instanceof Map<?, ?> result && result.get("classpaths") instanceof List<?> entries)) {
            throw new JdtlsException(
                    "java.project.getClasspaths answered " + answer + ", which is not a list of classpaths");
        }

        return entries.stream().map(JdtlsBuildAnswers::absolute).toList();
    }

    private static Path absolute(@Nullable Object path) {
        if (path instanceof String text) {
            try {
                Path found = Path.of(text);
                if (found.isAbsolute()) {
                    return found.normalize();
                }
            } catch (InvalidPathException invalid) {
                // Said below, as a path that is not absolute is.
            }
        }

        throw new JdtlsException(
                "jdtls gave " + path + " as a folder or a jar of the build, which is not an absolute path");
    }
}
