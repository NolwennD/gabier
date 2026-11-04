package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.ImportSettings.Setting;
import eu.gabier.protocol.ImportSettings.Source;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImportSettingsTest {

    private static final ImportSettings FROM_THE_FILES = new ImportSettings(
            new Setting<>(List.of("#", ""), Source.VSCODE),
            new Setting<>(2_147_483_647, Source.SETTINGS),
            new Setting<>(99, Source.DEFAULT));

    private static final String FROM_THE_FILES_JSON = """
        {"importOrder":{"value":["#",""],"source":".vscode"},\
        "starThreshold":{"value":2147483647,"source":".settings"},\
        "staticStarThreshold":{"value":99,"source":"default"}}\
        """;

    private static Map<String, Object> setting(Object value, String source) {
        var setting = new HashMap<String, Object>();
        setting.put("value", value);
        setting.put("source", source);

        return setting;
    }

    private static Map<String, Object> result() {
        var result = new HashMap<String, Object>();
        result.put("importOrder", setting(List.of("#", ""), ".vscode"));
        result.put("starThreshold", setting(new BigDecimal("2147483647"), ".settings"));
        result.put("staticStarThreshold", setting(new BigDecimal("99"), "default"));

        return result;
    }

    @Test
    void followsTheDefaultsOfJdtlsWhenNoFileSaysAnythingAndSaysSo() {
        assertThat(ImportSettings.DEFAULTS.importOrder())
                .isEqualTo(new Setting<>(List.of("java", "javax", "org", "com"), Source.DEFAULT));
        assertThat(ImportSettings.DEFAULTS.starThreshold()).isEqualTo(new Setting<>(99, Source.DEFAULT));
        assertThat(ImportSettings.DEFAULTS.staticStarThreshold()).isEqualTo(new Setting<>(99, Source.DEFAULT));
    }

    @Test
    void namesEachSourceByTheFolderOfTheFileItComesFrom() {
        assertThat(List.of(Source.values()).stream().map(Source::text))
                .containsExactly(".settings", ".vscode", "default");
    }

    @Test
    void isWrittenWithTheValueAndTheSourceOfEachSetting() {
        assertThat(FROM_THE_FILES.toJson()).isEqualTo(FROM_THE_FILES_JSON);
        StrictJson.parse(FROM_THE_FILES.toJson());
    }

    @Test
    void isReadFromWhatItWrites() {
        var read = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", FROM_THE_FILES.toJson()))).result();

        assertThat(ImportSettings.of(read)).isEqualTo(FROM_THE_FILES);
    }

    @Test
    void isReadFromTheResult() {
        assertThat(ImportSettings.of(result())).isEqualTo(FROM_THE_FILES);
    }

    @Test
    void isRefusedWhenASettingIsMissingOrOfAnotherShape() {
        var results = new ArrayList<Map<String, Object>>();
        for (String name : List.of("importOrder", "starThreshold", "staticStarThreshold")) {
            var missing = result();
            missing.remove(name);
            results.add(missing);
            var notAnObject = result();
            notAnObject.put(name, "x");
            results.add(notAnObject);
            var unknownSource = result();
            unknownSource.put(name, setting(name.equals("importOrder") ? List.of() : BigDecimal.ONE, "elsewhere"));
            results.add(unknownSource);
            var noValue = result();
            noValue.put(name, setting("x", ".vscode"));
            results.add(noValue);
        }
        var notStrings = result();
        notStrings.put("importOrder", setting(List.of(1), ".vscode"));
        results.add(notStrings);
        var notIntegers = result();
        notIntegers.put("starThreshold", setting(new BigDecimal("1.5"), ".vscode"));
        results.add(notIntegers);
        var tooBig = result();
        tooBig.put("staticStarThreshold", setting(new BigDecimal("2147483648"), ".vscode"));
        results.add(tooBig);

        for (var invalid : results) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> ImportSettings.of(invalid));
        }
    }

    @Test
    void refusesAMissingSetting() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() ->
                        new ImportSettings(null, FROM_THE_FILES.starThreshold(), FROM_THE_FILES.staticStarThreshold()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Setting<>(null, Source.DEFAULT));
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new Setting<>(1, null));
    }

    @Test
    void showsTheSettingsOfJdtlsWithTheirValueAndWhereTheyCameFrom() {
        assertThat(FROM_THE_FILES.lines())
                .containsExactly(
                        "java.completion.importOrder = [\"#\", \"\"] (.vscode)",
                        "java.sources.organizeImports.starThreshold = 2147483647 (.settings)",
                        "java.sources.organizeImports.staticStarThreshold = 99 (default of jdtls)");
    }
}
