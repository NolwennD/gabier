package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The three settings of jdtls that decide what the organization of the imports writes, as gabier follows them for the
 * project: each with its value and the place it comes from. They are the only configuration of the imports gabier
 * has: the project's own files say them, and jdtls's defaults say what the files do not.
 *
 * @param importOrder {@code java.completion.importOrder}: the groups of imports in order, a group being a package
 *     prefix, {@code "#"} the static imports and {@code ""} every import no other group takes
 * @param starThreshold {@code java.sources.organizeImports.starThreshold}: the number of imports of one package from
 *     which jdtls writes {@code import p.*;}
 * @param staticStarThreshold {@code java.sources.organizeImports.staticStarThreshold}: the same for the static imports
 *     of one type
 */
public record ImportSettings(
        Setting<List<String>> importOrder, Setting<Integer> starThreshold, Setting<Integer> staticStarThreshold) {

    /**
     * What jdtls does with no setting from its client (Eclipse JDT LS, {@code Preferences}: the groups {@code java},
     * {@code javax}, {@code org} and {@code com}, a star import from 99 imports): what a project that declares nothing
     * gets.
     */
    public static final ImportSettings DEFAULTS = new ImportSettings(
            new Setting<>(List.of("java", "javax", "org", "com"), Source.DEFAULT),
            new Setting<>(99, Source.DEFAULT),
            new Setting<>(99, Source.DEFAULT));

    private static final String IMPORT_ORDER = "importOrder";
    private static final String STAR_THRESHOLD = "starThreshold";
    private static final String STATIC_STAR_THRESHOLD = "staticStarThreshold";

    public ImportSettings {
        Assert.notNull(IMPORT_ORDER, importOrder);
        Assert.notNull(STAR_THRESHOLD, starThreshold);
        Assert.notNull(STATIC_STAR_THRESHOLD, staticStarThreshold);

        importOrder = new Setting<>(List.copyOf(importOrder.value()), importOrder.source());
    }

    /**
     * One setting.
     *
     * @param value what jdtls is given
     * @param source where it comes from
     * @param <T> the type of the value
     */
    public record Setting<T>(T value, Source source) {

        public Setting {
            Assert.notNull("value", value);
            Assert.notNull("source", source);
        }
    }

    /** Where the value of a setting comes from. */
    public enum Source {
        /** {@code .settings/org.eclipse.jdt.ui.prefs}, the preferences of Eclipse. */
        SETTINGS(".settings"),
        /** {@code .vscode/settings.json}, the settings of VS Code Java. */
        VSCODE(".vscode"),
        /** Neither file says it: what jdtls does without a setting. */
        DEFAULT("default");

        private final String text;

        Source(String text) {
            this.text = text;
        }

        /** The word the protocol uses for it. */
        public String text() {
            return text;
        }
    }

    /** The settings as JSON, the members in a stable order. */
    public String toJson() {
        return """
            {"importOrder":%s,"starThreshold":%s,"staticStarThreshold":%s}\
            """.formatted(
                        json(importOrder.source(), array(importOrder.value())),
                        json(starThreshold.source(), String.valueOf(starThreshold.value())),
                        json(staticStarThreshold.source(), String.valueOf(staticStarThreshold.value())));
    }

    /** One line per setting for a person: the setting of jdtls, its value, and where it comes from. */
    public List<String> lines() {
        return List.of(
                "java.completion.importOrder = %s (%s)".formatted(order(importOrder.value()), name(importOrder)),
                "java.sources.organizeImports.starThreshold = %d (%s)"
                        .formatted(starThreshold.value(), name(starThreshold)),
                "java.sources.organizeImports.staticStarThreshold = %d (%s)"
                        .formatted(staticStarThreshold.value(), name(staticStarThreshold)));
    }

    /**
     * Reads the settings of a result.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static ImportSettings of(Map<String, @Nullable Object> result) {
        Map<?, ?> order = member(result, IMPORT_ORDER);
        Map<?, ?> star = member(result, STAR_THRESHOLD);
        Map<?, ?> staticStar = member(result, STATIC_STAR_THRESHOLD);

        return new ImportSettings(
                new Setting<>(strings(order.get("value"), IMPORT_ORDER), source(order, IMPORT_ORDER)),
                new Setting<>(integer(star.get("value"), STAR_THRESHOLD), source(star, STAR_THRESHOLD)),
                new Setting<>(
                        integer(staticStar.get("value"), STATIC_STAR_THRESHOLD),
                        source(staticStar, STATIC_STAR_THRESHOLD)));
    }

    private static String name(Setting<?> setting) {
        return setting.source() == Source.DEFAULT
                ? "default of jdtls"
                : setting.source().text();
    }

    private static String json(Source source, String value) {
        return "{\"value\":%s,\"source\":%s}".formatted(value, Json.quote(source.text()));
    }

    private static String array(List<String> groups) {
        return groups.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));
    }

    private static String order(List<String> groups) {
        return groups.stream().map(Json::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    private static Map<?, ?> member(Map<String, @Nullable Object> result, String name) {
        if (result.get(name) instanceof Map<?, ?> members) {
            return members;
        }

        throw invalid(name);
    }

    private static Source source(Map<?, ?> setting, String name) {
        Object text = setting.get("source");
        for (var source : Source.values()) {
            if (source.text().equals(text)) {
                return source;
            }
        }

        throw invalid(name);
    }

    private static List<String> strings(@Nullable Object value, String name) {
        if (value instanceof List<?> elements && elements.stream().allMatch(String.class::isInstance)) {
            return elements.stream().map(String.class::cast).toList();
        }

        throw invalid(name);
    }

    private static int integer(@Nullable Object value, String name) {
        try {
            if (value instanceof BigDecimal number) {
                return number.intValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The value of " + name + " is not an integer", e);
        }

        throw invalid(name);
    }

    private static InvalidResponseException invalid(String name) {
        return InvalidResponseException.of("The settings of the imports have no " + name + " of the right type", null);
    }
}
