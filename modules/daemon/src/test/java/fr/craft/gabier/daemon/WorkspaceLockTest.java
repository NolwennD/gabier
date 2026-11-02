package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The lock a daemon holds on the data folder of its jdtls, which jdtls itself does not lock. */
class WorkspaceLockTest {

    @TempDir
    Path workspace;

    @Test
    void isHeldForAnotherProcessWhileTakenAndFreeOnceGivenUp() throws Exception {
        var file = workspace.resolve(".gabier-lock");

        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(AnotherProcess.findsLocked(file, workspace)).isTrue();
        }

        assertThat(AnotherProcess.findsLocked(file, workspace)).isFalse();
    }

    @Test
    void checkingItNeverGivesUpTheLockThisProcessHoldsForAnotherProcess() throws Exception {
        var file = workspace.resolve(".gabier-lock");

        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(WorkspaceLock.isHeld(workspace)).isTrue();

            assertThat(AnotherProcess.findsLocked(file, workspace)).isTrue();
        }
    }

    @Test
    void aSecondAttemptToTakeItNeverGivesUpTheLockThisProcessHoldsForAnotherProcess() throws Exception {
        var file = workspace.resolve(".gabier-lock");

        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(WorkspaceLock.acquire(workspace)).isEmpty();

            assertThat(AnotherProcess.findsLocked(file, workspace)).isTrue();
        }
    }

    @Test
    void cannotBeTakenWhileAnotherProcessHoldsItAndCanOnceItEnds() throws Exception {
        var file = Files.createFile(workspace.resolve(".gabier-lock"));
        var holder = AnotherProcess.holding(file, workspace);

        assertThat(WorkspaceLock.isHeld(workspace)).isTrue();
        assertThat(WorkspaceLock.acquire(workspace)).isEmpty();

        holder.getOutputStream().close();
        holder.waitFor();

        assertThat(WorkspaceLock.isHeld(workspace)).isFalse();
        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
        }
    }

    @Test
    void isNotHeldByAWorkspaceThatNeverHadOne() {
        assertThat(WorkspaceLock.isHeld(workspace)).isFalse();
    }

    @Test
    void isTakenOnAFileOfTheWorkspaceWhichIsMadeWhenThereIsNone() throws IOException {
        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(workspace.resolve(".gabier-lock")).isRegularFile();
        }
    }

    @Test
    void isHeldWhileTheLockIsTakenAndNotAfter() throws IOException {
        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(WorkspaceLock.isHeld(workspace)).isTrue();
        }

        assertThat(WorkspaceLock.isHeld(workspace)).isFalse();
    }

    @Test
    void cannotBeTakenTwice() throws IOException {
        try (var lock = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(lock).isNotNull();
            assertThat(WorkspaceLock.acquire(workspace)).isEmpty();
        }

        try (var again = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(again).isNotNull();
        }
    }

    @Test
    void checkingDoesNotKeepTheLock() throws IOException {
        WorkspaceLock.acquire(workspace).orElseThrow().close();

        assertThat(WorkspaceLock.isHeld(workspace)).isFalse();
        assertThat(WorkspaceLock.isHeld(workspace)).isFalse();
    }

    @Test
    void isHeldWhenTheFileCannotBeOpenedForNothingProvesNobodyUsesTheWorkspace() throws IOException {
        Path file = Files.createFile(workspace.resolve(".gabier-lock"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));

        assertThat(WorkspaceLock.isHeld(workspace)).isTrue();
    }
}
