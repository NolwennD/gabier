package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tests that a change affects, as Infinitest selects them ({@code ClassFileTestDetector.findTestsToRun}): the
 * classes that changed, and every class that depends on one of them, directly or not, form the changed set; the tests of
 * that set are affected, and so are, whatever changed, the tests whose dependencies cannot be read (see {@link
 * TestRecognition#loadsClassesByItself}), once anything changed. The tests the filters leave out are not answered.
 *
 * <p>A class depends on the classes its bytecode names ({@link ClassFacts#dependencies()}); an edge is kept between
 * classes of the project only. What the bytecode does not name is not seen: a constant that the compiler inlined, an
 * interface and its implementation (a test that calls the interface does not depend on a class that implements it),
 * reflection and the injection of dependencies.
 */
final class TestSelection {

    private TestSelection() {}

    /**
     * Selects the tests the change of some classes affects.
     *
     * @param changed the binary names of the classes that changed
     * @return the binary names of the tests, each once, in the order of the names
     */
    static List<String> of(ClassIndex index, Set<String> changed, TestFilters filters) {
        Assert.notNull("index", index);
        Assert.notNull("changed", changed);
        Assert.notNull("filters", filters);

        Map<String, Set<String>> dependents = dependents(index);
        Set<String> affected = new HashSet<>(changed);
        Deque<String> toFollow = new ArrayDeque<>(changed);
        while (!toFollow.isEmpty()) {
            for (String dependent : dependents.getOrDefault(toFollow.pop(), Set.of())) {
                if (affected.add(dependent)) {
                    toFollow.push(dependent);
                }
            }
        }
        var recognition = new TestRecognition(index);

        return index.projectClasses().stream()
                .map(ClassIndex.IndexedClass::facts)
                .filter(facts -> recognition.isATest(facts) && !filters.excludes(facts.name()))
                .filter(facts -> affected.contains(facts.name())
                        || (!changed.isEmpty() && recognition.loadsClassesByItself(facts)))
                .map(ClassFacts::name)
                .sorted()
                .toList();
    }

    /** For each class of the project, the classes of the project that depend on it. */
    private static Map<String, Set<String>> dependents(ClassIndex index) {
        Map<String, Set<String>> dependents = new HashMap<>();
        for (ClassIndex.IndexedClass indexed : index.projectClasses()) {
            ClassFacts facts = indexed.facts();
            for (String dependency : facts.dependencies()) {
                if (!dependency.equals(facts.name())) {
                    dependents
                            .computeIfAbsent(dependency, name -> new HashSet<>())
                            .add(facts.name());
                }
            }
        }

        return dependents;
    }
}
