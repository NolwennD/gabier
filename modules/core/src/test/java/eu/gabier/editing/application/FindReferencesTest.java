package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Reference;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindReferencesTest {

    private static final String LEDGER = "src/main/java/demo/Ledger.java";
    private static final String BANK = "src/main/java/demo/app/Bank.java";
    private static final String LEDGER_TEST = "src/test/java/demo/LedgerTest.java";

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private FindReferences find;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        find = new FindReferences(server, new NioFileStore());
    }

    private Path path(String relative) {
        return root.resolve(relative);
    }

    /** Every path under the root, with the text of its files: what "nothing was written" means. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                snapshot.put(
                        root.relativize(path).toString(),
                        Files.isDirectory(path) ? "<directory>" : Files.readString(path));
            }
        }

        return snapshot;
    }

    @Test
    void listsWhereATypeIsUsedWithTheLineOfSourceInTheOrderOfTheFilesThenOfThePositions() throws IOException {
        project(Scenarios.REFERENCES_OF_A_TYPE);

        SymbolReferences found = find.find(path(LEDGER), new Position(2, 13), false);

        assertThat(found.declarations()).isEmpty();
        assertThat(found.references())
                .containsExactly(
                        new Reference(path(BANK), new Position(2, 7), "import demo.Ledger;"),
                        new Reference(path(BANK), new Position(6, 8), "        Ledger ledger = new Ledger();"),
                        new Reference(path(BANK), new Position(6, 28), "        Ledger ledger = new Ledger();"),
                        new Reference(path(LEDGER_TEST), new Position(4, 8), "        Ledger ledger = new Ledger();"),
                        new Reference(path(LEDGER_TEST), new Position(4, 28), "        Ledger ledger = new Ledger();"));
    }

    @Test
    void listsTheDeclarationApartFromTheUsesWhenItIsAskedFor() throws IOException {
        project(Scenarios.REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION);

        SymbolReferences found = find.find(path(BANK), new Position(7, 15), true);

        assertThat(found.declarations())
                .containsExactly(
                        new Reference(path(LEDGER), new Position(9, 16), "    public void deposit(int amount) {"));
        assertThat(found.references())
                .containsExactly(
                        new Reference(path(BANK), new Position(7, 15), "        ledger.deposit(100);"),
                        new Reference(path(LEDGER_TEST), new Position(5, 15), "        ledger.deposit(5);"),
                        new Reference(path(LEDGER_TEST), new Position(6, 15), "        ledger.deposit(7);"));
    }

    @Test
    void listsNothingForASymbolNothingUses() throws IOException {
        project(Scenarios.REFERENCES_OF_A_SYMBOL_NOTHING_USES);

        SymbolReferences found = find.find(path(LEDGER), new Position(18, 8), false);

        assertThat(found.declarations()).isEmpty();
        assertThat(found.references()).isEmpty();
    }

    @Test
    void writesNothing() throws IOException {
        project(Scenarios.REFERENCES_OF_A_TYPE);
        Map<String, String> before = snapshot();

        find.find(path(LEDGER), new Position(2, 13), false);

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesWhereThereIsNoSymbolAsTheServerDoes() throws IOException {
        project(Scenarios.REFERENCES_WHERE_THERE_IS_NO_SYMBOL, Scenarios.REFERENCES_FROM_A_COMMENT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> find.find(path(LEDGER), new Position(4, 0), false))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> find.find(path(LEDGER), new Position(10, 40), false))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
    }

    @Test
    void refusesATypeThatDoesNotResolveAsTheServerDoes() throws IOException {
        project(Scenarios.REFERENCES_OF_A_TYPE_THAT_DOES_NOT_RESOLVE);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> find.find(path("src/main/java/demo/Unresolved.java"), new Position(4, 35), false))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NO_SUCH_SYMBOL));
    }

    @Test
    void refusesAFileOutsideTheProject() throws IOException {
        project(Scenarios.REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> find.find(path("../outside/Elsewhere.java"), new Position(0, 0), false))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
    }

    @Test
    void refusesWhileTheServerIsStillImportingTheProject() throws IOException {
        project(Scenarios.REFERENCES_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> find.find(path(LEDGER), new Position(2, 13), false))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
    }

    @Test
    void refusesALocationOfAFileThatChangedSinceTheServerAnswered() throws IOException {
        project(Scenarios.REFERENCES_OF_A_TYPE);
        Files.writeString(path(BANK), "package demo.app;\n");

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> find.find(path(LEDGER), new Position(2, 13), false))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT));
    }

    @Test
    void requiresTheServerTheStoreTheFileAndThePosition() throws IOException {
        project(Scenarios.REFERENCES_OF_A_TYPE);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FindReferences(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new FindReferences(server, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> find.find(null, new Position(2, 13), false))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> find.find(path(LEDGER), null, false))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }

    @Test
    void symbolReferencesRejectAMissingList() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new SymbolReferences(null, List.of()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("declarations"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new SymbolReferences(List.of(), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("references"));
    }
}
