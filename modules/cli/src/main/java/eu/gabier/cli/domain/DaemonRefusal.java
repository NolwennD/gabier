package eu.gabier.cli.domain;

import eu.gabier.shared.error.domain.ErrorStatus;
import java.util.Map;

/**
 * The daemon answered with an error: its problem, as it gave it, with the category the exit code is derived from. The
 * parameters are the values the explanation is made of.
 */
public final class DaemonRefusal extends RuntimeException {

    private final String type;
    private final String title;
    private final String detail;
    private final String key;
    private final transient Map<String, String> parameters;
    private final ErrorStatus category;

    /**
     * The refusal of the daemon.
     *
     * @param type the URI that identifies the kind of problem
     * @param title the short summary of the kind of problem
     * @param detail the explanation of this occurrence
     * @param key the key of the problem, such as {@code editing-invalid-range}
     * @param parameters the values the explanation is made of
     * @param category whether the request was refused or the daemon failed
     */
    public DaemonRefusal(
            String type,
            String title,
            String detail,
            String key,
            Map<String, String> parameters,
            ErrorStatus category) {
        super(detail);
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.key = key;
        this.parameters = parameters;
        this.category = category;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public String key() {
        return key;
    }

    public Map<String, String> parameters() {
        return parameters;
    }

    public ErrorStatus category() {
        return category;
    }
}
