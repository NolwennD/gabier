package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.time.Duration;

/**
 * What jdtls is doing while it starts, as {@code status} says it: the stage it is in, the last thing it reported, and
 * how long it has been in that stage. The message is jdtls's own text (the task of the import it is running, for
 * instance), relayed as it came: it is hostile input, so whoever prints it goes through {@link TerminalText}.
 *
 * @param phase the stage jdtls is in
 * @param message the last message jdtls sent in this stage, empty when it sent none
 * @param spent how long jdtls has been in this stage
 */
public record JdtlsProgress(Phase phase, String message, Duration spent) {

    /** The stages of a start, in order. */
    public enum Phase {
        /** The process is started and has not begun to import the project: its JVM and its runtime come up. */
        LAUNCHING("launching", "launching jdtls"),
        /** jdtls imports the project: for a Gradle project, it runs the build. */
        IMPORTING("importing", "importing the project");

        private final String text;
        private final String words;

        Phase(String text, String words) {
            this.text = text;
            this.words = words;
        }

        /** The name written in the result. */
        public String text() {
            return text;
        }
    }

    public JdtlsProgress {
        Assert.notNull("phase", phase);
        Assert.notNull("message", message);
        Assert.notNull("spent", spent);
        Assert.field("spent", spent.toMillis()).min(0);
    }

    /** The progress in words for a person, such as {@code importing the project for 47s: Configure project :core}. */
    public String text() {
        var time = phase.words + " for " + spent.toSeconds() + "s";

        return message.isEmpty() ? time : time + ": " + message;
    }

    /** The progress as JSON, the members in a stable order. */
    String toJson() {
        return """
            {"phase":%s,"message":%s,"spentMillis":%d}\
            """.formatted(Json.quote(phase.text), Json.quote(message), spent.toMillis());
    }
}
