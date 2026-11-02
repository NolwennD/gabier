package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.protocol.UserId;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SocketGuardTest {

    private static final Set<PosixFilePermission> PRIVATE = PosixFilePermissions.fromString("rwx------");

    @TempDir
    Path directory;

    private final SocketGuard guard = new SocketGuard(UserId.current());

    private static ServerSocketChannel listenOn(Path socket) throws IOException {
        var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));

        return listener;
    }

    @Nested
    class Folder {

        @Test
        void isCreatedPrivateToTheUserWithItsMissingParents() throws IOException {
            var folder = directory.resolve("a/b/gabier");

            guard.prepareFolder(folder);

            assertThat(Files.getPosixFilePermissions(folder)).isEqualTo(PRIVATE);
            assertThat(Files.isDirectory(folder.getParent())).isTrue();
        }

        @Test
        void isAcceptedWhenItIsAlreadyThereAndPrivate() throws IOException {
            var folder =
                    Files.createDirectory(directory.resolve("gabier"), PosixFilePermissions.asFileAttribute(PRIVATE));

            guard.prepareFolder(folder);

            assertThat(Files.getPosixFilePermissions(folder)).isEqualTo(PRIVATE);
        }

        @Test
        void isRefusedWhenOtherUsersCanReadIt() throws IOException {
            var folder = Files.createDirectory(directory.resolve("gabier"));
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));

            var refusal = catchThrowableOfType(DaemonSocketException.class, () -> guard.prepareFolder(folder));

            assertThat(refusal.key()).isEqualTo(DaemonErrorKey.SOCKET_NOT_PRIVATE);
            assertThat(refusal.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
            assertThat(refusal.parameters()).containsEntry("path", folder.toString());
            assertThat(refusal.getMessage()).contains(folder.toString()).contains("rwxr-xr-x");
            // It is left as it is: not made private behind the back of whoever owns it.
            assertThat(Files.getPosixFilePermissions(folder)).isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
        }

        @Test
        void isRefusedWhenItBelongsToSomeoneElse() throws IOException {
            var folder =
                    Files.createDirectory(directory.resolve("gabier"), PosixFilePermissions.asFileAttribute(PRIVATE));
            var someoneElse = new SocketGuard(String.valueOf(Long.parseLong(UserId.current()) + 1));

            var refusal = catchThrowableOfType(DaemonSocketException.class, () -> someoneElse.prepareFolder(folder));

            assertThat(refusal.key()).isEqualTo(DaemonErrorKey.SOCKET_NOT_PRIVATE);
            assertThat(refusal.getMessage()).contains("owned by someone else");
        }

        @Test
        void isRefusedWhenItIsAFile() throws IOException {
            var file = Files.createFile(directory.resolve("gabier"));

            assertThatThrownBy(() -> guard.prepareFolder(file))
                    .isInstanceOfSatisfying(
                            DaemonSocketException.class,
                            refusal -> assertThat(refusal.getMessage()).contains("not a folder"));
        }

        @Test
        void isRefusedWhenItIsALinkToAFolder() throws IOException {
            var target =
                    Files.createDirectory(directory.resolve("target"), PosixFilePermissions.asFileAttribute(PRIVATE));
            var link = Files.createSymbolicLink(directory.resolve("gabier"), target);

            assertThatThrownBy(() -> guard.prepareFolder(link))
                    .isInstanceOfSatisfying(
                            DaemonSocketException.class,
                            refusal -> assertThat(refusal.getMessage()).contains("not a folder"));
        }

        @Test
        void failsWhenItCannotBeCreated() throws IOException {
            var file = Files.createFile(directory.resolve("file"));

            assertThatThrownBy(() -> guard.prepareFolder(file.resolve("gabier")))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    class StaleSocket {

        private Path socket;

        @BeforeEach
        void socketInTheTemporaryDirectory() {
            socket = directory.resolve("gabier.sock");
        }

        @Test
        void isNothingToDoWhenThereIsNoFile() throws IOException {
            guard.clearStale(socket);

            assertThat(socket).doesNotExist();
        }

        @Test
        void isRemovedWhenNobodyAnswersOnIt() throws IOException {
            listenOn(socket).close();
            assertThat(socket).exists();

            guard.clearStale(socket);

            assertThat(socket).doesNotExist();
        }

        @Test
        void refusesToStartWhenAnotherDaemonAnswersOnIt() throws IOException {
            try (var listener = listenOn(socket)) {
                var refusal = catchThrowableOfType(DaemonSocketException.class, () -> guard.clearStale(socket));

                assertThat(refusal.key()).isEqualTo(DaemonErrorKey.ALREADY_RUNNING);
                assertThat(refusal.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                assertThat(refusal.parameters()).containsEntry("socket", socket.toString());
                assertThat(listener.isOpen()).isTrue();
                assertThat(socket).exists();
            }
        }

        @Test
        void leavesAFileThatIsNotASocketAlone() throws IOException {
            var file = Files.writeString(socket, "precious");

            assertThatThrownBy(() -> guard.clearStale(socket))
                    .isInstanceOfSatisfying(DaemonSocketException.class, refusal -> {
                        assertThat(refusal.key()).isEqualTo(DaemonErrorKey.SOCKET_NOT_PRIVATE);
                        assertThat(refusal.getMessage()).contains("not a socket");
                    });
            assertThat(file).hasContent("precious");
        }

        @Test
        void leavesAFolderAlone() throws IOException {
            Files.createDirectory(socket);

            assertThatThrownBy(() -> guard.clearStale(socket)).isInstanceOf(DaemonSocketException.class);
            assertThat(socket).isDirectory();
        }

        @Test
        void leavesALinkAloneWhateverItPointsTo() throws IOException {
            var target = Files.writeString(directory.resolve("target"), "precious");
            Files.createSymbolicLink(socket, target);

            assertThatThrownBy(() -> guard.clearStale(socket)).isInstanceOf(DaemonSocketException.class);
            assertThat(target).hasContent("precious");
            assertThat(Files.isSymbolicLink(socket)).isTrue();
        }

        @Test
        void leavesASocketOfSomeoneElseAlone() throws IOException {
            listenOn(socket).close();
            var someoneElse = new SocketGuard(String.valueOf(Long.parseLong(UserId.current()) + 1));

            assertThatThrownBy(() -> someoneElse.clearStale(socket))
                    .isInstanceOfSatisfying(
                            DaemonSocketException.class,
                            refusal -> assertThat(refusal.getMessage()).contains("owned by someone else"));
            assertThat(socket).exists();
        }
    }

    @Nested
    class SocketFile {

        @Test
        void isMadeReadableAndWritableByTheUserOnly() throws IOException {
            var socket = directory.resolve("gabier.sock");
            try (var listener = listenOn(socket)) {
                guard.restrict(socket);

                assertThat(Files.getPosixFilePermissions(socket))
                        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
                assertThat(listener.isOpen()).isTrue();
            }
        }
    }
}
