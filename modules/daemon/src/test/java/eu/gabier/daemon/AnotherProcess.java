package eu.gabier.daemon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a lock is worth is what another process sees of it: the lock of a file is a lock of the process, and closing any
 * channel of the file in the process that holds it gives the lock up. A test of a lock asks a process of its own.
 */
final class AnotherProcess {

    private static final String PROBE = """
        import java.nio.channels.FileChannel;
        import java.nio.file.Path;
        import java.nio.file.StandardOpenOption;

        public class Probe {
            public static void main(String[] arguments) throws Exception {
                try (FileChannel channel = FileChannel.open(Path.of(arguments[0]), StandardOpenOption.WRITE)) {
                    System.out.println(channel.tryLock() == null ? "held" : "free");
                }
            }
        }
        """;

    private AnotherProcess() {}

    /**
     * Whether a process other than this one finds the file locked: a {@code java} of the same JDK runs a probe that
     * tries the lock and tells.
     *
     * @param lockFile the file the lock is on
     * @param scratch a folder to write the probe in
     */
    static boolean findsLocked(Path lockFile, Path scratch) throws IOException, InterruptedException {
        Path source =
                Files.writeString(Files.createTempDirectory(scratch, "probe").resolve("Probe.java"), PROBE);
        Process probe = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        source.toString(),
                        lockFile.toString())
                .redirectErrorStream(true)
                .start();
        String answer = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        probe.waitFor();

        return switch (answer) {
            case "held" -> true;
            case "free" -> false;
            default -> throw new IllegalStateException("the probe answered: " + answer);
        };
    }
}
