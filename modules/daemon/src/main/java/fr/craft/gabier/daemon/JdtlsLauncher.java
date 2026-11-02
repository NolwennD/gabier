package fr.craft.gabier.daemon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts the jdtls launcher of the distribution, which talks the protocol on its standard input and output.
 *
 * @param launcher the {@code jdtls} script of the distribution
 * @param javaHome the JDK the launcher must run jdtls with, so that it does not depend on the shell's {@code java}
 * @param jvmArguments the options of the JVM jdtls runs in ({@code -Dname=value}), given to the launcher as {@code
 *     --jvm-arg=<option>}
 */
record JdtlsLauncher(Path launcher, Path javaHome, List<String> jvmArguments) implements JdtlsStarter {

    @Override
    public JdtlsConnection start(Path dataDirectory) throws IOException {
        List<String> line = new ArrayList<>(List.of(launcher.toString(), "-data", dataDirectory.toString()));
        jvmArguments.forEach(argument -> line.add("--jvm-arg=" + argument));
        ProcessBuilder command = new ProcessBuilder(line).redirectError(ProcessBuilder.Redirect.INHERIT);
        command.environment().put("JAVA_HOME", javaHome.toString());
        Process process = command.start();
        return new JdtlsConnection(process, process.getInputStream(), process.getOutputStream());
    }
}
