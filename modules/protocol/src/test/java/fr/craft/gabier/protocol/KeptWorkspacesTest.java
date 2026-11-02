package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The jdtls workspaces that are kept between daemons: where they are, and removing the one of a project. */
class KeptWorkspacesTest {

    @TempDir
    Path folder;

    private Path keptWorkspaceOf(Path root) throws IOException {
        Path workspace = Files.createDirectories(KeptWorkspaces.of(folder, root).resolve(".metadata/.plugins"));
        Files.writeString(workspace.resolve("state"), "imported");

        return KeptWorkspaces.of(folder, root);
    }

    @Nested
    class Folder {

        @Test
        void isNamedByTheVariable() {
            var environment = Map.of("GABIER_JDTLS_WORKSPACES", "/var/cache/gabier-workspaces");

            assertThat(KeptWorkspaces.folder(environment)).contains(Path.of("/var/cache/gabier-workspaces"));
        }

        @Test
        void isMadeAbsoluteAndNormalized() {
            var environment = Map.of("GABIER_JDTLS_WORKSPACES", "kept/../workspaces");

            assertThat(KeptWorkspaces.folder(environment))
                    .contains(Path.of("workspaces").toAbsolutePath());
        }

        @Test
        void isNoneWhenTheVariableIsNotSetOrBlank() {
            assertThat(KeptWorkspaces.folder(Map.of())).isEmpty();
            assertThat(KeptWorkspaces.folder(Map.of("GABIER_JDTLS_WORKSPACES", " ")))
                    .isEmpty();
        }
    }

    @Nested
    class Workspace {

        @Test
        void isAFolderNamedByAHashOfTheRootInTheKeptFolder() {
            var workspace = KeptWorkspaces.of(Path.of("/kept"), Path.of("/work/project"));

            assertThat(workspace.getParent()).isEqualTo(Path.of("/kept"));
            assertThat(workspace.getFileName().toString()).matches("[0-9a-f]{32}");
        }

        @Test
        void isTheSameForTheSameRootAndNotForAnother() {
            var root = Path.of("/work/project");

            assertThat(KeptWorkspaces.of(Path.of("/kept"), root)).isEqualTo(KeptWorkspaces.of(Path.of("/kept"), root));
            assertThat(KeptWorkspaces.of(Path.of("/kept"), root))
                    .isNotEqualTo(KeptWorkspaces.of(Path.of("/kept"), Path.of("/work/other")));
        }
    }

    @Nested
    class RootOfAFolder {

        @Test
        void isTheRealPathOfAFolderThatExists() throws IOException {
            Path real = Files.createDirectories(folder.resolve("real"));
            Path link = Files.createSymbolicLink(folder.resolve("link"), real);

            assertThat(KeptWorkspaces.rootOf(link)).isEqualTo(real.toRealPath());
        }

        @Test
        void isTheAbsoluteNormalizedPathOfAFolderThatIsGone() {
            assertThat(KeptWorkspaces.rootOf(folder.resolve("gone/../deleted-worktree")))
                    .isEqualTo(folder.resolve("deleted-worktree"));
        }
    }

    @Nested
    class Removal {

        @Test
        void removesTheWorkspaceOfTheProjectAndOnlyThat() throws IOException {
            Path root = Path.of("/work/project");
            Path mine = keptWorkspaceOf(root);
            Path other = keptWorkspaceOf(Path.of("/work/other"));

            assertThat(KeptWorkspaces.remove(folder, root)).isTrue();

            assertThat(mine).doesNotExist();
            assertThat(other.resolve(".metadata/.plugins/state")).hasContent("imported");
        }

        @Test
        void doesNothingTheSecondTimeOrWhenNothingWasKept() throws IOException {
            Path root = Path.of("/work/project");
            keptWorkspaceOf(root);
            KeptWorkspaces.remove(folder, root);

            assertThat(KeptWorkspaces.remove(folder, root)).isFalse();
            assertThat(KeptWorkspaces.remove(folder.resolve("no-such-folder"), root))
                    .isFalse();
        }

        @Test
        void isRefusedWhileAJdtlsRunsOnTheWorkspaceAndLeavesItAsItIs() throws IOException {
            Path root = Path.of("/work/project");
            Path workspace = keptWorkspaceOf(root);

            try (var held = WorkspaceLock.acquire(workspace).orElseThrow()) {
                assertThat(held).isNotNull();
                assertThatThrownBy(() -> KeptWorkspaces.remove(folder, root))
                        .isInstanceOfSatisfying(GeneratorException.class, refusal -> {
                            assertThat(refusal.key().get()).isEqualTo("workspace-in-use");
                            assertThat(refusal.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                            assertThat(refusal.parameters())
                                    .containsEntry("project", root.toString())
                                    .containsEntry("workspace", workspace.toString())
                                    .containsEntry("variable", "GABIER_JDTLS_WORKSPACES");
                            assertThat(refusal.getMessage()).contains("GABIER_JDTLS_WORKSPACES");
                        });
            }

            assertThat(workspace.resolve(".metadata/.plugins/state")).hasContent("imported");
            assertThat(KeptWorkspaces.remove(folder, root)).isTrue();
        }
    }
}
