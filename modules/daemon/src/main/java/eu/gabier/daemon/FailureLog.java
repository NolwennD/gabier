package eu.gabier.daemon;

import com.google.gson.JsonPrimitive;
import eu.gabier.protocol.TerminalText;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;

/**
 * What the log of the daemon says of a request that failed: the method of the request, then the type and the message
 * of the failure and of each of its causes, and for an error that jdtls answered its code, its message and the data of
 * its answer, which is where jdtls puts the cause (a stack trace). The parameters of the request (a file, a name) are
 * not in it, and neither is any text of the project: only the words of the exceptions. What jdtls says is written out
 * as text, one line of the log per line of it, as the other lines of jdtls are ({@link TerminalText#neutralise}).
 */
final class FailureLog {

    private static final String CAUSE_INDENT = "  caused by ";
    private static final String DATA_INDENT = "    ";

    private FailureLog() {}

    /** The lines to write for the request of this method that failed. */
    static List<String> lines(String method, Throwable failure) {
        List<String> lines = new ArrayList<>();
        lines.add("request " + method + " failed: " + describe(failure));
        dataOf(failure, lines);
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            lines.add(CAUSE_INDENT + describe(cause));
            dataOf(cause, lines);
        }

        return lines.stream().map(TerminalText::neutralise).toList();
    }

    private static String describe(Throwable failure) {
        var type = failure.getClass().getName();
        var message = failure.getMessage();
        var described = message == null ? type : type + ": " + message;
        if (failure instanceof ResponseErrorException error) {
            return described + " (response error code "
                    + error.getResponseError().getCode() + ")";
        }

        return described;
    }

    private static void dataOf(Throwable failure, List<String> lines) {
        if (failure instanceof ResponseErrorException error
                && error.getResponseError().getData() != null) {
            var data = error.getResponseError().getData();
            var text = data instanceof JsonPrimitive primitive && primitive.isString()
                    ? primitive.getAsString()
                    : data.toString();
            text.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> lines.add(DATA_INDENT + line));
        }
    }
}
