package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SocketLocationTest {

    // The largest uid there is (an unsigned 32 bits integer): the longest default path a user can get.
    private static final String LARGEST_UID = "4294967295";

    private static Path resolve(Map<String, String> environment) {
        return SocketLocation.resolve(environment, "1000");
    }

    private static int bytes(Path path) {
        return path.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    /** An absolute path of exactly this many bytes: copies of the character, then ASCII to fill what is left. */
    private static String pathOfBytes(int bytes, String character) {
        var width = character.getBytes(StandardCharsets.UTF_8).length;
        var copies = (bytes - 1) / width;

        return "/" + character.repeat(copies) + "x".repeat(bytes - 1 - copies * width);
    }

    @Nested
    class Default {

        @Test
        void isInTheRuntimeDirectoryOfTheUser() {
            var path = resolve(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"));

            assertThat(path).isEqualTo(Path.of("/run/user/1000/gabier/gabier.sock"));
        }

        @Test
        void isInTheTemporaryDirectoryNamedAfterTheUserWithoutARuntimeDirectory() {
            assertThat(resolve(Map.of())).isEqualTo(Path.of("/tmp/gabier-1000/gabier.sock"));
        }

        @Test
        void ignoresABlankRuntimeDirectory() {
            assertThat(resolve(Map.of("XDG_RUNTIME_DIR", " "))).isEqualTo(Path.of("/tmp/gabier-1000/gabier.sock"));
        }

        @Test
        void ignoresARelativeRuntimeDirectory() {
            assertThat(resolve(Map.of("XDG_RUNTIME_DIR", "run/user")))
                    .isEqualTo(Path.of("/tmp/gabier-1000/gabier.sock"));
        }

        @Test
        void isShortEvenForTheLargestUid() {
            var withRuntimeDirectory =
                    SocketLocation.resolve(Map.of("XDG_RUNTIME_DIR", "/run/user/" + LARGEST_UID), LARGEST_UID);
            var withoutRuntimeDirectory = SocketLocation.resolve(Map.of(), LARGEST_UID);

            assertThat(bytes(withRuntimeDirectory)).isLessThan(SocketLocation.MAXIMUM_BYTES);
            assertThat(bytes(withoutRuntimeDirectory)).isLessThan(SocketLocation.MAXIMUM_BYTES);
        }
    }

    @Nested
    class Override {

        @Test
        void winsOverTheRuntimeDirectory() {
            var environment = Map.of("GABIER_SOCKET", "/somewhere/g.sock", "XDG_RUNTIME_DIR", "/run/user/1000");

            assertThat(resolve(environment)).isEqualTo(Path.of("/somewhere/g.sock"));
        }

        @Test
        void isIgnoredWhenBlank() {
            assertThat(resolve(Map.of("GABIER_SOCKET", "  "))).isEqualTo(Path.of("/tmp/gabier-1000/gabier.sock"));
        }

        @Test
        void isMadeAbsoluteAndNormalized() {
            var path = resolve(Map.of("GABIER_SOCKET", "relative/../g.sock"));

            assertThat(path).isAbsolute().isEqualTo(Path.of("g.sock").toAbsolutePath());
        }
    }

    @Nested
    class Length {

        @Test
        void acceptsAPathOfExactlyTheLimit() {
            var path = pathOfBytes(107, "a");

            assertThat(resolve(Map.of("GABIER_SOCKET", path))).isEqualTo(Path.of(path));
        }

        @Test
        void acceptsAPathOfTheLimitMadeOfMultiByteCharacters() {
            var path = pathOfBytes(107, "é");

            assertThat(path.length()).isLessThan(107);
            assertThat(resolve(Map.of("GABIER_SOCKET", path))).isEqualTo(Path.of(path));
        }

        @Test
        void refusesAPathOfOneByteOverTheLimit() {
            var path = pathOfBytes(108, "a");

            assertThatExceptionOfType(SocketPathTooLongException.class)
                    .isThrownBy(() -> resolve(Map.of("GABIER_SOCKET", path)));
        }

        @Test
        void refusesAPathOf120Characters() {
            var path = "/" + "a".repeat(119);

            var failure = refused(path);

            assertThat(failure.parameters()).containsEntry("length", "120");
        }

        @Test
        void refusesAShortInCharactersButLongInBytesPath() {
            var path = pathOfBytes(107, "é") + "é";

            assertThat(path.length()).isLessThan(107);
            var failure = refused(path);

            assertThat(failure.parameters()).containsEntry("length", String.valueOf(bytes(Path.of(path))));
        }

        @Test
        void refusesALongRuntimeDirectory() {
            var runtimeDirectory = "/" + "r".repeat(100);

            assertThatExceptionOfType(SocketPathTooLongException.class)
                    .isThrownBy(() -> resolve(Map.of("XDG_RUNTIME_DIR", runtimeDirectory)));
        }

        @Test
        void givesThePathItsLengthTheLimitAndTheOverride() {
            var path = "/" + "a".repeat(119);

            var failure = refused(path);

            assertThat(failure.key()).isEqualTo(ProtocolErrorKey.SOCKET_PATH_TOO_LONG);
            assertThat(failure.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(failure.parameters())
                    .containsEntry("path", path)
                    .containsEntry("length", "120")
                    .containsEntry("limit", "107")
                    .containsEntry("override", "GABIER_SOCKET");
            assertThat(failure.getMessage()).contains("GABIER_SOCKET").contains("107");
        }

        private GeneratorException refused(String path) {
            return catchThrowableOfType(SocketPathTooLongException.class, () -> resolve(Map.of("GABIER_SOCKET", path)));
        }
    }
}
