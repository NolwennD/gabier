package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The one place where a jdtls process is started. A script stands in for the launcher: no jdtls is started. */
class JdtlsLauncherTest {

    @TempDir
    Path folder;

    @Test
    void startsTheLauncherWithTheDataDirectoryAndTheJavaHomeOfTheBuild() throws Exception {
        Path launcher = folder.resolve("jdtls");
        Files.writeString(launcher, "#!/bin/sh\necho \"$@\"\necho \"$JAVA_HOME\"\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
        Path data = folder.resolve("data");
        Path javaHome = folder.resolve("java");

        JdtlsConnection connection = new JdtlsLauncher(launcher, javaHome).start(data);

        List<String> output = new String(connection.fromJdtls().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .toList();
        assertThat(output).containsExactly("-data " + data, javaHome.toString());
        assertThat(connection.process().waitFor()).isZero();
    }

    @Test
    void givesTheStreamsOfTheProcessToTalkToJdtls() throws Exception {
        Path launcher = folder.resolve("jdtls");
        Files.writeString(launcher, "#!/bin/sh\nread line\necho \"heard $line\"\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));

        JdtlsConnection connection = new JdtlsLauncher(launcher, folder).start(folder.resolve("data"));
        connection.toJdtls().write("hello\n".getBytes(StandardCharsets.UTF_8));
        connection.toJdtls().flush();

        assertThat(new String(connection.fromJdtls().readAllBytes(), StandardCharsets.UTF_8).strip())
                .isEqualTo("heard hello");
        connection.process().waitFor();
    }
}
