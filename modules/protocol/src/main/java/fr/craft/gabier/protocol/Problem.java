package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.Assert;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What gabier says when it refuses a request or fails: a problem object, whose data model follows RFC 9457 (problem
 * details), without its HTTP side. It is written as JSON ({@link #toJson()}) for a program, and as one line
 * ({@link #toText()}) for a human; the same object travels from the daemon to the cli and to any other client.
 *
 * <p>The members are those of the RFC and two extensions: {@code type} (a non-resolvable URI built from the key, {@code
 * urn:gabier:error:<key>}), {@code title} (the same for every problem of the key), {@code detail} (this occurrence),
 * then {@code key} and {@code parameters}, the machine-readable cause. There is no {@code status} member and no {@code
 * application/problem+json} media type: they belong to HTTP.
 *
 * @param type the URI that identifies the kind of problem
 * @param title the short, English summary of the kind of problem
 * @param detail the English explanation of this occurrence
 * @param key the key of the error, such as {@code editing-invalid-range}
 * @param parameters the values the explanation is made of, sorted by name
 * @param category whether the request was refused or gabier failed: not written in the JSON, it is what the exit code
 *     is derived from
 */
public record Problem(
        String type, String title, String detail, String key, Map<String, String> parameters, ErrorStatus category) {

    private static final Pattern LINE_BREAKS = Pattern.compile("\\R+");

    public Problem {
        Assert.field("type", type).notBlank();
        Assert.field("title", title).notBlank();
        Assert.field("detail", detail).notBlank();
        Assert.field("key", key).notBlank();
        Assert.notNull("parameters", parameters);
        Assert.notNull("category", category);

        parameters = Collections.unmodifiableMap(new TreeMap<>(parameters));
    }

    /** The problem as one line of JSON, the members in a stable order. */
    public String toJson() {
        var json = new StringBuilder("{");
        member(json, "type", type).append(',');
        member(json, "title", title).append(',');
        member(json, "detail", detail).append(',');
        member(json, "key", key).append(',');
        json.append("\"parameters\":{");
        json.append(parameters.entrySet().stream()
                .map(parameter -> Json.quote(parameter.getKey()) + ":" + Json.quote(parameter.getValue()))
                .collect(Collectors.joining(",")));

        return json.append("}}").toString();
    }

    /** The problem as one line for a human: {@code title: detail (key; name=value, ...)}. */
    public String toText() {
        var text = new StringBuilder(title)
                .append(": ")
                .append(detail)
                .append(" (")
                .append(key);
        if (!parameters.isEmpty()) {
            text.append("; ")
                    .append(parameters.entrySet().stream()
                            .map(parameter -> parameter.getKey() + "=" + parameter.getValue())
                            .collect(Collectors.joining(", ")));
        }

        return LINE_BREAKS.matcher(text.append(')')).replaceAll(" ");
    }

    private static StringBuilder member(StringBuilder json, String name, String value) {
        return json.append(Json.quote(name)).append(':').append(Json.quote(value));
    }
}
