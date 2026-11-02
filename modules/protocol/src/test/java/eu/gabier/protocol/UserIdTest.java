package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserIdTest {

    @TempDir
    Path directory;

    @Test
    void isTheOwnerOfTheFilesTheProcessCreates() throws IOException {
        var created = Files.createFile(directory.resolve("mine"));

        assertThat(UserId.current()).isEqualTo(String.valueOf(Files.getAttribute(created, "unix:uid")));
    }

    @Test
    void isTheOwnerOfTheGivenPath() throws IOException {
        var created = Files.createFile(directory.resolve("mine"));

        assertThat(UserId.ownerOf(created)).isEqualTo(String.valueOf(Files.getAttribute(created, "unix:uid")));
    }

    @Test
    void isReportedAsAnInternalErrorWhenThePathCannotBeRead() {
        var failure = catchThrowableOfType(GeneratorException.class, () -> UserId.ownerOf(directory.resolve("absent")));

        assertThat(failure.key()).isEqualTo(ProtocolErrorKey.USER_UNKNOWN);
        assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        assertThat(failure.getCause()).isInstanceOf(IOException.class);
    }
}
