package eu.gabier.daemon;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ways the daemon knows to choose the JDK of an execution environment, in their order of precedence: the one place
 * that lists them. For an environment the first source that chooses a JDK wins, and a later one is not heard of for it;
 * what every source found wrong is told. Adding a way is a class that implements {@link RuntimeSource} and a line in
 * {@link #STANDARD}; removing one is deleting that line (and its class).
 *
 * @param ordered the sources, the first having the precedence
 */
record RuntimeSources(List<RuntimeSource> ordered) {

    /** The sources of the daemon, the first having the precedence. */
    static final RuntimeSources STANDARD = new RuntimeSources(List.of(new VsCodeRuntimes()));

    RuntimeSources {
        ordered = List.copyOf(ordered);
    }

    /** What the sources choose for the project at {@code root}, one JDK for each environment, and what they warn of. */
    Chosen read(Path root) {
        Map<String, Choice> byEnvironment = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (RuntimeSource source : ordered) {
            RuntimeSource.Found found = source.read(root);
            found.jdks().forEach(jdk -> byEnvironment.putIfAbsent(jdk.name(), new Choice(jdk, source.description())));
            warnings.addAll(found.warnings());
        }

        return new Chosen(List.copyOf(byEnvironment.values()), warnings);
    }

    /**
     * A JDK that a source chose.
     *
     * @param jdk the JDK
     * @param source how that source says it ({@link RuntimeSource#description})
     */
    record Choice(Jdk jdk, String source) {}

    /**
     * What the sources chose.
     *
     * @param choices the JDK of each environment that a source chose
     * @param warnings what the sources found wrong, in English, one line each
     */
    record Chosen(List<Choice> choices, List<String> warnings) {

        Chosen {
            choices = List.copyOf(choices);
            warnings = List.copyOf(warnings);
        }

        /** The JDKs chosen, one for each environment. */
        List<Jdk> jdks() {
            return choices.stream().map(Choice::jdk).toList();
        }
    }
}
