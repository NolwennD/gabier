package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The JDKs jdtls binds the projects to, as {@code status} says them: for each execution environment the projects ask
 * for ({@code JavaSE-25}), the folder of the JDK, and who chose it. A machine may hold several JDKs of one version, so
 * the choice is made by one of the ways the daemon knows when one does, and jdtls draws one otherwise.
 *
 * @param bindings the JDK of each environment, as jdtls reported it when it was ready
 * @param warnings what is wrong with the choice of the project (an entry that names no JDK, or a JDK of another
 *     version; a JDK that jdtls did not bind as chosen), in English, one line each
 */
public record ProjectJdks(List<Binding> bindings, List<String> warnings) {

    /** Nothing to say: jdtls is not ready, or no project is bound to a JDK. */
    public static final ProjectJdks NONE = new ProjectJdks(List.of(), List.of());

    private static final String BINDINGS = "bindings";
    private static final String WARNINGS = "warnings";

    public ProjectJdks {
        Assert.notNull(BINDINGS, bindings);
        Assert.notNull(WARNINGS, warnings);

        bindings = List.copyOf(bindings);
        warnings = List.copyOf(warnings);
    }

    /**
     * One execution environment and the JDK it is bound to.
     *
     * @param environment the environment, as jdtls names it ({@code JavaSE-25})
     * @param home the folder of the JDK
     * @param source who chose that JDK, in words ({@code chosen in .vscode/settings.json}, {@code bound by jdtls}): each
     *     way of choosing a JDK says its own, and the protocol knows none of them
     */
    public record Binding(String environment, Path home, String source) {

        public Binding {
            Assert.notBlank("environment", environment);
            Assert.notNull("home", home);
            Assert.notBlank("source", source);
        }
    }

    /** True when there is nothing to say: no JDK and no warning. */
    public boolean isEmpty() {
        return bindings.isEmpty() && warnings.isEmpty();
    }

    /** The JDKs as JSON, the members in a stable order. */
    public String toJson() {
        var jdks = bindings.stream()
                .map(binding -> "{\"environment\":%s,\"home\":%s,\"source\":%s}"
                        .formatted(
                                Json.quote(binding.environment()),
                                Json.quote(binding.home().toString()),
                                Json.quote(binding.source())))
                .collect(Collectors.joining(",", "[", "]"));
        var problems = warnings.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));

        return "{\"bindings\":%s,\"warnings\":%s}".formatted(jdks, problems);
    }

    /** One line for each JDK, with who chose it, then one for each warning, for a person. */
    public List<String> lines() {
        var lines = new ArrayList<String>();
        for (var binding : bindings) {
            lines.add("%s %s (%s)".formatted(binding.environment(), binding.home(), binding.source()));
        }
        warnings.forEach(warning -> lines.add("warning: " + warning));

        return lines;
    }

    /**
     * Reads the JDKs of a result.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static ProjectJdks of(Map<String, @Nullable Object> result) {
        return new ProjectJdks(bindings(result.get(BINDINGS)), warnings(result.get(WARNINGS)));
    }

    private static List<Binding> bindings(@Nullable Object bindings) {
        if (!(bindings instanceof List<?> elements)) {
            throw invalid(BINDINGS);
        }

        return elements.stream().map(ProjectJdks::binding).toList();
    }

    private static Binding binding(Object element) {
        if (element instanceof Map<?, ?> members
                && members.get("environment") instanceof String environment
                && members.get("home") instanceof String home
                && members.get("source") instanceof String source) {
            return new Binding(environment, path(home), source);
        }

        throw invalid(BINDINGS);
    }

    private static Path path(String home) {
        try {
            return Path.of(home);
        } catch (InvalidPathException e) {
            throw InvalidResponseException.of("The home of a JDK of the status is not a path", e);
        }
    }

    private static List<String> warnings(@Nullable Object warnings) {
        if (warnings instanceof List<?> elements && elements.stream().allMatch(String.class::isInstance)) {
            return elements.stream().map(String.class::cast).toList();
        }

        throw invalid(WARNINGS);
    }

    private static InvalidResponseException invalid(String name) {
        return InvalidResponseException.of("The JDKs of the status have no " + name + " of the right type", null);
    }
}
