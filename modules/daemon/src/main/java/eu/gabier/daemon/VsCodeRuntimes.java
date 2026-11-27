package eu.gabier.daemon;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.MalformedJsonException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The JDKs the project chooses in the setting {@code java.configuration.runtimes} of {@code .vscode/settings.json},
 * the one VS Code Java uses for it (a list of {@code {"name": "JavaSE-25", "path": "<folder of a JDK>"}}): a way of
 * choosing the JDK of an execution environment ({@link RuntimeSource}).
 *
 * <p>jdtls takes whatever it is given for an environment: checked on the real jdtls, a JDK 21 declared as {@code
 * JavaSE-25} is bound to the projects of that environment, and a folder that does not exist is ignored without a word.
 * So each entry is checked here, with the file {@code release} of the JDK ({@link Jdk#at}), and an entry that names a
 * folder that is no JDK, or a JDK of another version than its environment, is left out with a warning that says why.
 */
final class VsCodeRuntimes implements RuntimeSource {

    static final String FILE = ImportPreferences.VSCODE_FILE;
    static final String SETTING = "java.configuration.runtimes";

    @Override
    public String description() {
        return "chosen in " + FILE;
    }

    /** Reads the file of the project; a file that is not there, or that does not set the JDKs, chooses none. */
    @Override
    public Found read(Path root) {
        var warnings = new ArrayList<String>();
        var chosen = new LinkedHashMap<String, Jdk>();
        for (Object entry : entries(root.resolve(FILE), warnings)) {
            entry(entry, warnings).ifPresent(jdk -> {
                if (chosen.putIfAbsent(jdk.name(), jdk) != null) {
                    warnings.add("ignored a second entry for %s of %s in %s: %s"
                            .formatted(jdk.name(), SETTING, FILE, jdk.home()));
                }
            });
        }

        return new Found(List.copyOf(chosen.values()), warnings);
    }

    private static List<?> entries(Path file, List<String> warnings) {
        if (!Files.exists(file)) {
            return List.of();
        }
        @Nullable Object parsed;
        try {
            parsed = Json.parse(JsonWithComments.clean(Files.readString(file)));
        } catch (IOException | MalformedJsonException e) {
            warnings.add("ignored %s: %s".formatted(FILE, e.getMessage()));

            return List.of();
        }
        if (!(parsed instanceof Map<?, ?> settings)) {
            warnings.add("ignored %s: it is not a JSON object".formatted(FILE));

            return List.of();
        }
        if (!settings.containsKey(SETTING)) {
            return List.of();
        }
        if (settings.get(SETTING) instanceof List<?> entries) {
            return entries;
        }
        warnings.add("ignored %s of %s: it is not a list".formatted(SETTING, FILE));

        return List.of();
    }

    private static Optional<Jdk> entry(Object entry, List<String> warnings) {
        if (!(entry instanceof Map<?, ?> members)
                || !(members.get("name") instanceof String name)
                || !(members.get("path") instanceof String location)) {
            warnings.add("ignored an entry of %s in %s: it has no name and path, both text".formatted(SETTING, FILE));

            return Optional.empty();
        }
        Path home;
        try {
            home = Path.of(location);
        } catch (InvalidPathException notAPath) {
            warnings.add("ignored %s of %s in %s: %s is not a path".formatted(name, SETTING, FILE, location));

            return Optional.empty();
        }
        if (!home.isAbsolute()) {
            warnings.add("ignored %s of %s in %s: the path %s is not absolute".formatted(name, SETTING, FILE, home));

            return Optional.empty();
        }
        var jdk = Jdk.at(home);
        if (jdk.isEmpty()) {
            warnings.add("ignored %s of %s in %s: there is no JDK at %s (no readable file release naming its version)"
                    .formatted(name, SETTING, FILE, home));

            return Optional.empty();
        }
        if (!jdk.get().name().equals(name)) {
            warnings.add("ignored %s of %s in %s: the JDK at %s is %s, not %s"
                    .formatted(name, SETTING, FILE, home, jdk.get().name(), name));

            return Optional.empty();
        }

        return jdk;
    }
}
