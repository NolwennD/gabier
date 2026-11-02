package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.fixtures.FakeOnly;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Ties the label {@link FakeOnly} to the fake: a test that makes {@link FakeJdtls} play a failure that a real jdtls cannot
 * produce on demand (an answer that never comes, a failure that is not a refusal, an initialisation that is not
 * answered, a status that is not ready, a shutdown that fails, a list of projects that is null, changes that are not
 * acknowledged) fails unless it, or a class that encloses it, carries {@code @FakeOnly(reason = "...")} with a reason.
 *
 * <p>What it does not see: an answer that is a value of the wrong shape (an edit that is null or has no message, an
 * edit of another file), and the death of the process. A refusal that a real jdtls gives (a response error {@code
 * InvalidRequest}) is not a failure of the fake, it is what the replay shows. The tests of those cases are labelled by
 * hand and nothing finds one that is not.
 *
 * <p>A class that uses {@link FakeJdtls} has it declared with {@code @ExtendWith(FakeOnlyGuard.class)}; {@code
 * FakeOnlyReasonTest} fails on one that has not.
 */
public final class FakeOnlyGuard implements BeforeEachCallback, AfterEachCallback {

    private static final List<String> SCRIPTED = new CopyOnWriteArrayList<>();

    /** The fake plays a failure: the test that runs now must be labelled. */
    static void failureScripted(String what) {
        SCRIPTED.add(what);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        SCRIPTED.clear();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        List<String> failures = List.copyOf(SCRIPTED);
        SCRIPTED.clear();
        String violation = violation(context.getRequiredTestClass(), context.getRequiredTestMethod(), failures);
        if (!violation.isEmpty()) {
            throw new AssertionError(violation);
        }
    }

    /**
     * What is wrong with a test that made the fake play these failures: it is not labelled, or its label has no reason.
     * Empty when it is fine, or when the fake played no failure.
     */
    static String violation(Class<?> testClass, Method testMethod, List<String> failures) {
        if (failures.isEmpty() || !reasonOf(testClass, testMethod).isBlank()) {
            return "";
        }

        return testClass.getName() + "#" + testMethod.getName() + " makes the fake play a failure of jdtls (" + failures
                + ") and is not marked @FakeOnly(reason = \"...\"), on itself or on a class that encloses it";
    }

    /** The reason of the label on the test method or on a class around it, blank when there is none. */
    private static String reasonOf(Class<?> testClass, Method testMethod) {
        List<AnnotatedElement> labelled = new ArrayList<>();
        labelled.add(testMethod);
        for (Class<?> type = testClass; type != null; type = type.getEnclosingClass()) {
            labelled.add(type);
        }
        for (AnnotatedElement element : labelled) {
            FakeOnly label = element.getAnnotation(FakeOnly.class);
            if (label != null) {
                return label.reason();
            }
        }

        return "";
    }
}
