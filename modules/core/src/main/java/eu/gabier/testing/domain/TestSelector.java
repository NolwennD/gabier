package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tells the tests that the change of some source files affects, from what the build of the project wrote: the classes
 * the files were compiled to are found with the source file name the compiler recorded in them and the package of the
 * folder they are in, so that the other top-level types of a file and its nested classes are taken too.
 */
public final class TestSelector {

    /** The file of filters Infinitest reads, at the root of the project. */
    private static final String FILTERS = "infinitest.filters";

    private final ProjectFiles files;
    private final Path root;

    /**
     * Creates the selector of a project.
     *
     * @param files where the classes, the sources and the filters are
     * @param root the root of the project, which holds the file of filters
     */
    public TestSelector(ProjectFiles files, Path root) {
        Assert.notNull("files", files);
        Assert.notNull("root", root);

        this.files = files;
        this.root = root;
    }

    /**
     * The tests that depend, directly or not, on the classes of the given sources, those of the sources themselves
     * when they are tests, and the tests that load their classes by themselves.
     *
     * @param built what the build gives, once it has compiled the sources
     * @param sources the files that changed, as absolute paths, whose {@code .} and {@code ..} segments are cleaned here: a
     *     file that is not a Java source is left out
     * @return the binary names of the tests, in the order of the names
     * @throws TestingException if a Java file does not exist or is under no source folder of the project, if the build
     *     found an error in one of them, or if a class or the file of filters cannot be read
     */
    public List<String> affectedBy(BuiltProject built, List<Path> sources) {
        Assert.notNull("built", built);
        Assert.field("sources", sources).notNull().noNullElement();

        List<Path> javaFiles = sources.stream()
                .map(Path::normalize)
                .filter(TestSelector::isJava)
                .toList();
        List<ClassFolder> folders =
                javaFiles.stream().map(file -> folderOf(built, file)).toList();
        List<Path> failed =
                javaFiles.stream().filter(built.filesWithErrors()::contains).toList();
        if (!failed.isEmpty()) {
            throw TestingException.compilationFailed(failed);
        }
        ClassIndex index = new ClassIndex(files, built);
        Set<String> changed = new HashSet<>();
        for (int position = 0; position < javaFiles.size(); position++) {
            changed.addAll(classesOf(index, folders.get(position), javaFiles.get(position)));
        }

        return TestSelection.of(index, changed, filters());
    }

    /**
     * As {@link #affectedBy}, for the files that changed and that nobody named: what cannot be asked of the build is left
     * out instead of refused, since no one gave it. A file that no longer exists (its tests, if it had any, are gone,
     * and the classes that used it are recompiled by the build, so a change to them is told on its own), and a Java file
     * that is under no source folder, change nothing. A file the build found an error in is still refused.
     *
     * @param built what the build gives, once it has compiled the sources
     * @param sources the files that changed, as absolute paths
     * @return the binary names of the tests, in the order of the names, none when no source is left to ask about
     * @throws TestingException if the build found an error in one of the files, or if a class or the file of filters
     *     cannot be read
     */
    public List<String> affectedByChanges(BuiltProject built, List<Path> sources) {
        Assert.notNull("built", built);
        Assert.field("sources", sources).notNull().noNullElement();

        List<Path> askable = sources.stream()
                .map(Path::normalize)
                .filter(TestSelector::isJava)
                .filter(files::isFile)
                .filter(file -> sourceFolderOf(built, file).isPresent())
                .toList();

        return askable.isEmpty() ? List.of() : affectedBy(built, askable);
    }

    private static boolean isJava(Path file) {
        return file.getFileName().toString().endsWith(".java");
    }

    /** The source folder the file is in, the innermost when they nest. */
    private ClassFolder folderOf(BuiltProject built, Path file) {
        if (!files.isFile(file)) {
            throw TestingException.fileMissing(file);
        }

        return sourceFolderOf(built, file).orElseThrow(() -> TestingException.notInASourceFolder(file));
    }

    private static Optional<ClassFolder> sourceFolderOf(BuiltProject built, Path file) {
        return built.folders().stream()
                .filter(folder -> file.startsWith(folder.sources()))
                .max(Comparator.comparingInt(folder -> folder.sources().getNameCount()));
    }

    /** The classes the build wrote in the class folder for the file: those of its package that name it as their source. */
    private static Set<String> classesOf(ClassIndex index, ClassFolder folder, Path file) {
        Path inSources = folder.sources().relativize(file);
        String packageName = inSources.getParent() == null
                ? ""
                : inSources.getParent().toString().replace('/', '.');
        String fileName = file.getFileName().toString();
        Set<String> classes = new HashSet<>();
        for (ClassIndex.IndexedClass indexed : index.projectClasses()) {
            ClassFacts facts = indexed.facts();
            if (indexed.classes().equals(folder.classes())
                    && facts.sourceFile().equals(Optional.of(fileName))
                    && packageOf(facts.name()).equals(packageName)) {
                classes.add(facts.name());
            }
        }

        return classes;
    }

    private static String packageOf(String className) {
        int dot = className.lastIndexOf('.');

        return dot < 0 ? "" : className.substring(0, dot);
    }

    private TestFilters filters() {
        Path file = root.resolve(FILTERS);
        try {
            Optional<String> text = files.textOf(file);

            return text.isPresent() ? TestFilters.parse(file, text.get()) : TestFilters.none();
        } catch (IOException failure) {
            throw TestingException.readFailed(file, failure);
        }
    }
}
