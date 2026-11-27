package eu.gabier.daemon;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import java.nio.file.Path;
import java.util.List;

/**
 * One way of choosing the JDK of an execution environment ({@code JavaSE-25}) for a project, which jdtls would
 * otherwise draw among the JDKs of that version it finds ({@link JdtlsRuntimes}). A source reads its own place (a file
 * of the project, a variable, a tool) and says what it chooses and what is wrong with what it read; it knows nothing of
 * the other sources, of jdtls or of the workspace. The sources the daemon knows, and their order of precedence, are
 * listed in one place, {@link RuntimeSources}: a new way is a class that implements this and a line in that list.
 */
interface RuntimeSource {

    /** How {@code status} says that the JDK is this source's choice, in words ({@code chosen in .vscode/settings.json}). */
    String description();

    /**
     * What the source chooses for the project at {@code root}: at most one JDK for each environment, each checked to be
     * a JDK of the version its environment names. What it cannot use it leaves out, and says why in the warnings.
     */
    Found read(Path root);

    /**
     * What a source found.
     *
     * @param jdks the JDKs chosen, one for each environment
     * @param warnings what is wrong with what the source read, in English, one line each
     */
    record Found(List<Jdk> jdks, List<String> warnings) {

        public Found {
            jdks = List.copyOf(jdks);
            warnings = List.copyOf(warnings);
        }
    }
}
