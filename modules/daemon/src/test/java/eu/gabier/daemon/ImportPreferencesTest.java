package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.ImportSettings.Setting;
import eu.gabier.protocol.ImportSettings.Source;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportPreferencesTest {

    private static final String PREFERENCES = ".settings/org.eclipse.jdt.ui.prefs";
    private static final String VSCODE = ".vscode/settings.json";

    @TempDir
    Path project;

    private void write(String file, String text) throws IOException {
        Path path = project.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private ImportPreferences read() {
        return ImportPreferences.read(project);
    }

    @Test
    void leavesTheDefaultsOfJdtlsToAProjectWithNoFileForTheImports() {
        var read = read();

        assertThat(read.settings()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(read.warnings()).isEmpty();
    }

    @Test
    void readsTheThreeSettingsOfTheSettingsOfVsCode() throws IOException {
        write(VSCODE, """
            {
              "java.completion.importOrder": ["#", ""],
              "java.sources.organizeImports.starThreshold": 2147483647,
              "java.sources.organizeImports.staticStarThreshold": 5
            }
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        new Setting<>(List.of("#", ""), Source.VSCODE),
                        new Setting<>(2_147_483_647, Source.VSCODE),
                        new Setting<>(5, Source.VSCODE)));
        assertThat(read.warnings()).isEmpty();
    }

    @Test
    void leavesJdtlsItsDefaultForTheSettingsAFileDoesNotSet() throws IOException {
        write(VSCODE, """
            {"editor.tabSize": 4, "java.sources.organizeImports.starThreshold": 3}
            """);

        assertThat(read().settings())
                .isEqualTo(new ImportSettings(
                        ImportSettings.DEFAULTS.importOrder(),
                        new Setting<>(3, Source.VSCODE),
                        ImportSettings.DEFAULTS.staticStarThreshold()));
    }

    @Test
    void readsTheThreeSettingsOfTheEclipsePreferencesOfTheProject() throws IOException {
        write(PREFERENCES, """
            eclipse.preferences.version=1
            org.eclipse.jdt.ui.importorder=\\#;java;javax;org;com;
            org.eclipse.jdt.ui.ondemandthreshold=2147483647
            org.eclipse.jdt.ui.staticondemandthreshold=7
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        new Setting<>(List.of("#", "java", "javax", "org", "com", ""), Source.SETTINGS),
                        new Setting<>(2_147_483_647, Source.SETTINGS),
                        new Setting<>(7, Source.SETTINGS)));
        assertThat(read.warnings()).isEmpty();
    }

    @Test
    void keepsTheCatchAllGroupThatJdtlsWritesAsATrailingSemicolon() throws IOException {
        write(PREFERENCES, "org.eclipse.jdt.ui.importorder=\\#;\n");

        assertThat(read().settings().importOrder()).isEqualTo(new Setting<>(List.of("#", ""), Source.SETTINGS));
    }

    @Test
    void leavesJdtlsItsDefaultForThePreferencesTheEclipseFileDoesNotSet() throws IOException {
        write(PREFERENCES, "org.eclipse.jdt.ui.ondemandthreshold=4\norg.eclipse.jdt.ui.importorder=\n");

        assertThat(read().settings())
                .isEqualTo(new ImportSettings(
                        ImportSettings.DEFAULTS.importOrder(),
                        new Setting<>(4, Source.SETTINGS),
                        ImportSettings.DEFAULTS.staticStarThreshold()));
    }

    @Test
    void takesEachSettingFromTheFileThatSetsItWhenTheTwoFilesDoNotOverlap() throws IOException {
        write(PREFERENCES, "org.eclipse.jdt.ui.importorder=\\#;java\n");
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 9}
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        new Setting<>(List.of("#", "java"), Source.SETTINGS),
                        new Setting<>(9, Source.VSCODE),
                        ImportSettings.DEFAULTS.staticStarThreshold()));
        assertThat(read.warnings()).isEmpty();
    }

    @Test
    void followsTheEclipsePreferencesAndWarnsWhenTheTwoFilesSayDifferentThings() throws IOException {
        write(PREFERENCES, """
            org.eclipse.jdt.ui.importorder=\\#;java
            org.eclipse.jdt.ui.ondemandthreshold=2147483647
            """);
        write(VSCODE, """
            {
              "java.completion.importOrder": ["java", "#"],
              "java.sources.organizeImports.starThreshold": 2147483647,
              "java.sources.organizeImports.staticStarThreshold": 5
            }
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        new Setting<>(List.of("#", "java"), Source.SETTINGS),
                        new Setting<>(2_147_483_647, Source.SETTINGS),
                        new Setting<>(5, Source.VSCODE)));
        assertThat(read.warnings())
                .containsExactly("java.completion.importOrder is [\"#\", \"java\"] in " + PREFERENCES
                        + " and [\"java\", \"#\"]" + " in " + VSCODE + ": following " + PREFERENCES);
    }

    @Test
    void readsTheSettingsOfVsCodeThatHaveCommentsAndTrailingCommas() throws IOException {
        write(VSCODE, """
            // the conventions of the project
            {
              /* the groups: "#" is the static imports */
              "java.completion.importOrder": [
                "#", // statics first
                "", // then the rest
              ],
              "java.sources.organizeImports.starThreshold": 2147483647, // no star
              "java.sources.organizeImports.staticStarThreshold": 2147483647,
              "files.exclude": {"**/x": true, "http://not/a/comment": "/* nor this */"},
            }
            """);

        var read = read();

        assertThat(read.settings().importOrder()).isEqualTo(new Setting<>(List.of("#", ""), Source.VSCODE));
        assertThat(read.settings().starThreshold()).isEqualTo(new Setting<>(2_147_483_647, Source.VSCODE));
        assertThat(read.settings().staticStarThreshold()).isEqualTo(new Setting<>(2_147_483_647, Source.VSCODE));
        assertThat(read.warnings()).isEmpty();
    }

    @Test
    void ignoresAFileOfVsCodeThatIsNotJsonAndSaysSo() throws IOException {
        write(VSCODE, "{\"java.completion.importOrder\": [\"#\"");

        var read = read();

        assertThat(read.settings()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(read.warnings()).hasSize(1).first().asString().startsWith("ignored " + VSCODE + ": ");
    }

    @Test
    void ignoresAFileOfVsCodeThatIsNotAnObjectAndSaysSo() throws IOException {
        write(VSCODE, "[1, 2]");

        var read = read();

        assertThat(read.settings()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(read.warnings()).containsExactly("ignored " + VSCODE + ": it is not a JSON object");
    }

    @Test
    void ignoresAFileItCannotReadAndSaysSo() throws IOException {
        Files.createDirectories(project.resolve(VSCODE));

        var read = read();

        assertThat(read.settings()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(read.warnings()).hasSize(1).first().asString().startsWith("ignored " + VSCODE + ": ");
    }

    @Test
    void ignoresASettingOfVsCodeOfTheWrongTypeAndKeepsTheOthers() throws IOException {
        write(VSCODE, """
            {
              "java.completion.importOrder": "java",
              "java.sources.organizeImports.starThreshold": "many",
              "java.sources.organizeImports.staticStarThreshold": 6
            }
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        ImportSettings.DEFAULTS.importOrder(),
                        ImportSettings.DEFAULTS.starThreshold(),
                        new Setting<>(6, Source.VSCODE)));
        assertThat(read.warnings())
                .containsExactly(
                        "ignored java.completion.importOrder of " + VSCODE + ": it is not a list of text",
                        "ignored java.sources.organizeImports.starThreshold of " + VSCODE + ": it is not an integer");
    }

    @Test
    void ignoresAnOrderOfVsCodeWhoseGroupsAreNotAllTextAndSaysSo() throws IOException {
        write(VSCODE, """
            {"java.completion.importOrder": ["java", 3]}
            """);

        var read = read();

        assertThat(read.settings().importOrder()).isEqualTo(ImportSettings.DEFAULTS.importOrder());
        assertThat(read.warnings())
                .containsExactly("ignored java.completion.importOrder of " + VSCODE + ": it is not a list of text");
    }

    @Test
    void warnsOfTwoThresholdsThatDifferAndShowsTheNumbers() throws IOException {
        write(PREFERENCES, """
            org.eclipse.jdt.ui.ondemandthreshold=4
            """);
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 7}
            """);

        var read = read();

        assertThat(read.settings().starThreshold()).isEqualTo(new Setting<>(4, Source.SETTINGS));
        assertThat(read.warnings())
                .containsExactly("java.sources.organizeImports.starThreshold is 4 in " + PREFERENCES + " and 7 in "
                        + VSCODE + ": following " + PREFERENCES);
    }

    @Test
    void ignoresAThresholdOfVsCodeThatIsNotAnIntegerOrDoesNotFitOne() throws IOException {
        write(VSCODE, """
            {
              "java.sources.organizeImports.starThreshold": 1.5,
              "java.sources.organizeImports.staticStarThreshold": 99999999999
            }
            """);

        var read = read();

        assertThat(read.settings()).isEqualTo(ImportSettings.DEFAULTS);
        assertThat(read.warnings()).hasSize(2);
    }

    @Test
    void ignoresAnEclipsePreferenceThatIsNotAnIntegerAndSaysSo() throws IOException {
        write(PREFERENCES, """
            org.eclipse.jdt.ui.ondemandthreshold=many
            org.eclipse.jdt.ui.staticondemandthreshold=3
            """);

        var read = read();

        assertThat(read.settings())
                .isEqualTo(new ImportSettings(
                        ImportSettings.DEFAULTS.importOrder(),
                        ImportSettings.DEFAULTS.starThreshold(),
                        new Setting<>(3, Source.SETTINGS)));
        assertThat(read.warnings())
                .containsExactly(
                        "ignored org.eclipse.jdt.ui.ondemandthreshold of " + PREFERENCES + ": it is not an integer");
    }

    @Test
    void ignoresEclipsePreferencesItCannotReadAndSaysSo() throws IOException {
        Files.createDirectories(project.resolve(PREFERENCES));
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 8}
            """);

        var read = read();

        assertThat(read.settings().starThreshold()).isEqualTo(new Setting<>(8, Source.VSCODE));
        assertThat(read.warnings()).hasSize(1).first().asString().startsWith("ignored " + PREFERENCES + ": ");
    }

    @Test
    void readsTheFilesAgainAtEachCall() throws IOException {
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 8}
            """);
        assertThat(read().settings().starThreshold().value()).isEqualTo(8);

        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 9}
            """);
        assertThat(read().settings().starThreshold().value()).isEqualTo(9);

        Files.delete(project.resolve(VSCODE));
        assertThat(read().settings()).isEqualTo(ImportSettings.DEFAULTS);
    }

    @Test
    void saysWhatJdtlsIsToldAsThePreferencesOfItsClientInTheNestedFormOfTheSettings() {
        var settings = new ImportSettings(
                new Setting<>(List.of("#", ""), Source.VSCODE),
                new Setting<>(2_147_483_647, Source.SETTINGS),
                new Setting<>(5, Source.VSCODE));

        assertThat(JdtlsSettings.imports(settings))
                .isEqualTo(Map.of(
                        "java",
                        Map.of(
                                "completion",
                                Map.of("importOrder", List.of("#", "")),
                                "sources",
                                Map.of(
                                        "organizeImports",
                                        Map.of("starThreshold", 2_147_483_647, "staticStarThreshold", 5)))));
    }

    @Test
    void comparesTheValuesOfTheSettingsAndNotTheirSources() {
        var fromTheFile = new ImportSettings(
                new Setting<>(List.of("java", "javax", "org", "com"), Source.VSCODE),
                new Setting<>(99, Source.SETTINGS),
                new Setting<>(99, Source.VSCODE));

        assertThat(ImportPreferences.sameValues(fromTheFile, ImportSettings.DEFAULTS))
                .isTrue();
        assertThat(ImportPreferences.sameValues(
                        new ImportSettings(
                                new Setting<>(List.of("#"), Source.DEFAULT),
                                ImportSettings.DEFAULTS.starThreshold(),
                                ImportSettings.DEFAULTS.staticStarThreshold()),
                        ImportSettings.DEFAULTS))
                .isFalse();
        assertThat(ImportPreferences.sameValues(
                        new ImportSettings(
                                ImportSettings.DEFAULTS.importOrder(),
                                new Setting<>(98, Source.DEFAULT),
                                ImportSettings.DEFAULTS.staticStarThreshold()),
                        ImportSettings.DEFAULTS))
                .isFalse();
        assertThat(ImportPreferences.sameValues(
                        new ImportSettings(
                                ImportSettings.DEFAULTS.importOrder(),
                                ImportSettings.DEFAULTS.starThreshold(),
                                new Setting<>(98, Source.DEFAULT)),
                        ImportSettings.DEFAULTS))
                .isFalse();
    }
}
