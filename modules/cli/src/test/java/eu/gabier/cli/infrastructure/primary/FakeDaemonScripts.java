package eu.gabier.cli.infrastructure.primary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/** The executables of the tests that stand for {@code gabier-daemon}: shell scripts, so that the cli starts a process. */
public final class FakeDaemonScripts {

    private FakeDaemonScripts() {}

    /** An executable file with this body, written as {@code gabier-daemon} in the folder. */
    public static Path install(Path folder, String body) throws IOException {
        Files.createDirectories(folder);
        var script = Files.writeString(folder.resolve("gabier-daemon"), "#!/bin/sh\n" + body + "\n");
        script.toFile().setExecutable(true);

        return script;
    }

    /** A daemon that runs {@link FakeDaemonMain}, in a process of its own, on the class path of the tests. */
    public static Path installListening(Path folder) throws IOException {
        var java = ProcessHandle.current().info().command().orElseThrow();

        return install(
                folder,
                "exec '%s' -cp '%s' %s"
                        .formatted(java, System.getProperty("java.class.path"), FakeDaemonMain.class.getName()));
    }

    /** The pid the daemon of {@link FakeDaemonMain} wrote in its log. */
    static Optional<Long> pidIn(Path log) throws IOException {
        return Files.readAllLines(log).stream()
                .flatMap(line -> Arrays.stream(line.split(" ")))
                .filter(word -> word.startsWith("pid="))
                .map(word -> Long.parseLong(word.substring("pid=".length())))
                .findFirst();
    }

    /** Ends the daemon whose log this is, which a test that started one must do. */
    public static void end(Path log) throws IOException {
        if (Files.exists(log)) {
            pidIn(log).flatMap(ProcessHandle::of).ifPresent(ProcessHandle::destroyForcibly);
        }
    }
}
