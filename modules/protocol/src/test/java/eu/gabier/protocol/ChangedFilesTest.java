package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.ChangedFiles.Moved;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChangedFilesTest {

    private static final Path GREETER = Path.of("/project/Greeter.java");
    private static final Path WELCOMER = Path.of("/project/Welcomer.java");
    private static final Path MAIN = Path.of("/project/Main.java");
    private static final String DIFF = "--- /project/Main.java\n+++ /project/Main.java\n@@ -1 +1 @@\n-\"a\"\n+\"b\"\n";

    private static final ChangedFiles EVERYTHING = new ChangedFiles(
            List.of(Path.of("/project/Created.java")),
            List.of(new Moved(GREETER, WELCOMER)),
            List.of(Path.of("/project/Gone.java")),
            List.of(MAIN),
            DIFF);

    private static ChangedFiles readBack(ChangedFiles changed) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", changed.toJson()));

        return ChangedFiles.of(response.result());
    }

    @Test
    void isWrittenAsTheResultOfARenameWithTheDiffEscapedOnOneLine() {
        var json = EVERYTHING.toJson();

        assertThat(json).isEqualTo("""
            {"created":["/project/Created.java"],\
            "renamed":[{"from":"/project/Greeter.java","to":"/project/Welcomer.java"}],\
            "deleted":["/project/Gone.java"],\
            "modified":["/project/Main.java"],\
            "diff":"--- /project/Main.java\\n+++ /project/Main.java\\n@@ -1 +1 @@\\n-\\"a\\"\\n+\\"b\\"\\n"}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void saysNothingChangedWithFourEmptyListsAndAnEmptyDiff() {
        assertThat(new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "").toJson())
                .isEqualTo("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[],\"diff\":\"\"}");
    }

    @Test
    void isReadFromWhatItWrites() {
        assertThat(readBack(EVERYTHING)).isEqualTo(EVERYTHING);
    }

    @Test
    void isRefusedWhenAMemberIsMissingOrOfAnotherType() {
        var valid = Map.<String, Object>of(
                "created", List.of(), "renamed", List.of(), "deleted", List.of(), "modified", List.of(), "diff", "");
        for (var name : valid.keySet()) {
            var without = new HashMap<>(valid);
            without.remove(name);
            var wrong = new HashMap<>(valid);
            wrong.put(name, 7);

            assertThatExceptionOfType(InvalidResponseException.class)
                    .as("without " + name)
                    .isThrownBy(() -> ChangedFiles.of(without));
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as("a number as " + name)
                    .isThrownBy(() -> ChangedFiles.of(wrong));
        }
    }

    @Test
    void isRefusedWhenAListHoldsSomethingElseThanPaths() {
        var invalid = List.of(
                Map.<String, Object>of(
                        "created",
                        List.of(7),
                        "renamed",
                        List.of(),
                        "deleted",
                        List.of(),
                        "modified",
                        List.of(),
                        "diff",
                        ""),
                Map.<String, Object>of(
                        "created",
                        List.of("a\u0000b"),
                        "renamed",
                        List.of(),
                        "deleted",
                        List.of(),
                        "modified",
                        List.of(),
                        "diff",
                        ""),
                Map.<String, Object>of(
                        "created",
                        List.of(),
                        "renamed",
                        List.of("x"),
                        "deleted",
                        List.of(),
                        "modified",
                        List.of(),
                        "diff",
                        ""),
                Map.<String, Object>of(
                        "created",
                        List.of(),
                        "renamed",
                        List.of(Map.of("from", "/a")),
                        "deleted",
                        List.of(),
                        "modified",
                        List.of(),
                        "diff",
                        ""),
                Map.<String, Object>of(
                        "created",
                        List.of(),
                        "renamed",
                        List.of(Map.of("from", "/a", "to", 7)),
                        "deleted",
                        List.of(),
                        "modified",
                        List.of(),
                        "diff",
                        ""));

        for (var result : invalid) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(result.toString())
                    .isThrownBy(() -> ChangedFiles.of(result));
        }
    }

    @Test
    void isRefusedWithoutItsMembers() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangedFiles(null, List.of(), List.of(), List.of(), ""));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangedFiles(List.of(), null, List.of(), List.of(), ""));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangedFiles(List.of(), List.of(), null, List.of(), ""));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangedFiles(List.of(), List.of(), List.of(), null, ""));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangedFiles(List.of(), List.of(), List.of(), List.of(), null));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Moved(null, WELCOMER));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Moved(GREETER, null));
    }
}
