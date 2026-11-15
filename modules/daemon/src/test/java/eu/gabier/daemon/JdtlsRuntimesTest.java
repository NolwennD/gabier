package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The JDKs a workspace is bound to, as the file {@code release} of each JDK describes it: no jdtls, no real JDK. */
class JdtlsRuntimesTest {

    @TempDir
    Path folder;

    /** A JDK folder holding the file {@code release} with the text given. */
    private Path jdk(String name, String release) throws IOException {
        Path home = Files.createDirectories(folder.resolve(name));
        Files.writeString(home.resolve("release"), release);
        return home;
    }

    @Test
    void namesTheExecutionEnvironmentAfterTheVersionOfTheJdkAndItsVersionAfterItsBuild() throws IOException {
        Path home = jdk("jdk-25", """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_VERSION="25.0.2"
            JAVA_VERSION_DATE="2026-01-20"
            JAVA_RUNTIME_VERSION="25.0.2+10-LTS"
            """);

        assertThat(Jdk.at(home)).contains(new Jdk("JavaSE-25", home, "25.0.2+10-LTS"));
    }

    @Test
    void namesJavaEightAfterItsOldNumbering() throws IOException {
        Path home = jdk("jdk-8", "JAVA_VERSION=\"1.8.0_402\"\n");

        assertThat(Jdk.at(home)).contains(new Jdk("JavaSE-1.8", home, "1.8.0_402"));
    }

    @Test
    void takesTheVersionAsTheBuildWhenTheReleaseHasNone() throws IOException {
        Path home = jdk("jdk-17", "JAVA_VERSION=\"17.0.10\"\n");

        assertThat(Jdk.at(home)).contains(new Jdk("JavaSE-17", home, "17.0.10"));
    }

    @Test
    void takesAVersionWithNoQuotesAndNoMinorNumber() throws IOException {
        Path home = jdk("jdk-27", "JAVA_VERSION=27\n");

        assertThat(Jdk.at(home)).contains(new Jdk("JavaSE-27", home, "27"));
    }

    @Test
    void knowsNoJdkWhereThereIsNoReleaseOrOneThatNamesNoVersion() throws IOException {
        assertThat(Jdk.at(folder.resolve("nowhere"))).isEmpty();
        assertThat(Jdk.at(jdk("no-version", "IMPLEMENTOR=\"Someone\"\n"))).isEmpty();
        assertThat(Jdk.at(jdk("odd-version", "JAVA_VERSION=\"early-access\"\n")))
                .isEmpty();
    }

    @Test
    void writesAJdkOnALineAndReadsItBack() throws IOException {
        Jdk jdk = Jdk.at(jdk("jdk-25", "JAVA_VERSION=\"25.0.2\"\n")).orElseThrow();

        assertThat(Jdk.parse(jdk.line())).contains(jdk);
    }

    @Test
    void readsNoJdkFromALineThatIsNotOneOfItsOwn() {
        assertThat(Jdk.parse("JavaSE-25\t/jdk")).isEmpty();
        assertThat(Jdk.parse("JavaSE-25\t/jdk\t25\textra")).isEmpty();
        assertThat(Jdk.parse("JavaSE-25\ta\0path\t25")).isEmpty();
    }

    @Test
    void isStillThereWhileTheFolderHoldsTheSameJdk() throws IOException {
        Path home = jdk("jdk-25", "JAVA_VERSION=\"25.0.2\"\n");
        Jdk jdk = Jdk.at(home).orElseThrow();

        assertThat(jdk.isStillThere()).isTrue();

        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"25.0.4\"\n");
        assertThat(jdk.isStillThere()).isFalse();

        Files.delete(home.resolve("release"));
        assertThat(jdk.isStillThere()).isFalse();
    }

    @Test
    void declaresTheJdksAsTheSettingRuntimesOfJdtls() {
        var settings = JdtlsRuntimes.settings(List.of(
                new Jdk("JavaSE-25", Path.of("/jdk25"), "25.0.2"), new Jdk("JavaSE-27", Path.of("/jdk27"), "27")));

        assertThat(settings)
                .isEqualTo(Map.of(
                        "java",
                        Map.of(
                                "configuration",
                                Map.of(
                                        "runtimes",
                                        List.of(
                                                Map.of("name", "JavaSE-25", "path", "/jdk25"),
                                                Map.of("name", "JavaSE-27", "path", "/jdk27"))))));
    }

    @Test
    void readsTheJdkOfEachProjectFromWhatJdtlsAnsweredAndKeepsTheFirstOfAnEnvironment() throws IOException {
        Path first = jdk("jdk-25-a", "JAVA_VERSION=\"25.0.2\"\n");
        Path second = jdk("jdk-25-b", "JAVA_VERSION=\"25.0.4\"\n");
        Path other = jdk("jdk-27", "JAVA_VERSION=\"27\"\n");
        List<Object> answers = new ArrayList<>();
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, first.toString()));
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, second.toString()));
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, other.toString()));

        assertThat(JdtlsRuntimes.boundIn(answers))
                .extracting(Jdk::name, Jdk::home)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("JavaSE-25", first),
                        org.assertj.core.groups.Tuple.tuple("JavaSE-27", other));
    }

    @Test
    void skipsTheProjectsWhoseJdkItCannotTell() throws IOException {
        Path known = jdk("jdk-25", "JAVA_VERSION=\"25.0.2\"\n");
        List<Object> answers = new ArrayList<>();
        answers.add(null);
        answers.add("not a map of settings");
        answers.add(Map.of());
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, 25));
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, "a\0path"));
        answers.add(Map.of(
                JdtlsRuntimes.VM_LOCATION, folder.resolve("without-release").toString()));
        answers.add(Map.of(JdtlsRuntimes.VM_LOCATION, known.toString()));

        assertThat(JdtlsRuntimes.boundIn(answers)).extracting(Jdk::home).containsExactly(known);
    }
}
