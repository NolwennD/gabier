package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocketLocationTest {

    // The largest uid there is (an unsigned 32 bits integer): the longest default path a user can get.
    private static final String LARGEST_UID = "4294967295";

    private static final Path ELSEWHERE = Path.of("/work/project");
    private static final Pattern NAME = Pattern.compile("(.+)-([0-9a-f]{8})\\.sock");

    @TempDir
    Path folder;

    private static Path resolve(Map<String, String> environment) {
        return SocketLocation.resolve(environment, "1000", ELSEWHERE);
    }

    /** A project the way Git makes one: a folder with a {@code .git} (a folder, or a file in a worktree). */
    private Path project(String name) throws IOException {
        var root = Files.createDirectories(folder.resolve(name)).toRealPath();
        Files.createFile(root.resolve(".git"));

        return root;
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
        void isInTheRuntimeDirectoryOfTheUserNamedAfterTheFolderOfTheProjectAndAHashOfItsPath() throws IOException {
            var root = project("demo");

            var path = SocketLocation.resolve(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"), "1000", root);

            assertThat(path.getParent()).isEqualTo(Path.of("/run/user/1000/gabier"));
            var name = NAME.matcher(path.getFileName().toString());
            assertThat(name.matches()).as(path.toString()).isTrue();
            assertThat(name.group(1)).isEqualTo("demo");
        }

        @Test
        void isInTheTemporaryDirectoryNamedAfterTheUserWithoutARuntimeDirectory() throws IOException {
            var path = SocketLocation.resolve(Map.of(), "1000", project("demo"));

            assertThat(path.getParent()).isEqualTo(Path.of("/tmp/gabier-1000"));
            assertThat(path.getFileName().toString()).matches(NAME);
        }

        @Test
        void ignoresABlankRuntimeDirectory() throws IOException {
            var path = SocketLocation.resolve(Map.of("XDG_RUNTIME_DIR", " "), "1000", project("demo"));

            assertThat(path.getParent()).isEqualTo(Path.of("/tmp/gabier-1000"));
        }

        @Test
        void ignoresARelativeRuntimeDirectory() throws IOException {
            var path = SocketLocation.resolve(Map.of("XDG_RUNTIME_DIR", "run/user"), "1000", project("demo"));

            assertThat(path.getParent()).isEqualTo(Path.of("/tmp/gabier-1000"));
        }

        @Test
        void isTheSameForTheSameProject() throws IOException {
            var root = project("demo");

            assertThat(SocketLocation.resolve(Map.of(), "1000", root))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void isAnotherForEachWorktreeEvenWhenTheyHaveTheSameName() throws IOException {
            var first = SocketLocation.resolve(Map.of(), "1000", project("one/demo"));
            var second = SocketLocation.resolve(Map.of(), "1000", project("two/demo"));
            var other = SocketLocation.resolve(Map.of(), "1000", project("other"));

            assertThat(first).isNotEqualTo(second).isNotEqualTo(other);
            assertThat(first.getFileName().toString()).startsWith("demo-");
            assertThat(second.getFileName().toString()).startsWith("demo-");
        }

        @Test
        void isTheOneOfTheRootFromASubfolder() throws IOException {
            var root = project("demo");
            var deep = Files.createDirectories(root.resolve("modules/daemon/src"));

            assertThat(SocketLocation.resolve(Map.of(), "1000", deep))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void isTheOneOfAFolderThatHoldsAGitFolderAsWellAsAGitFile() throws IOException {
            var clone = Files.createDirectories(folder.resolve("clone"));
            Files.createDirectories(clone.resolve(".git"));
            var sub = Files.createDirectories(clone.resolve("sub"));

            assertThat(SocketLocation.resolve(Map.of(), "1000", sub))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", clone));
        }

        @Test
        void isTheOneOfTheWorkingDirectoryWhenNoFolderAboveItIsAProject() throws IOException {
            var plain = Files.createDirectories(folder.resolve("plain/inner")).toRealPath();

            var path = SocketLocation.resolve(Map.of(), "1000", plain);

            assertThat(path.getFileName().toString()).startsWith("inner-");
            assertThat(path).isNotEqualTo(SocketLocation.resolve(Map.of(), "1000", plain.getParent()));
        }

        @Test
        void isTheOneOfTheProjectThatTheVariableNamesWhateverTheWorkingDirectory() throws IOException {
            var root = project("demo");
            var elsewhere = project("elsewhere");

            assertThat(SocketLocation.resolve(Map.of("GABIER_PROJECT", root.toString()), "1000", elsewhere))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void takesARelativeProjectFromTheWorkingDirectory() throws IOException {
            var root = project("demo");
            var sibling = Files.createDirectories(root.resolve("modules/a"));

            assertThat(SocketLocation.resolve(Map.of("GABIER_PROJECT", "../.."), "1000", sibling))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void ignoresABlankProjectVariable() throws IOException {
            var root = project("demo");

            assertThat(SocketLocation.resolve(Map.of("GABIER_PROJECT", " "), "1000", root))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void followsALinkToTheRealFolderSoThatBothNamesGiveOneSocket() throws IOException {
            var root = project("demo");
            var link = Files.createSymbolicLink(folder.resolve("link"), root);

            assertThat(SocketLocation.resolve(Map.of(), "1000", link))
                    .isEqualTo(SocketLocation.resolve(Map.of(), "1000", root));
        }

        @Test
        void takesTheAbsoluteNormalizedPathOfAProjectFolderThatDoesNotExist() {
            var gone = Map.of(
                    "GABIER_PROJECT", folder.resolve("gone/../deleted-worktree").toString());

            assertThat(SocketLocation.projectRoot(gone, folder)).isEqualTo(folder.resolve("deleted-worktree"));
        }

        @Test
        void isNamedWithPlainCharactersWhateverTheNameOfTheFolder() throws IOException {
            var path = SocketLocation.resolve(Map.of(), "1000", project("my demo\u00e9\u001b[31m"));

            assertThat(path.getFileName().toString()).matches("my_demo___31m-[0-9a-f]{8}\\.sock");
        }

        @Test
        void keepsTheStartOfALongName() throws IOException {
            var path = SocketLocation.resolve(Map.of(), "1000", project("a".repeat(200)));

            var name = NAME.matcher(path.getFileName().toString());
            assertThat(name.matches()).isTrue();
            assertThat(name.group(1)).isEqualTo("a".repeat(40));
        }

        @Test
        void hasAReadableNameForTheRootOfTheFileSystem() {
            var path = SocketLocation.resolve(Map.of(), "1000", Path.of("/"));

            assertThat(path.getFileName().toString()).startsWith("root-");
        }

        @Test
        void isShortEvenForTheLargestUidAndTheLongestName() throws IOException {
            var root = project("b".repeat(255));
            var withRuntimeDirectory =
                    SocketLocation.resolve(Map.of("XDG_RUNTIME_DIR", "/run/user/" + LARGEST_UID), LARGEST_UID, root);
            var withoutRuntimeDirectory = SocketLocation.resolve(Map.of(), LARGEST_UID, root);

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
            assertThat(resolve(Map.of("GABIER_SOCKET", "  ")).getParent()).isEqualTo(Path.of("/tmp/gabier-1000"));
        }

        @Test
        void winsOverTheProjectToo() throws IOException {
            var environment = Map.of(
                    "GABIER_SOCKET",
                    "/somewhere/g.sock",
                    "GABIER_PROJECT",
                    project("demo").toString());

            assertThat(resolve(environment)).isEqualTo(Path.of("/somewhere/g.sock"));
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
