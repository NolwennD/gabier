package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.fixtures.FakeOnly;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The label {@link FakeOnly} is enforced, not only declared. A test that makes {@link FakeJdtls} play a failure that a
 * real jdtls cannot produce on demand is refused by {@link FakeOnlyGuard} unless it is labelled with a reason; here, a
 * blank reason fails, and so does a test class that uses the fake without the guard.
 */
class FakeOnlyReasonTest {

    @FakeOnly(reason = " ")
    static class WithoutAReason {

        @FakeOnly(reason = "a timeout cannot be asked of a real server")
        void withOne() {}

        @FakeOnly(reason = "")
        void withAnEmptyOne() {}
    }

    /** Examples of tests, labelled or not, for the guard to judge. */
    static class Examples {

        void unlabelled() {}

        @FakeOnly(reason = "a timeout cannot be asked of a real server")
        void labelled() {}

        @FakeOnly(reason = " ")
        void labelledWithoutReason() {}
    }

    @FakeOnly(reason = "the whole class plays failures")
    static class LabelledClass {

        class Inside {

            void test() {}
        }
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    /** What is marked {@link FakeOnly} with a blank reason, among the classes and their methods. */
    private static List<String> blankReasons(List<Class<?>> classes) {
        List<String> blank = new ArrayList<>();
        for (Class<?> type : classes) {
            List<AnnotatedElement> elements = new ArrayList<>(List.of(type.getDeclaredMethods()));
            elements.add(type);
            for (AnnotatedElement element : elements) {
                FakeOnly label = element.getAnnotation(FakeOnly.class);
                if (label != null && label.reason().isBlank()) {
                    blank.add(element.toString());
                }
            }
        }

        return blank;
    }

    /** The compiled files of the tests of the daemon, in the folders of every package under it (nested classes apart). */
    private static List<Path> testClassFiles() throws Exception {
        Path folder = Path.of(FakeOnlyReasonTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        try (var files = Files.walk(folder)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().startsWith(FakeOnlyReasonTest.class.getSimpleName()))
                    .toList();
        }
    }

    /** The test classes of the daemon, nested ones included (their own files), but not this test's examples. */
    private static List<Class<?>> testClasses() throws Exception {
        Path folder = Path.of(FakeOnlyReasonTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        List<Class<?>> classes = new ArrayList<>();
        for (Path file : testClassFiles()) {
            String name = folder.relativize(file).toString().replace('/', '.');
            classes.add(Class.forName(
                    name.substring(0, name.length() - ".class".length()),
                    false,
                    FakeOnlyReasonTest.class.getClassLoader()));
        }

        return classes;
    }

    @Test
    void findsTheClassesAndTheMethodsMarkedWithABlankReason() {
        assertThat(blankReasons(List.of(WithoutAReason.class)))
                .hasSize(2)
                .anyMatch(element -> element.contains("WithoutAReason") && !element.contains("("))
                .anyMatch(element -> element.contains("withAnEmptyOne"))
                .noneMatch(element -> element.contains("withOne"));
    }

    @Test
    void readsEveryTestOfTheDaemon() throws Exception {
        assertThat(testClasses()).contains(JdtlsLanguageServerTest.class, JdtlsSessionTest.class);
    }

    @Test
    void givesEveryFakeOnlyCaseOfTheDaemonItsReason() throws Exception {
        assertThat(blankReasons(testClasses())).isEmpty();
    }

    @Test
    void refusesATestThatMakesTheFakePlayAFailureAndIsNotLabelled() throws Exception {
        assertThat(FakeOnlyGuard.violation(
                        Examples.class, method(Examples.class, "unlabelled"), List.of("rename fails")))
                .contains("FakeOnlyReasonTest$Examples#unlabelled")
                .contains("rename fails")
                .contains("@FakeOnly");
    }

    @Test
    void refusesALabelThatHasNoReason() throws Exception {
        assertThat(FakeOnlyGuard.violation(
                        Examples.class, method(Examples.class, "labelledWithoutReason"), List.of("rename fails")))
                .contains("labelledWithoutReason");
    }

    @Test
    void acceptsALabelledTestAndATestWhereTheFakePlaysNoFailure() throws Exception {
        assertThat(FakeOnlyGuard.violation(Examples.class, method(Examples.class, "labelled"), List.of("rename fails")))
                .isEmpty();
        assertThat(FakeOnlyGuard.violation(Examples.class, method(Examples.class, "unlabelled"), List.of()))
                .isEmpty();
    }

    @Test
    void acceptsATestInsideALabelledClass() throws Exception {
        assertThat(FakeOnlyGuard.violation(
                        LabelledClass.Inside.class,
                        method(LabelledClass.Inside.class, "test"),
                        List.of("rename fails")))
                .isEmpty();
    }

    @Test
    void putsTheGuardOnEveryTestClassThatUsesTheFake() throws Exception {
        String fake = "fr/craft/gabier/daemon/FakeJdtls";
        List<String> unguarded = new ArrayList<>();
        List<Class<?>> classes = testClasses();
        for (Path file : testClassFiles()) {
            String simple = file.getFileName().toString();
            if (simple.startsWith("FakeJdtls") || simple.startsWith("FakeOnlyGuard")) {
                continue;
            }
            if (new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1).contains(fake)) {
                Class<?> outermost = outermost(classes, file);
                ExtendWith extension = outermost.getAnnotation(ExtendWith.class);
                if (extension == null || !List.of(extension.value()).contains(FakeOnlyGuard.class)) {
                    unguarded.add(outermost.getName());
                }
            }
        }

        assertThat(unguarded)
                .as("test classes that use FakeJdtls without @ExtendWith(FakeOnlyGuard.class)")
                .isEmpty();
    }

    private static Class<?> outermost(List<Class<?>> classes, Path file) {
        String name = file.getFileName().toString();
        String simple = name.substring(0, name.length() - ".class".length());
        String top = simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple;

        return classes.stream()
                .filter(type ->
                        type.getEnclosingClass() == null && type.getSimpleName().equals(top))
                .findFirst()
                .orElseThrow();
    }
}
