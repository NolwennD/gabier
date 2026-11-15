package eu.gabier.daemon;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The JUnit Platform of the project under test, used by reflection: gabier ships no JUnit of its own, so the process of
 * the tests runs with the launcher and the engines the class path of the project holds, whatever their version. The
 * classes are looked up by name in the loader of the class path, and every method is taken from the public type that
 * declares it (the launcher a factory gives is an internal class).
 */
final class JUnitPlatform {

    private static final String LAUNCHER_FACTORY = "org.junit.platform.launcher.core.LauncherFactory";

    private static final String TEST_PLAN = "org.junit.platform.launcher.TestPlan";
    private static final String TEST_IDENTIFIER = "org.junit.platform.launcher.TestIdentifier";
    private static final String TEST_EXECUTION_RESULT = "org.junit.platform.engine.TestExecutionResult";
    private static final String CLASS_SOURCE = "org.junit.platform.engine.support.descriptor.ClassSource";
    private static final String METHOD_SOURCE = "org.junit.platform.engine.support.descriptor.MethodSource";

    private JUnitPlatform() {}

    /** Whether the class path of the thread has a JUnit Platform launcher. */
    static boolean hasLauncher() {
        try {
            type(LAUNCHER_FACTORY);

            return true;
        } catch (IllegalStateException noLauncher) {
            return false;
        }
    }

    /**
     * Discovers the tests of the classes and tells what the engines found.
     *
     * @return none when the class path holds no test engine at all
     */
    static Optional<Discovered> discover(List<String> classes) {
        try {
            return Optional.of(discovering(classes));
        } catch (IllegalStateException failure) {
            boolean noEngine = Optional.ofNullable(failure.getCause())
                    .filter(cause -> cause.getClass().getSimpleName().equals("PreconditionViolationException"))
                    .isPresent();
            if (noEngine) {
                return Optional.empty();
            }
            throw failure;
        }
    }

    private static Discovered discovering(List<String> classes) {
        Object launcher = call(method(LAUNCHER_FACTORY, "create"), null);
        List<Object> selectors = new ArrayList<>();
        for (String name : classes) {
            selectors.add(call(
                    method("org.junit.platform.engine.discovery.DiscoverySelectors", "selectClass", String.class),
                    null,
                    name));
        }
        String builderName = "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder";
        Object builder = call(method(builderName, "request"), null);
        call(method(builderName, "selectors", List.class), builder, selectors);
        Object request = call(method(builderName, "build"), builder);
        Object plan = call(
                method(
                        "org.junit.platform.launcher.Launcher",
                        "discover",
                        type("org.junit.platform.launcher.LauncherDiscoveryRequest")),
                launcher,
                request);

        return new Discovered(launcher, plan);
    }

    /** The tests the engines found, ready to run. */
    static final class Discovered {

        private final Object launcher;
        private final Object plan;

        private Discovered(Object launcher, Object plan) {
            this.launcher = launcher;
            this.plan = plan;
        }

        /** Whether the plan holds a class or a method of the class, which an engine recognised. */
        boolean recognises(String className) {
            Collection<?> all = identifiers();

            return all.stream()
                    .anyMatch(identifier ->
                            sourceClassOf(identifier).filter(className::equals).isPresent());
        }

        /** The identifier the given one is under; none for a root. */
        Optional<Object> parentOf(Object identifier) {
            return ((Optional<?>) call(method(TEST_PLAN, "getParent", type(TEST_IDENTIFIER)), plan, identifier))
                    .map(Object.class::cast);
        }

        /** The number of tests under the identifier: itself when it is a test. */
        int testsIn(Object identifier) {
            if (isTrue(identifier, "isTest")) {
                return 1;
            }

            return (int) ((Collection<?>)
                            call(method(TEST_PLAN, "getDescendants", type(TEST_IDENTIFIER)), plan, identifier))
                    .stream().filter(descendant -> isTrue(descendant, "isTest")).count();
        }

        /**
         * Runs the tests and tells every identifier that finished, with the throwable of a failure, and every one that
         * was skipped.
         */
        void run(Consumer<Finished> finished, BiConsumer<Object, String> skipped) {
            Class<?> listener = type("org.junit.platform.launcher.TestExecutionListener");
            Object proxy = Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[] {listener},
                    (self, called, arguments) -> {
                        switch (called.getName()) {
                            case "executionFinished" -> finished.accept(new Finished(arguments[0], arguments[1]));
                            case "executionSkipped" -> skipped.accept(arguments[0], (String) arguments[1]);
                            default -> {
                                // The other events are not told.
                            }
                        }

                        return null;
                    });
            Object listeners = Array.newInstance(listener, 1);
            Array.set(listeners, 0, proxy);
            call(
                    method("org.junit.platform.launcher.Launcher", "execute", type(TEST_PLAN), listeners.getClass()),
                    launcher,
                    plan,
                    listeners);
        }

        private Collection<?> identifiers() {
            List<Object> all = new ArrayList<>();
            Collection<?> roots = (Collection<?>) call(method(TEST_PLAN, "getRoots"), plan);
            for (Object root : roots) {
                all.add(root);
                all.addAll(
                        (Collection<?>) call(method(TEST_PLAN, "getDescendants", type(TEST_IDENTIFIER)), plan, root));
            }

            return all;
        }
    }

    /**
     * An identifier of the plan that finished.
     *
     * @param identifier the {@code TestIdentifier}
     * @param result the {@code TestExecutionResult}
     */
    record Finished(Object identifier, Object result) {

        boolean isTest() {
            return isTrue(identifier, "isTest");
        }

        boolean failed() {
            return call(method(TEST_EXECUTION_RESULT, "getStatus"), result)
                    .toString()
                    .equals("FAILED");
        }

        boolean aborted() {
            return call(method(TEST_EXECUTION_RESULT, "getStatus"), result)
                    .toString()
                    .equals("ABORTED");
        }

        /** What was thrown: an engine always gives it with a failure. */
        Throwable thrown() {
            return (Throwable)
                    ((Optional<?>) call(method(TEST_EXECUTION_RESULT, "getThrowable"), result)).orElseThrow();
        }
    }

    /** The class an identifier comes from: the class it is, or the class of its method; none when it has no source. */
    static Optional<String> sourceClassOf(Object identifier) {
        return source(identifier).flatMap(source -> {
            if (isInstance(CLASS_SOURCE, source)) {
                return Optional.of((String) call(method(CLASS_SOURCE, "getClassName"), source));
            }
            if (isInstance(METHOD_SOURCE, source)) {
                return Optional.of((String) call(method(METHOD_SOURCE, "getClassName"), source));
            }

            return Optional.empty();
        });
    }

    /** The method an identifier comes from; none when it is no method. */
    static Optional<String> sourceMethodOf(Object identifier) {
        return source(identifier)
                .filter(source -> isInstance(METHOD_SOURCE, source))
                .map(source -> (String) call(method(METHOD_SOURCE, "getMethodName"), source));
    }

    /** What a person calls the identifier, for a failure that has no class. */
    static String displayNameOf(Object identifier) {
        return (String) call(method(TEST_IDENTIFIER, "getDisplayName"), identifier);
    }

    private static Optional<Object> source(Object identifier) {
        return ((Optional<?>) call(method(TEST_IDENTIFIER, "getSource"), identifier)).map(Object.class::cast);
    }

    private static boolean isInstance(String className, Object value) {
        return type(className).isInstance(value);
    }

    private static boolean isTrue(Object target, String name) {
        return (Boolean) call(method(TEST_IDENTIFIER, name), target);
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("The class path has no " + name, missing);
        }
    }

    static Method method(String className, String name, Class<?>... parameters) {
        try {
            return type(className).getMethod(name, parameters);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException(className + " has no method " + name, missing);
        }
    }

    /** Calls the method; what it throws is thrown as it is, anything else is an {@link IllegalStateException}. */
    static Object call(Method method, @Nullable Object target, @Nullable Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invoked ? invoked.getCause() : failure;
            if (cause instanceof RuntimeException thrown) {
                throw new IllegalStateException(thrown.getMessage(), thrown);
            }
            throw new IllegalStateException(cause);
        }
    }
}
