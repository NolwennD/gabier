package eu.gabier.daemon;

import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.ImportSettings.Setting;
import eu.gabier.protocol.ImportSettings.Source;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.MalformedJsonException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What the project says about its imports, read from its files: the preferences of Eclipse
 * ({@code .settings/org.eclipse.jdt.ui.prefs}) and the settings of VS Code Java ({@code .vscode/settings.json}), both
 * under the root of the project the daemon serves. jdtls reads neither for the imports; it takes the {@code java.*}
 * settings its client sends, so gabier reads the files and sends them (see {@link JdtlsSettings#imports}).
 *
 * <p>Each of the three settings comes from the file that sets it, the Eclipse preferences first. A setting that the two
 * files give with different values is followed as the Eclipse preferences say it, and a warning says so. A setting no
 * file sets is left to the default of jdtls ({@link ImportSettings#DEFAULTS}). A file that cannot be read or is not
 * what it should be is ignored, and a warning says so: an agent that runs {@code organize-imports} learns that the
 * project's files were not all followed. The files are read at each call.
 *
 * @param settings the three settings, each with its value and the file it comes from
 * @param warnings what is wrong with the files, in English, one line each
 */
record ImportPreferences(ImportSettings settings, List<String> warnings) {

    static final String ECLIPSE_FILE = ".settings/org.eclipse.jdt.ui.prefs";
    static final String VSCODE_FILE = ".vscode/settings.json";

    private static final String ORDER = "java.completion.importOrder";
    private static final String STAR = "java.sources.organizeImports.starThreshold";
    private static final String STATIC_STAR = "java.sources.organizeImports.staticStarThreshold";
    private static final String ECLIPSE_ORDER = "org.eclipse.jdt.ui.importorder";
    private static final String ECLIPSE_STAR = "org.eclipse.jdt.ui.ondemandthreshold";
    private static final String ECLIPSE_STATIC_STAR = "org.eclipse.jdt.ui.staticondemandthreshold";

    ImportPreferences {
        warnings = List.copyOf(warnings);
    }

    /** Reads the files of the project; a file that is not there says nothing. */
    static ImportPreferences read(Path root) {
        var warnings = new ArrayList<String>();
        var eclipse = eclipse(root.resolve(ECLIPSE_FILE), warnings);
        var vscode = vscode(root.resolve(VSCODE_FILE), warnings);
        var settings = new ImportSettings(
                merge(ImportSettings.DEFAULTS.importOrder(), ORDER, eclipse.order, vscode.order, warnings),
                merge(ImportSettings.DEFAULTS.starThreshold(), STAR, eclipse.star, vscode.star, warnings),
                merge(
                        ImportSettings.DEFAULTS.staticStarThreshold(),
                        STATIC_STAR,
                        eclipse.staticStar,
                        vscode.staticStar,
                        warnings));

        return new ImportPreferences(settings, warnings);
    }

    /** Whether jdtls is given the same thing by both: the sources of the values do not count. */
    static boolean sameValues(ImportSettings first, ImportSettings second) {
        return first.importOrder().value().equals(second.importOrder().value())
                && first.starThreshold().value().equals(second.starThreshold().value())
                && first.staticStarThreshold()
                        .value()
                        .equals(second.staticStarThreshold().value());
    }

    /** What one file says: each setting, when it sets it. */
    private record Found(Optional<List<String>> order, Optional<Integer> star, Optional<Integer> staticStar) {

        static final Found NOTHING = new Found(Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static <T> Setting<T> merge(
            Setting<T> byDefault, String name, Optional<T> eclipse, Optional<T> vscode, List<String> warnings) {
        if (eclipse.isPresent() && vscode.isPresent() && !eclipse.get().equals(vscode.get())) {
            warnings.add("%s is %s in %s and %s in %s: following %s"
                    .formatted(name, show(eclipse.get()), ECLIPSE_FILE, show(vscode.get()), VSCODE_FILE, ECLIPSE_FILE));
        }
        if (eclipse.isPresent()) {
            return new Setting<>(eclipse.get(), Source.SETTINGS);
        }

        return vscode.map(value -> new Setting<>(value, Source.VSCODE)).orElse(byDefault);
    }

    private static String show(Object value) {
        if (value instanceof List<?> groups) {
            return groups.stream()
                    .map(group -> Json.quote(String.valueOf(group)))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        return String.valueOf(value);
    }

    private static Found eclipse(Path file, List<String> warnings) {
        if (!Files.exists(file)) {
            return Found.NOTHING;
        }
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            warnings.add("ignored %s: %s".formatted(ECLIPSE_FILE, e));

            return Found.NOTHING;
        }

        return new Found(
                order(properties.getProperty(ECLIPSE_ORDER)),
                threshold(properties, ECLIPSE_STAR, warnings),
                threshold(properties, ECLIPSE_STATIC_STAR, warnings));
    }

    /**
     * The groups of the order, as Eclipse writes them: separated by {@code ;}, with a trailing one for the group of
     * what no other takes, as jdtls writes its own order ({@code String.join(";", groups)}).
     */
    private static Optional<List<String>> order(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Arrays.asList(value.split(";", -1)));
    }

    private static Optional<Integer> threshold(Properties properties, String name, List<String> warnings) {
        String value = properties.getProperty(name);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            warnings.add("ignored %s of %s: it is not an integer".formatted(name, ECLIPSE_FILE));

            return Optional.empty();
        }
    }

    private static Found vscode(Path file, List<String> warnings) {
        if (!Files.exists(file)) {
            return Found.NOTHING;
        }
        @Nullable Object parsed;
        try {
            parsed = Json.parse(JsonWithComments.clean(Files.readString(file)));
        } catch (IOException | MalformedJsonException e) {
            warnings.add("ignored %s: %s".formatted(VSCODE_FILE, e.getMessage()));

            return Found.NOTHING;
        }
        if (!(parsed instanceof Map<?, ?> settings)) {
            warnings.add("ignored %s: it is not a JSON object".formatted(VSCODE_FILE));

            return Found.NOTHING;
        }

        return new Found(
                groups(settings.get(ORDER), settings.containsKey(ORDER), warnings),
                integer(settings.get(STAR), settings.containsKey(STAR), STAR, warnings),
                integer(settings.get(STATIC_STAR), settings.containsKey(STATIC_STAR), STATIC_STAR, warnings));
    }

    private static Optional<List<String>> groups(@Nullable Object value, boolean set, List<String> warnings) {
        if (!set) {
            return Optional.empty();
        }
        if (value instanceof List<?> elements && elements.stream().allMatch(String.class::isInstance)) {
            return Optional.of(elements.stream().map(String.class::cast).toList());
        }
        warnings.add("ignored %s of %s: it is not a list of text".formatted(ORDER, VSCODE_FILE));

        return Optional.empty();
    }

    private static Optional<Integer> integer(@Nullable Object value, boolean set, String name, List<String> warnings) {
        if (!set) {
            return Optional.empty();
        }
        try {
            if (value instanceof BigDecimal number) {
                return Optional.of(number.intValueExact());
            }
        } catch (ArithmeticException e) {
            // Not a whole number, or one that an int cannot hold: said below.
        }
        warnings.add("ignored %s of %s: it is not an integer".formatted(name, VSCODE_FILE));

        return Optional.empty();
    }
}
