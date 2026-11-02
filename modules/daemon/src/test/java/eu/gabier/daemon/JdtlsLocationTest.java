package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtlsLocationTest {

    @TempDir
    Path directory;

    private Path home() {
        return directory.resolve("home");
    }

    private Path launcherAt(Path launcher) throws IOException {
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, "#!/bin/sh\n");
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));

        return launcher;
    }

    private Path installed() throws IOException {
        return launcherAt(home().resolve(".local/share/jdtls/" + JdtlsLocation.PINNED_VERSION + "/bin/jdtls"));
    }

    private Path onThePath() throws IOException {
        return launcherAt(directory.resolve("path/bin/jdtls"));
    }

    private Map<String, String> pathOfTheLauncher() {
        return Map.of("PATH", directory.resolve("elsewhere") + File.pathSeparator + directory.resolve("path/bin"));
    }

    @Nested
    class Finding {

        @Test
        void takesTheLauncherTheVariableNamesWhateverIsInstalled() throws IOException {
            installed();
            onThePath();
            var environment = Map.of(
                    "GABIER_JDTLS",
                    "/chosen/jdtls",
                    "PATH",
                    directory.resolve("path/bin").toString());

            assertThat(JdtlsLocation.launcher(environment, home())).isEqualTo(Path.of("/chosen/jdtls"));
        }

        @Test
        void takesTheInstalledPinnedJdtlsWhenTheVariableIsNotSet() throws IOException {
            var launcher = installed();
            onThePath();

            assertThat(JdtlsLocation.launcher(pathOfTheLauncher(), home())).isEqualTo(launcher);
        }

        @Test
        void takesTheInstalledPinnedJdtlsWhenTheVariableIsBlank() throws IOException {
            var launcher = installed();

            assertThat(JdtlsLocation.launcher(Map.of("GABIER_JDTLS", " "), home()))
                    .isEqualTo(launcher);
        }

        @Test
        void takesTheJdtlsOfThePathWhenThePinnedOneIsNotInstalled() throws IOException {
            var launcher = onThePath();

            assertThat(JdtlsLocation.launcher(pathOfTheLauncher(), home())).isEqualTo(launcher);
        }

        @Test
        void ignoresAPinnedLauncherThatCannotBeRun() throws IOException {
            var launcher = installed();
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rw-------"));
            var onThePath = onThePath();

            assertThat(JdtlsLocation.launcher(pathOfTheLauncher(), home())).isEqualTo(onThePath);
        }

        @Test
        void ignoresAFolderNamedJdtlsOnThePath() throws IOException {
            Files.createDirectories(directory.resolve("path/bin/jdtls"));

            assertThatThrownBy(() -> JdtlsLocation.launcher(pathOfTheLauncher(), home()))
                    .isInstanceOf(IOException.class);
        }

        @Test
        void skipsTheEmptyEntriesOfThePath() {
            var environment = Map.of("PATH", File.pathSeparator);

            assertThatThrownBy(() -> JdtlsLocation.launcher(environment, home()))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    class Refusing {

        @Test
        void namesThePlacesLookedAtAndTheInstallWhenThereIsNoJdtls() {
            assertThatThrownBy(() -> JdtlsLocation.launcher(Map.of("PATH", directory.toString()), home()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("GABIER_JDTLS")
                    .hasMessageContaining(
                            home().resolve(".local/share/jdtls/" + JdtlsLocation.PINNED_VERSION + "/bin/jdtls")
                                    .toString())
                    .hasMessageContaining("PATH")
                    .hasMessageContaining("mise run jdtls:install");
        }

        @Test
        void refusesWhenThereIsNoPathEither() {
            assertThatThrownBy(() -> JdtlsLocation.launcher(Map.of(), home()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("mise run jdtls:install");
        }
    }

    @Nested
    class Pinned {

        @Test
        void isTheVersionOfMiseToml() {
            assertThat(JdtlsLocation.PINNED_VERSION).isEqualTo(System.getProperty("gabier.jdtlsVersion"));
        }

        @Test
        void readsTheVersionOfTheResource() {
            var resource = new ByteArrayInputStream("version=1.63.0\n".getBytes(StandardCharsets.UTF_8));

            assertThat(JdtlsLocation.read(resource)).isEqualTo("1.63.0");
        }

        @Test
        void refusesAResourceThatIsNotThere() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> JdtlsLocation.read(null))
                    .withMessageContaining("jdtls.properties");
        }

        @Test
        void refusesAResourceWithNoVersion() {
            var resource = new ByteArrayInputStream("other=1.63.0\n".getBytes(StandardCharsets.UTF_8));

            assertThatIllegalStateException()
                    .isThrownBy(() -> JdtlsLocation.read(resource))
                    .withMessageContaining("jdtls.properties");
        }

        @Test
        void refusesAResourceThatTheBuildLeftUnfilled() {
            var resource = new ByteArrayInputStream("version=@version@\n".getBytes(StandardCharsets.UTF_8));

            assertThatIllegalStateException()
                    .isThrownBy(() -> JdtlsLocation.read(resource))
                    .withMessageContaining("jdtls.properties");
        }

        @Test
        void reportsAResourceThatCannotBeRead() {
            var broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("disk");
                }
            };

            assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> JdtlsLocation.read(broken));
        }
    }
}
