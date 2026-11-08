package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code status} method: what a running daemon says about itself.
 *
 * @param version the protocol version the daemon speaks
 * @param pid the process id of the daemon
 * @param uptime the time since the daemon started listening
 * @param project the root of the project the daemon serves
 * @param jdtls where the jdtls of the daemon is: not started, starting, ready or failed
 * @param jdtlsFailure why jdtls is failed, as jdtls or the daemon said it (text from jdtls is hostile input: whoever
 *     prints it goes through {@link TerminalText}); empty when jdtls is not failed or the reason is not known
 * @param imports the settings of the imports that gabier follows for the project, read from its files when the status
 *     is asked for
 * @param jdtlsProgress what jdtls is doing, while it is starting; empty in any other state
 */
public record DaemonStatus(
        String version,
        long pid,
        Duration uptime,
        Path project,
        JdtlsState jdtls,
        Optional<String> jdtlsFailure,
        ImportSettings imports,
        Optional<JdtlsProgress> jdtlsProgress) {

    public DaemonStatus {
        Assert.notBlank("version", version);
        Assert.field("pid", pid).min(1);
        Assert.notNull("uptime", uptime);
        Assert.field("uptime", uptime.toMillis()).min(0);
        Assert.notNull("project", project);
        Assert.notNull("jdtls", jdtls);
        Assert.notNull("jdtlsFailure", jdtlsFailure);
        Assert.notNull("imports", imports);
        Assert.notNull("jdtlsProgress", jdtlsProgress);
    }

    /** A status that says nothing of what jdtls is doing. */
    public DaemonStatus(
            String version,
            long pid,
            Duration uptime,
            Path project,
            JdtlsState jdtls,
            Optional<String> jdtlsFailure,
            ImportSettings imports) {
        this(version, pid, uptime, project, jdtls, jdtlsFailure, imports, Optional.empty());
    }

    /** A status that says no reason for the state of jdtls, and whose project follows the defaults of jdtls. */
    public DaemonStatus(String version, long pid, Duration uptime, Path project, JdtlsState jdtls) {
        this(version, pid, uptime, project, jdtls, Optional.empty(), ImportSettings.DEFAULTS);
    }

    /** A status of a project that follows the defaults of jdtls. */
    public DaemonStatus(
            String version, long pid, Duration uptime, Path project, JdtlsState jdtls, Optional<String> jdtlsFailure) {
        this(version, pid, uptime, project, jdtls, jdtlsFailure, ImportSettings.DEFAULTS);
    }

    /** The result as JSON, the members in a stable order; the reason is a member only when there is one. */
    public String toJson() {
        var failure = jdtlsFailure
                .map(reason -> ",\"jdtlsFailure\":" + Json.quote(reason))
                .orElse("");
        var progress = jdtlsProgress
                .map(doing -> ",\"jdtlsProgress\":" + doing.toJson())
                .orElse("");
        return """
            {"version":%s,"pid":%d,"uptimeMillis":%d,"project":%s,"jdtls":%s,"imports":%s%s%s}\
            """.formatted(
                        Json.quote(version),
                        pid,
                        uptime.toMillis(),
                        Json.quote(project.toString()),
                        Json.quote(jdtls.text()),
                        imports.toJson(),
                        failure,
                        progress);
    }

    /**
     * Reads the result of a {@code status} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static DaemonStatus of(Map<String, @Nullable Object> result) {
        if (!(result.get("version") instanceof String version)) {
            throw member("version");
        }

        return new DaemonStatus(
                version,
                integer(result, "pid"),
                Duration.ofMillis(integer(result, "uptimeMillis")),
                project(result.get("project")),
                state(result.get("jdtls")),
                failure(result.get("jdtlsFailure")),
                imports(result.get("imports")),
                progress(result.get("jdtlsProgress")));
    }

    private static Optional<JdtlsProgress> progress(@Nullable Object progress) {
        if (progress == null) {
            return Optional.empty();
        }
        if (!(progress instanceof Map<?, ?> members)) {
            throw member("jdtlsProgress");
        }
        var phase = Stream.of(JdtlsProgress.Phase.values())
                .filter(candidate -> candidate.text().equals(members.get("phase")))
                .findFirst()
                .orElseThrow(() -> member("jdtlsProgress"));
        if (!(members.get("message") instanceof String message)) {
            throw member("jdtlsProgress");
        }
        var spent = new LinkedHashMap<String, @Nullable Object>();
        spent.put("spentMillis", members.get("spentMillis"));

        return Optional.of(new JdtlsProgress(phase, message, Duration.ofMillis(integer(spent, "spentMillis"))));
    }

    private static ImportSettings imports(@Nullable Object imports) {
        if (!(imports instanceof Map<?, ?> members)) {
            throw member("imports");
        }
        var settings = new LinkedHashMap<String, @Nullable Object>();
        members.forEach((name, setting) -> settings.put(String.valueOf(name), setting));

        return ImportSettings.of(settings);
    }

    private static Optional<String> failure(@Nullable Object failure) {
        if (failure == null) {
            return Optional.empty();
        }
        if (failure instanceof String reason) {
            return Optional.of(reason);
        }

        throw member("jdtlsFailure");
    }

    private static Path project(@Nullable Object project) {
        if (!(project instanceof String text)) {
            throw member("project");
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw InvalidResponseException.of("The member project of the status is not a path", e);
        }
    }

    private static JdtlsState state(@Nullable Object name) {
        for (var state : JdtlsState.values()) {
            if (state.text().equals(name)) {
                return state;
            }
        }

        throw member("jdtls");
    }

    private static long integer(Map<String, @Nullable Object> result, String name) {
        try {
            if (result.get(name) instanceof BigDecimal number) {
                return number.longValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The member " + name + " of the status is not an integer", e);
        }

        throw member(name);
    }

    private static InvalidResponseException member(String name) {
        return InvalidResponseException.of("The status has no member " + name + " of the right type", null);
    }
}
