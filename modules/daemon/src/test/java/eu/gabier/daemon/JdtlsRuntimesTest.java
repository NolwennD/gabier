package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.protocol.ProjectJdks;
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

    @Test
    void declaresTheJdksChosenInPlaceOfTheOnesJdtlsBoundAndKeepsTheOthers() {
        var drawn25 = new Jdk("JavaSE-25", Path.of("/drawn25"), "25.0.4");
        var drawn17 = new Jdk("JavaSE-17", Path.of("/drawn17"), "17.0.10");
        var chosen25 = new Jdk("JavaSE-25", Path.of("/chosen25"), "25.0.2");

        assertThat(JdtlsRuntimes.overlay(List.of(drawn25, drawn17), List.of(chosen25)))
                .containsExactly(chosen25, drawn17);
        assertThat(JdtlsRuntimes.overlay(List.of(drawn25), List.of())).containsExactly(drawn25);
    }

    @Test
    void declaresNoJdkForAnEnvironmentNoProjectIsBoundTo() {
        var drawn25 = new Jdk("JavaSE-25", Path.of("/drawn25"), "25.0.4");
        var chosen21 = new Jdk("JavaSE-21", Path.of("/chosen21"), "21.0.1");

        assertThat(JdtlsRuntimes.overlay(List.of(drawn25), List.of(chosen21))).containsExactly(drawn25);
        assertThat(JdtlsRuntimes.overlay(List.of(), List.of(chosen21))).isEmpty();
    }

    @Test
    void tellsEachJdkBoundAndWhoChoseIt() {
        var chosen25 = new Jdk("JavaSE-25", Path.of("/chosen25"), "25.0.2");
        var drawn17 = new Jdk("JavaSE-17", Path.of("/drawn17"), "17.0.10");
        var chosen = new RuntimeSources.Chosen(
                List.of(new RuntimeSources.Choice(chosen25, "chosen in a file")), List.of("a source warns"));

        var jdks = JdtlsRuntimes.describe(List.of(chosen25, drawn17), chosen);

        assertThat(jdks.bindings())
                .containsExactly(
                        new ProjectJdks.Binding("JavaSE-25", Path.of("/chosen25"), "chosen in a file"),
                        new ProjectJdks.Binding("JavaSE-17", Path.of("/drawn17"), "bound by jdtls"));
        assertThat(jdks.warnings()).containsExactly("a source warns");
    }

    @Test
    void warnsWhenJdtlsBoundAnEnvironmentToAnotherJdkThanTheOneChosen() {
        var chosen25 = new Jdk("JavaSE-25", Path.of("/chosen25"), "25.0.2");
        var drawn25 = new Jdk("JavaSE-25", Path.of("/drawn25"), "25.0.4");
        var chosen =
                new RuntimeSources.Chosen(List.of(new RuntimeSources.Choice(chosen25, "chosen in a file")), List.of());

        var jdks = JdtlsRuntimes.describe(List.of(drawn25), chosen);

        assertThat(jdks.bindings())
                .containsExactly(new ProjectJdks.Binding("JavaSE-25", Path.of("/drawn25"), "bound by jdtls"));
        assertThat(jdks.warnings())
                .singleElement()
                .asString()
                .contains("JavaSE-25")
                .contains("/drawn25")
                .contains("/chosen25")
                .contains("chosen in a file");
    }

    @Test
    void saysNothingOfAChoiceForAnEnvironmentNoProjectIsBoundTo() {
        var chosen21 = new Jdk("JavaSE-21", Path.of("/chosen21"), "21.0.1");
        var chosen =
                new RuntimeSources.Chosen(List.of(new RuntimeSources.Choice(chosen21, "chosen in a file")), List.of());

        assertThat(JdtlsRuntimes.describe(List.of(), chosen)).isEqualTo(ProjectJdks.NONE);
        assertThat(JdtlsRuntimes.describe(List.of(), new RuntimeSources.Chosen(List.of(), List.of("a source warns"))))
                .isEqualTo(new ProjectJdks(List.of(), List.of("a source warns")));
    }

    @Test
    void givesJdtlsTheJdksAsTheSectionConfigurationOfItsSettings() {
        var jdk = new Jdk("JavaSE-25", Path.of("/jdk25"), "25.0.2");

        assertThat(JdtlsRuntimes.configuration(List.of(jdk)))
                .isEqualTo(Map.of("runtimes", List.of(Map.of("name", "JavaSE-25", "path", "/jdk25"))));
    }
}
