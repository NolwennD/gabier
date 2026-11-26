package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.SocketLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

        JdtlsConnection connection = new JdtlsLauncher(launcher, javaHome, List.of()).start(data);

        List<String> output = new String(connection.fromJdtls().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .toList();
        assertThat(output).containsExactly("-data " + data, javaHome.toString());
        assertThat(connection.process().waitFor()).isZero();
    }

    @Test
    void passesEachJvmArgumentToTheLauncherAsAJvmArgOption() throws Exception {
        Path launcher = folder.resolve("jdtls");
        Files.writeString(launcher, "#!/bin/sh\nfor argument in \"$@\"; do echo \"$argument\"; done\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
        Path data = folder.resolve("data");

        JdtlsConnection connection =
                new JdtlsLauncher(launcher, folder, List.of("-Dfirst=1", "-Dsecond=a b")).start(data);

        List<String> output = new String(connection.fromJdtls().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .toList();
        assertThat(output).containsExactly("-data", data.toString(), "--jvm-arg=-Dfirst=1", "--jvm-arg=-Dsecond=a b");
        assertThat(connection.process().waitFor()).isZero();
    }

    @Test
    void givesTheStreamsOfTheProcessToTalkToJdtls() throws Exception {
        Path launcher = folder.resolve("jdtls");
        Files.writeString(launcher, "#!/bin/sh\nread line\necho \"heard $line\"\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));

        JdtlsConnection connection = new JdtlsLauncher(launcher, folder, List.of()).start(folder.resolve("data"));
        connection.toJdtls().write("hello\n".getBytes(StandardCharsets.UTF_8));
        connection.toJdtls().flush();

        assertThat(new String(connection.fromJdtls().readAllBytes(), StandardCharsets.UTF_8).strip())
                .isEqualTo("heard hello");
        connection.process().waitFor();
    }

    @Test
    void givesWhatTheLauncherWritesOnItsStandardErrorToTheConsumerLineByLine() throws Exception {
        Path launcher = folder.resolve("jdtls");
        Files.writeString(launcher, "#!/bin/sh\necho \"first problem\" >&2\necho \"second problem\" >&2\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
        var lines = new LinkedBlockingQueue<String>();

        JdtlsConnection connection = new JdtlsLauncher(launcher, folder, List.of(), Optional.empty(), lines::add)
                .start(folder.resolve("data"));

        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo("first problem");
        assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo("second problem");
        connection.process().waitFor();
    }

    /** A launcher of a distribution of jdtls as the archive of Eclipse unpacks it, of the version given. */
    private Path launcherOfVersion(String version) throws IOException {
        Files.createDirectories(folder.resolve("jdtls/bin"));
        Files.createDirectories(folder.resolve("jdtls/plugins"));
        Files.createFile(folder.resolve("jdtls/plugins/org.eclipse.jdt.ls.core_" + version + ".jar"));
        return Files.createFile(folder.resolve("jdtls/bin/jdtls"));
    }

    @Test
    void keepsTheWorkspaceOfAProjectInTheCacheOfTheVersionOfJdtlsWithTheNameOfTheProject() throws IOException {
        Path cache = folder.resolve("cache");
        Path project = folder.resolve("work/project");
        var launcher =
                new JdtlsLauncher(launcherOfVersion("1.63.0"), folder.resolve("jdk"), List.of(), Optional.of(cache));

        var kept = launcher.keptWorkspace(project).orElseThrow();

        assertThat(kept.folder())
                .isEqualTo(cache.resolve("jdtls-1.63.0/workspaces").resolve(SocketLocation.projectKey(project)));
        assertThat(kept.projectRoot()).isEqualTo(project);
        assertThat(kept.fingerprint())
                .isEqualTo("jdtls 1.63.0, Java " + System.getProperty("java.runtime.version") + " in "
                        + folder.resolve("jdk"));
    }

    @Test
    void keepsTheFolderOfAProjectWhateverTheJdkAndChangesTheFingerprintWithIt() throws IOException {
        Path cache = folder.resolve("cache");
        var project = folder.resolve("work/project");
        Path distribution = launcherOfVersion("1.63.0");

        var first = new JdtlsLauncher(distribution, folder.resolve("jdk25"), List.of(), Optional.of(cache))
                .keptWorkspace(project)
                .orElseThrow();
        var second = new JdtlsLauncher(distribution, folder.resolve("jdk27"), List.of(), Optional.of(cache))
                .keptWorkspace(project)
                .orElseThrow();

        assertThat(second.folder()).isEqualTo(first.folder());
        assertThat(second.fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void keepsNoWorkspaceWithoutACacheFolderOrWhenItsDistributionTellsNoVersion() throws IOException {
        var project = folder.resolve("work/project");

        assertThat(new JdtlsLauncher(launcherOfVersion("1.63.0"), folder, List.of()).keptWorkspace(project))
                .isEmpty();
        assertThat(new JdtlsLauncher(folder.resolve("nowhere/jdtls"), folder, List.of(), Optional.of(folder))
                        .keptWorkspace(project))
                .isEmpty();
    }
}
