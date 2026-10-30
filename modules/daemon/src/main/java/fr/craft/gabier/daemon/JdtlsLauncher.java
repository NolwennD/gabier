package fr.craft.gabier.daemon;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts the jdtls launcher of the distribution, which talks the protocol on its standard input and output.
 *
 * @param launcher the {@code jdtls} script of the distribution
 * @param javaHome the JDK the launcher must run jdtls with, so that it does not depend on the shell's {@code java}
 */
record JdtlsLauncher(Path launcher, Path javaHome) implements JdtlsStarter {

    @Override
    public JdtlsConnection start(Path dataDirectory) throws IOException {
        ProcessBuilder command = new ProcessBuilder(launcher.toString(), "-data", dataDirectory.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        command.environment().put("JAVA_HOME", javaHome.toString());
        Process process = command.start();
        return new JdtlsConnection(process, process.getInputStream(), process.getOutputStream());
    }
}
