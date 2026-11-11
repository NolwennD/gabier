package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The classes of the build, read once: every class the build wrote in the class folders of the project, and, on demand,
 * the classes of the libraries that the selection needs to know about (the annotation of a test framework, a base class
 * that comes from a jar). The classes of the JDK are never looked for: nothing in them tells a test.
 */
final class ClassIndex {

    private static final List<String> OF_THE_JDK = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

    /**
     * A class of the project and the class folder the build wrote it in.
     *
     * @param facts what the class says
     * @param classes the class folder that holds it
     */
    record IndexedClass(ClassFacts facts, Path classes) {}

    private final ProjectFiles files;
    private final List<Path> libraries;
    private final Map<String, IndexedClass> ofTheProject = new LinkedHashMap<>();
    private final Map<String, Optional<ClassFacts>> ofTheLibraries = new HashMap<>();

    /**
     * Reads the classes of the class folders of the build.
     *
     * @throws TestingException if a class file cannot be read
     */
    ClassIndex(ProjectFiles files, BuiltProject built) {
        Assert.notNull("files", files);
        Assert.notNull("built", built);

        this.files = files;
        this.libraries = built.libraries();
        for (Path classes :
                built.folders().stream().map(ClassFolder::classes).distinct().toList()) {
            read(classes);
        }
    }

    /** The classes of the build, in the order of their files, the folders in the order of the build. */
    Collection<IndexedClass> projectClasses() {
        return ofTheProject.values();
    }

    /** A class of the project, else one of the libraries; none for a class of the JDK or one that nothing holds. */
    Optional<ClassFacts> find(String name) {
        IndexedClass indexed = ofTheProject.get(name);
        if (indexed != null) {
            return Optional.of(indexed.facts());
        }
        if (OF_THE_JDK.stream().anyMatch(name::startsWith)) {
            return Optional.empty();
        }

        return ofTheLibraries.computeIfAbsent(name, this::inTheLibraries);
    }

    private void read(Path classes) {
        try {
            for (Path file : files.classFilesIn(classes)) {
                ClassFacts facts = ClassFacts.read(file, files.read(file));
                ofTheProject.put(facts.name(), new IndexedClass(facts, classes));
            }
        } catch (IOException failure) {
            throw TestingException.readFailed(classes, failure);
        }
    }

    private Optional<ClassFacts> inTheLibraries(String name) {
        for (Path library : libraries) {
            try {
                Optional<byte[]> bytes = files.readFromLibrary(library, name);
                if (bytes.isPresent()) {
                    return Optional.of(ClassFacts.read(library, bytes.get()));
                }
            } catch (IOException failure) {
                throw TestingException.readFailed(library, failure);
            }
        }

        return Optional.empty();
    }
}
