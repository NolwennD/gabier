package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JulLogTest {

    private final Logger root = Logger.getLogger("");
    private final List<String> lines = new ArrayList<>();
    private Handler[] before;

    @BeforeEach
    void keepTheHandlersOfTheRuntime() {
        before = root.getHandlers();
    }

    @AfterEach
    void restoreTheHandlersOfTheRuntime() {
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        for (var handler : before) {
            root.addHandler(handler);
        }
    }

    @Test
    void givesWhatALibraryLogsToTheConsumerAsALineInsteadOfTheTwoLinesOfTheConsoleOnTheStandardError() {
        JulLog.install(lines::add);

        Logger.getLogger("org.eclipse.lsp4j").warning("Unsupported notification method: language/other");

        assertThat(lines).containsExactly("WARNING: Unsupported notification method: language/other");
        assertThat(root.getHandlers()).hasSize(1);
        root.getHandlers()[0].flush();
        root.getHandlers()[0].close();
    }

    @Test
    void addsTheTypeAndTheMessageOfTheFailureAndWritesItAsText() {
        JulLog.install(lines::add);

        Logger.getLogger("org.eclipse.lsp4j").log(Level.SEVERE, "broke", new IllegalStateException("red\u001b[2J"));

        assertThat(lines).containsExactly("SEVERE: broke: java.lang.IllegalStateException: red\\u001b[2J");
    }
}
