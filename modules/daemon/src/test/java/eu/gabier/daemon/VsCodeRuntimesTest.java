package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The source of the JDKs in {@code .vscode/settings.json}: the files of a folder, no jdtls, no real JDK. */
class VsCodeRuntimesTest {

    @TempDir
    Path root;

    private Path jdk(String name, String version) throws IOException {
        Path home = Files.createDirectories(root.resolve("jdks").resolve(name));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");

        return home;
    }

    private void settings(String text) throws IOException {
        Files.createDirectories(root.resolve(".vscode"));
        Files.writeString(root.resolve(".vscode/settings.json"), text);
    }

    private void choosing(String environment, Path home) throws IOException {
        settings("""
            {"java.configuration.runtimes": [{"name": "%s", "path": "%s"}]}
            """.formatted(environment, home));
    }

    @Test
    void saysItIsTheFileOfTheProjectThatChose() {
        assertThat(new VsCodeRuntimes().description()).isEqualTo("chosen in .vscode/settings.json");
    }

    @Test
    void choosesNothingWhenTheProjectSaysNothing() throws IOException {
        assertThat(new VsCodeRuntimes().read(root)).isEqualTo(new RuntimeSource.Found(List.of(), List.of()));

        settings("{\"java.completion.importOrder\": [\"#\", \"\"]}");

        assertThat(new VsCodeRuntimes().read(root)).isEqualTo(new RuntimeSource.Found(List.of(), List.of()));
    }

    @Test
    void choosesTheJdkAnEntryNamesWhenItIsOfTheVersionOfTheEnvironment() throws IOException {
        Path home = jdk("temurin-25", "25.0.2");
        choosing("JavaSE-25", home);

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).containsExactly(new Jdk("JavaSE-25", home, "25.0.2"));
        assertThat(chosen.warnings()).isEmpty();
    }

    @Test
    void readsTheSettingsAsVsCodeWritesThem() throws IOException {
        Path home = jdk("temurin-25", "25.0.2");
        settings("""
            {
              // the JDK of the build
              "java.configuration.runtimes": [
                {"name": "JavaSE-25", "path": "%s", "default": true,},
              ],
            }
            """.formatted(home));

        assertThat(new VsCodeRuntimes().read(root).jdks()).extracting(Jdk::home).containsExactly(home);
    }

    @Test
    void choosesOneJdkForEachEnvironment() throws IOException {
        Path jdk25 = jdk("temurin-25", "25.0.2");
        Path jdk17 = jdk("temurin-17", "17.0.10");
        settings("""
            {"java.configuration.runtimes": [
              {"name": "JavaSE-25", "path": "%s"}, {"name": "JavaSE-17", "path": "%s"}]}
            """.formatted(jdk25, jdk17));

        assertThat(new VsCodeRuntimes().read(root).jdks())
                .extracting(Jdk::name)
                .containsExactly("JavaSE-25", "JavaSE-17");
    }

    @Test
    void refusesAJdkThatIsNotThereAndSaysWhere() throws IOException {
        Path missing = root.resolve("jdks/nowhere");
        choosing("JavaSE-25", missing);

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).isEmpty();
        assertThat(chosen.warnings())
                .singleElement()
                .asString()
                .contains("JavaSE-25")
                .contains(missing.toString())
                .contains("no JDK")
                .contains(".vscode/settings.json");
    }

    @Test
    void refusesAJdkOfAnotherVersionThanTheEnvironmentAndSaysBoth() throws IOException {
        Path home = jdk("temurin-21", "21.0.1");
        choosing("JavaSE-25", home);

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).isEmpty();
        assertThat(chosen.warnings())
                .singleElement()
                .asString()
                .contains("JavaSE-25")
                .contains("JavaSE-21")
                .contains(home.toString());
    }

    @Test
    void refusesAPathThatIsNotAbsolute() throws IOException {
        choosing("JavaSE-25", Path.of("jdks/temurin-25"));

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).isEmpty();
        assertThat(chosen.warnings()).singleElement().asString().contains("not absolute");
    }

    @Test
    void keepsTheFirstEntryOfAnEnvironmentAndSaysThatAnotherWasIgnored() throws IOException {
        Path first = jdk("temurin-25", "25.0.2");
        Path second = jdk("graal-25", "25.0.4");
        settings("""
            {"java.configuration.runtimes": [
              {"name": "JavaSE-25", "path": "%s"}, {"name": "JavaSE-25", "path": "%s"}]}
            """.formatted(first, second));

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).extracting(Jdk::home).containsExactly(first);
        assertThat(chosen.warnings())
                .singleElement()
                .asString()
                .contains("JavaSE-25")
                .contains(second.toString());
    }

    @Test
    void refusesEntriesThatHaveNoNameOrNoPathAndKeepsTheOthers() throws IOException {
        Path home = jdk("temurin-25", "25.0.2");
        settings("""
            {"java.configuration.runtimes": [
              "JavaSE-25", {"name": "JavaSE-25"}, {"path": "/jdk"}, {"name": 25, "path": "/jdk"},
              {"name": "JavaSE-25", "path": "a\\u0000path"},
              {"name": "JavaSE-25", "path": "%s"}]}
            """.formatted(home));

        var chosen = new VsCodeRuntimes().read(root);

        assertThat(chosen.jdks()).extracting(Jdk::home).containsExactly(home);
        assertThat(chosen.warnings()).hasSize(5).allMatch(warning -> warning.contains("java.configuration.runtimes"));
    }

    @Test
    void saysWhenTheSettingIsNotAListOrTheFileIsNotJson() throws IOException {
        settings("{\"java.configuration.runtimes\": \"JavaSE-25\"}");
        assertThat(new VsCodeRuntimes().read(root).warnings())
                .singleElement()
                .asString()
                .contains("not a list");

        settings("not json");
        assertThat(new VsCodeRuntimes().read(root).warnings())
                .singleElement()
                .asString()
                .contains("ignored .vscode/settings.json");

        Files.delete(root.resolve(".vscode/settings.json"));
        Files.createDirectories(root.resolve(".vscode/settings.json"));
        assertThat(new VsCodeRuntimes().read(root).warnings())
                .singleElement()
                .asString()
                .contains("ignored .vscode/settings.json");

        Files.delete(root.resolve(".vscode/settings.json"));
        settings("[1]");
        assertThat(new VsCodeRuntimes().read(root).warnings())
                .singleElement()
                .asString()
                .contains("not a JSON object");
    }
}
