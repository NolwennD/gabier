package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonPrimitive;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.junit.jupiter.api.Test;

class FailureLogTest {

    private static ResponseErrorException answered(Object data) {
        return new ResponseErrorException(new ResponseError(-32603, "Internal error.", data));
    }

    @Test
    void saysTheMethodTheTypeAndTheMessage() {
        assertThat(FailureLog.lines("rename", new IllegalStateException("broke")))
                .containsExactly("request rename failed: java.lang.IllegalStateException: broke");
    }

    @Test
    void saysTheTypeAloneOfAFailureWithNoMessage() {
        assertThat(FailureLog.lines("rename", new IllegalStateException()))
                .containsExactly("request rename failed: java.lang.IllegalStateException");
    }

    @Test
    void saysEachCauseOnALineOfItsOwnFromTheNearestToTheFarthest() {
        var failure = new IllegalStateException("outer", new IllegalArgumentException("middle", new Error("inner")));

        assertThat(FailureLog.lines("move", failure))
                .containsExactly(
                        "request move failed: java.lang.IllegalStateException: outer",
                        "  caused by java.lang.IllegalArgumentException: middle",
                        "  caused by java.lang.Error: inner");
    }

    @Test
    void saysTheCodeAndTheMessageOfAnErrorOfJdtlsAndEachLineOfItsData() {
        var failure = new JdtlsException("Failed", answered(new JsonPrimitive("first\n\tat second\n\n  third  ")));

        assertThat(FailureLog.lines("rename", failure))
                .containsExactly(
                        "request rename failed: eu.gabier.daemon.JdtlsException: Failed",
                        "  caused by org.eclipse.lsp4j.jsonrpc.ResponseErrorException: Internal error. (response error"
                                + " code -32603)",
                        "    first",
                        "    at second",
                        "    third");
    }

    @Test
    void saysTheDataOfAnErrorThatIsTheFailureItself() {
        assertThat(FailureLog.lines("rename", answered("plain data")))
                .containsExactly(
                        "request rename failed: org.eclipse.lsp4j.jsonrpc.ResponseErrorException: Internal error."
                                + " (response error code -32603)",
                        "    plain data");
    }

    @Test
    void writesTheDataThatIsNotATextAsItIs() {
        assertThat(FailureLog.lines("rename", answered(new JsonPrimitive(42)))).endsWith("    42");
    }

    @Test
    void saysNothingMoreOfAnErrorThatHasNoData() {
        assertThat(FailureLog.lines("rename", answered(null))).hasSize(1);
    }

    @Test
    void writesOutWhatATerminalWouldObeyAndKeepsEveryLineOnOneLine() {
        var lines = FailureLog.lines("rename", new IllegalStateException("clear\u001b[2J\nnext"));

        assertThat(lines)
                .containsExactly("request rename failed: java.lang.IllegalStateException: clear\\u001b[2J\\u000anext");
    }
}
