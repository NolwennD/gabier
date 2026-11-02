package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.SourceText;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the name check of {@code extract-method} relies on, asked of a real jdtls the way the adapter asks it for the
 * scenario {@link Scenarios#EXTRACT_METHOD_TO_THE_NAME_OF_AN_OVERLOAD} (the replay compares the answer, which would
 * be the same if the labels or the positions were not what the check reads): the name that {@code java/getRefactorEdit}
 * introduces is at the call, {@code textDocument/definition} of it is the declaration of the new method, and the
 * labels that {@code textDocument/documentSymbol} gives are {@code name(parameters)}, with no return type, under
 * the simple name of the class. Not run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class ExtractedMethodSymbolsTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @TempDir
    Path directory;

    @Test
    void jdtlsLabelsTheMethodsOfTheExtractedTextWithTheirParametersOnlyAndDeclaresTheNewOneWhereItsDefinitionIs()
            throws Exception {
        Path root = directory.toRealPath();
        var scenario = Scenarios.EXTRACT_METHOD_TO_THE_NAME_OF_AN_OVERLOAD;
        scenario.writeProject(root);
        EclipseProject.describe(root);
        var request = (Request.ExtractMethod) scenario.inside(root).request();
        Path file = request.file();
        String original = Files.readString(file);
        var published = new CompletableFuture<Void>();
        try (JdtlsSession session = JdtlsSession.start(
                RealJdtls.launcher(),
                JdtlsSettings.of(root, List.of(root), TIMEOUT),
                System.out::println,
                publication -> {
                    if (publication.getUri().equals(file.toUri().toString())) {
                        published.complete(null);
                    }
                })) {
            var document = new TextDocumentIdentifier(file.toUri().toString());
            var range = new org.eclipse.lsp4j.Range(
                    new org.eclipse.lsp4j.Position(
                            request.selection().start().line(),
                            request.selection().start().column()),
                    new org.eclipse.lsp4j.Position(
                            request.selection().end().line(),
                            request.selection().end().column()));
            var refactor = session.server()
                    .getRefactorEdit(new JdtlsServer.RefactorEditParams(
                            "extractMethod", new CodeActionParams(document, range, new CodeActionContext(List.of()))))
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            var changes = WorkspaceEdits.of(
                    refactor.edit(), other -> other.equals(file) ? Optional.of(original) : Optional.empty());
            String extracted = SourceText.edited(file, original, ((FileChange.EditFile) changes.getFirst()).edits());
            var introduced = refactor.command().arguments().getFirst();
            String name = extracted.substring(introduced.offset(), introduced.offset() + introduced.length());
            var atTheName = SourceText.positionAt(extracted, introduced.offset());

            var service = session.server().getTextDocumentService();
            service.didOpen(new DidOpenTextDocumentParams(
                    new TextDocumentItem(file.toUri().toString(), "java", 1, extracted)));
            published.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            var declaredAt = service.definition(new DefinitionParams(
                            document, new org.eclipse.lsp4j.Position(atTheName.line(), atTheName.column())))
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            var listed = service.documentSymbol(new DocumentSymbolParams(document))
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(extracted.substring(introduced.offset()))
                    .as("the name jdtls introduces is the one of the call")
                    .startsWith(name + "(values);");
            String declaration = "private int " + name + "(int[] values)";
            var declarationAt =
                    SourceText.positionAt(extracted, extracted.indexOf(declaration) + "private int ".length());
            assertThat(declaredAt.isLeft())
                    .as("the definition is a list of locations")
                    .isTrue();
            assertThat(declaredAt.getLeft())
                    .as("the definition of the name at the call")
                    .hasSize(1)
                    .first()
                    .satisfies(location -> {
                        assertThat(location.getRange().getStart().getLine()).isEqualTo(declarationAt.line());
                        assertThat(location.getRange().getStart().getCharacter())
                                .isEqualTo(declarationAt.column());
                        assertThat(location.getRange().getStart()).isNotEqualTo(atTheName);
                    });
            assertThat(listed).as("a flat list").allMatch(Either::isLeft);
            List<SymbolInformation> symbols =
                    listed.stream().map(Either::getLeft).toList();
            assertThat(symbols.stream().filter(symbol -> symbol.getKind() == SymbolKind.Method))
                    .extracting(SymbolInformation::getName)
                    .as("the labels of the methods: name(parameters), no return type")
                    .containsExactlyInAnyOrder(
                            "print(int[])",
                            "total(int[], int)",
                            name + "(int[])",
                            "describe(int)",
                            "doubled(int)",
                            "quadrupled(int)");
            assertThat(symbols.stream().filter(symbol -> symbol.getKind() == SymbolKind.Method))
                    .extracting(SymbolInformation::getContainerName)
                    .as("the container of a method is the simple name of its class")
                    .containsOnly("Totals");
            assertThat(symbols.stream()
                            .filter(symbol -> symbol.getKind() == SymbolKind.Method
                                    && symbol.getName().equals(name + "(int[])")))
                    .extracting(symbol -> symbol.getLocation().getRange().getStart())
                    .as("the name range of the new method starts where its definition says")
                    .containsExactly(declaredAt.getLeft().getFirst().getRange().getStart());
        }
    }
}
