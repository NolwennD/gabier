package eu.gabier.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The JDKs the projects of a workspace are bound to, so that a jdtls that starts again on the workspace binds them to
 * the same ones. A project asks for an execution environment ({@code JavaSE-25}), and jdtls binds it to one of the
 * JDKs it knows of that version; with several of them (the JDKs of SDKMAN! and of mise, say) the one it picks is not the
 * same from one start to the next (the choice is the first of a hash set, whose order depends on the identity of the
 * objects: {@code ExecutionEnvironment.getCompatibleVM} of Eclipse JDT Debug), and a project bound to another JDK is
 * built again as a whole. The setting {@code java.configuration.runtimes} names the JDK of an environment (jdtls
 * makes it the default of that environment, {@code JVMConfigurator.configureJVMs}, and keeps it in its workspace): once
 * jdtls is ready the session reads the JDK of each project ({@code java.project.getSettings}, {@value #VM_LOCATION})
 * and declares them, so that the workspace binds the next start the same way.
 */
final class JdtlsRuntimes {

    /** The setting of a project that gives the folder of the JDK it is bound to, asked of {@code java.project.getSettings}. */
    static final String VM_LOCATION = "org.eclipse.jdt.ls.core.vm.location";

    private static final Pattern JAVA_VERSION = Pattern.compile("(?m)^JAVA_VERSION=\"?([^\"\\r\\n]*)");
    private static final Pattern MAJOR = Pattern.compile("^(?:1\\.(\\d+)|(\\d+))");
    private static final Pattern RUNTIME_VERSION = Pattern.compile("(?m)^JAVA_RUNTIME_VERSION=\"([^\"]*)\"");

    private JdtlsRuntimes() {}

    /**
     * A JDK and the execution environment it stands for.
     *
     * @param name the execution environment, as jdtls names it ({@code JavaSE-25}, {@code JavaSE-1.8})
     * @param home the folder of the JDK
     * @param version what tells the JDK at that folder from another one that replaced it (the file {@code release} of the
     *     JDK, which names its version and its build)
     */
    record Jdk(String name, Path home, String version) {

        /** The JDK at the folder, as its file {@code release} describes it; nothing for a folder that has none. */
        static Optional<Jdk> at(Path home) {
            try {
                String release = Files.readString(home.resolve("release"));
                Matcher version = JAVA_VERSION.matcher(release);
                if (!version.find()) {
                    return Optional.empty();
                }
                Matcher major = MAJOR.matcher(version.group(1));
                if (!major.find()) {
                    return Optional.empty();
                }
                Matcher build = RUNTIME_VERSION.matcher(release);
                String name = "JavaSE-" + (major.group(1) != null ? "1." + major.group(1) : major.group(2));

                return Optional.of(new Jdk(name, home, build.find() ? build.group(1) : version.group(1)));
            } catch (IOException | UncheckedIOException unreadable) {
                return Optional.empty();
            }
        }

        /** What {@link #line} wrote, or nothing for a line that is not one of its own. */
        static Optional<Jdk> parse(String line) {
            String[] parts = line.split("\t", -1);
            try {
                return parts.length == 3
                        ? Optional.of(new Jdk(parts[0], Path.of(parts[1]), parts[2]))
                        : Optional.empty();
            } catch (InvalidPathException unreadable) {
                return Optional.empty();
            }
        }

        /** The runtime on a line, to write down: the name, the folder and the version, separated by tabs. */
        String line() {
            return name + "\t" + home + "\t" + version;
        }

        /** True while the folder holds the JDK that was written down (and not another one that took its place). */
        boolean isStillThere() {
            return at(home).filter(this::equals).isPresent();
        }
    }

    /**
     * The settings that declare these JDKs to jdtls: {@code java.configuration.runtimes}, in the nested form of the JSON
     * settings, sent with {@code workspace/didChangeConfiguration}.
     */
    static Map<String, Object> settings(List<Jdk> jdks) {
        List<Object> declared = new ArrayList<>();
        for (Jdk jdk : jdks) {
            declared.add(Map.of("name", jdk.name(), "path", jdk.home().toString()));
        }

        return Map.of("java", Map.of("configuration", Map.of("runtimes", declared)));
    }

    /**
     * The JDKs the projects are bound to, from what {@code java.project.getSettings} answered for each: one runtime for
     * each execution environment (the first), and none for a project whose JDK has no {@code release} file.
     *
     * @param answers the answers, each a map from a setting to its value
     */
    static List<Jdk> boundIn(List<@Nullable Object> answers) {
        Map<String, Jdk> byName = new LinkedHashMap<>();
        for (Object answer : answers) {
            if (answer instanceof Map<?, ?> settings && settings.get(VM_LOCATION) instanceof String location) {
                try {
                    Jdk.at(Path.of(location)).ifPresent(runtime -> byName.putIfAbsent(runtime.name(), runtime));
                } catch (InvalidPathException notAPath) {
                    // A location that is no path names no JDK.
                }
            }
        }

        return List.copyOf(byName.values());
    }
}
