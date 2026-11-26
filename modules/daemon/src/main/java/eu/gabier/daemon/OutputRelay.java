package eu.gabier.daemon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Gives the lines that a process writes on its output to the daemon, which writes them to its log. */
final class OutputRelay {

    private OutputRelay() {}

    /** Reads the stream line by line until it ends, or is closed when the process is, and gives each line. */
    static void relay(InputStream stream, Consumer<String> lines) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                lines.accept(line);
            }
        } catch (IOException closed) {
            // The process ended and its stream with it: there is nothing more to relay.
        }
    }

    /** Relays the stream in a thread of its own, which does not keep the daemon alive. */
    static void relayInBackground(String name, InputStream stream, Consumer<String> lines) {
        Thread.ofPlatform().daemon().name(name).start(() -> relay(stream, lines));
    }
}
