package eu.gabier.daemon;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.SourceText;
import eu.gabier.editing.domain.TextEdit;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * The renames and the extractions of {@link JdtlsLanguageServer}: they share the way jdtls gives an edit, the name asked
 * for being put in with a rename. The facade holds the lock.
 */
final class JdtlsRefactorings {

    /** The kinds of the symbols that jdtls lists for a class, an interface or an enum (a record is a class). */
    private static final Set<SymbolKind> TYPES = Set.of(SymbolKind.Class, SymbolKind.Interface, SymbolKind.Enum);

    /** What a name starts with: the label of an anonymous class does not ({@code {...}}). */
    private static final Pattern STARTS_AS_A_NAME = Pattern.compile("^\\p{javaJavaIdentifierStart}");

    private final JdtlsLanguageServer server;

    JdtlsRefactorings(JdtlsLanguageServer server) {
        this.server = server;
    }

    /**
     * Asks jdtls whether the symbol can be renamed ({@code prepareRename}), then for the rename, and gives its edit in
     * the terms of the domain ({@link WorkspaceEdits}). jdtls reads the files as it knows them (nothing is opened), after
     * it has been told of the changes on disk. A
     * symbol that jdtls says it cannot rename is no symbol (jdtls answers so for a blank, a keyword, a position past
     * the text), and a name that jdtls declines is invalid for the symbol. The name in a {@code package} line is the
     * exception: jdtls declines to prepare the rename of a package but renames it (the files move to the folder of the
     * new name, see {@link PackageDeclarations}), and renames nothing when the package named up to the segment of the
     * position has no class of its own (a refusal of its own, which says which package is empty).
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, there is no symbol at the
     *     position, the package at the position holds no class or jdtls declines the name
     * @throws JdtlsException if jdtls dies or does not answer in time
     */
    ChangeSet rename(Path file, Position position, String newName) {
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        String text = JdtlsProject.textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var at = new org.eclipse.lsp4j.Position(position.line(), position.column());
        var service = session.server().getTextDocumentService();
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            var renamable = watch.await(
                    "whether the symbol at " + position + " of " + path + " can be renamed",
                    () -> JdtlsRequests.unlessDeclined(service.prepareRename(new PrepareRenameParams(document, at))));
            // jdtls declines to prepare the rename of a package, which it renames all the same: its name is not a
            // symbol it would answer for, so the position is checked here.
            var packageName = PackageDeclarations.nameAt(text, position);
            if (renamable.isEmpty() && packageName.isEmpty()) {
                throw LanguageServerException.noSuchSymbol(path, position);
            }
            var edit = watch.await(
                    "the rename of the symbol at " + position + " of " + path + " to " + newName,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(document, at, newName))));
            List<FileChange> changes = WorkspaceEdits.of(
                    edit.orElseThrow(() -> LanguageServerException.invalidName(path, position, newName)),
                    JdtlsProject::textOnDisk);
            if (changes.isEmpty()) {
                // jdtls renames the package up to the segment of the position, and answers no change at all when that
                // package holds no class of its own (a prefix of the name that only has sub-packages).
                throw packageName
                        .map(name ->
                                LanguageServerException.emptyPackage(path, position, name.upToPosition(), name.whole()))
                        .orElseGet(() -> LanguageServerException.noSuchSymbol(path, position));
            }
            return new ChangeSet(changes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for the rename of the symbol at " + position + " of " + path,
                    interrupted);
        }
    }

    /**
     * Extracts the expression of the selection into a local variable named as asked. jdtls cannot take the name: it
     * guesses one when it computes the refactoring ({@code java/getRefactorEdit}, command {@code extractVariable}, the
     * refactoring that the code action {@code refactor.extract.variable} stands for) and says where that name is in the
     * text the edit gives, for the client to rename it. So the edit is applied in memory, the text it gives is
     * opened in jdtls (never written to disk) and the variable is renamed with {@code textDocument/rename}, which is
     * what a client does after the edit. The two edits are folded into one on the text as it is on disk
     * ({@link SourceText#replacing}), so that the answer shows the final name only.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    ChangeSet extractVariable(Path file, Range selection, String name) {
        return extract(Extraction.VARIABLE, file, selection, name);
    }

    /**
     * Extracts the expression of the selection into a new constant ({@code private static final}, declared at the top of
     * the class) named as asked, and replaces it by the constant. As for the variable, jdtls proposes a name of its own
     * ({@code java/getRefactorEdit}, command {@code extractConstant}, which needs no client capability and no setting)
     * and says where it is for the client to rename: the constant is renamed with {@code textDocument/rename} on the text
     * the edit gives, and the two edits are folded into one. A selection that jdtls cannot extract is refused in the same way by the four extractions.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    ChangeSet extractConstant(Path file, Range selection, String name) {
        return extract(Extraction.CONSTANT, file, selection, name);
    }

    /**
     * Extracts the expression of the selection into a new field of the class named as asked, and replaces it by the
     * field. As for the variable, jdtls proposes a name of its own ({@code java/getRefactorEdit}, command {@code
     * extractField}, which needs no client capability and no setting) and says where it is for the client to rename: the
     * field is renamed with {@code textDocument/rename} on the text the edit gives, and the two edits are folded into
     * one. Where the field is initialised is left to jdtls: the command takes the place as an optional argument, which the
     * adapter does not send, so jdtls takes its default, the current method.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    ChangeSet extractField(Path file, Range selection, String name) {
        return extract(Extraction.FIELD, file, selection, name);
    }

    /**
     * Extracts the selection (statements, or an expression) into a new method named as asked, and replaces it by the
     * call. As for the variable, jdtls proposes a name of its own ({@code java/getRefactorEdit}, command {@code
     * extractMethod}) and says where it is for the client to rename: the method is renamed with {@code
     * textDocument/rename} on the text the edit gives, and the two edits are folded into one.
     *
     * @throws LanguageServerException if jdtls is not ready, the file is not in its project, jdtls cannot extract the
     *     selection or declines the name
     * @throws JdtlsException if jdtls dies, does not answer in time or answers what the adapter cannot apply
     */
    ChangeSet extractMethod(Path file, Range selection, String name) {
        return extract(Extraction.METHOD, file, selection, name);
    }

    /**
     * The refactorings of {@code java/getRefactorEdit} that introduce a name for the client to rename: the command of
     * the refactoring, how the extraction is called in the messages, and what it introduces.
     */
    private enum Extraction {
        VARIABLE("extractVariable", "the extraction of the expression", "the extracted variable"),
        CONSTANT("extractConstant", "the extraction of the expression into a constant", "the extracted constant"),
        FIELD("extractField", "the extraction of the expression into a field", "the extracted field"),
        METHOD("extractMethod", "the extraction of the selection into a method", "the extracted method");

        private final String command;
        private final String what;
        private final String extracted;

        Extraction(String command, String what, String extracted) {
            this.command = command;
            this.what = what;
            this.extracted = extracted;
        }
    }

    /**
     * The extraction that jdtls offers as a command of {@code java/getRefactorEdit}, with the name asked for. A selection
     * jdtls cannot extract is refused the same way by the four extractions, whatever jdtls answers: nothing at all (what
     * it answers for these commands), a response error {@code InvalidRequest}, or an answer with no edit, with a message
     * or without. The refusal is gabier's text; the message of jdtls, when there is one, is not carried.
     */
    private ChangeSet extract(Extraction extraction, Path file, Range selection, String name) {
        String command = extraction.command;
        String what = extraction.what;
        JdtlsSession session = server.ready();
        Path path = JdtlsProject.normalized(file);
        String original = JdtlsProject.textInProject(session, path);
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var refactoring = new JdtlsServer.RefactorEditParams(
                command,
                new CodeActionParams(document, JdtlsPositions.lsp(selection), new CodeActionContext(List.of())));
        JdtlsWatch watch = session.newWatch();
        try {
            server.prepare(session, watch);
            var proposal = watch.await(
                    what + " at " + selection + " of " + path,
                    () -> JdtlsRequests.unlessDeclined(session.server().getRefactorEdit(refactoring)));
            var refactor = proposal.orElseThrow(() -> LanguageServerException.notExtractable(path, selection));
            var proposed = refactor.edit();
            if (proposed == null) {
                throw LanguageServerException.notExtractable(path, selection);
            }
            String extracted = SourceText.edited(path, original, editsOf(path, proposed, original));
            var introduced = introducedName(extraction, refactor, extracted);
            String named = extracted;
            if (!introduced.text().equals(name)) {
                named = SourceText.edited(
                        path,
                        extracted,
                        renamed(session, watch, extraction, path, extracted, introduced, name, selection));
            }

            return new ChangeSet(
                    List.of(new FileChange.EditFile(path, List.of(SourceText.replacing(original, named)))));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JdtlsException(
                    "Interrupted while waiting for " + what + " at " + selection + " of " + path, interrupted);
        }
    }

    /** The name jdtls introduced, and where it is in the text after the edit. */
    private record Introduced(int offset, String text) {}

    private static Introduced introducedName(
            Extraction extraction, JdtlsServer.RefactorEdit refactor, String extracted) {
        var follow = refactor.command();
        if (follow == null || follow.arguments().isEmpty()) {
            throw new JdtlsException(
                    "jdtls made " + extraction.extracted + " but did not say where its name is: " + refactor);
        }
        var where = follow.arguments().getFirst();

        return new Introduced(where.offset(), extracted.substring(where.offset(), where.offset() + where.length()));
    }

    /**
     * The edits of the one file the extraction is about, as they are given: the edit of jdtls is made of this file only.
     */
    private static List<TextEdit> editsOf(Path path, WorkspaceEdit edit, String text) {
        List<FileChange> changes =
                WorkspaceEdits.of(edit, other -> other.equals(path) ? Optional.of(text) : Optional.empty());

        return changes.stream()
                .map(change -> {
                    if (change instanceof FileChange.EditFile edits
                            && edits.file().equals(path)) {
                        return edits.edits();
                    }
                    throw new JdtlsException(
                            "jdtls answered an extraction that does more than edit " + path + ": " + change);
                })
                .flatMap(List::stream)
                .toList();
    }

    /** Renames what the extraction introduced on the text jdtls has been given, opened for the time of the request. */
    private List<TextEdit> renamed(
            JdtlsSession session,
            JdtlsWatch watch,
            Extraction extraction,
            Path path,
            String text,
            Introduced introduced,
            String name,
            Range selection)
            throws InterruptedException {
        var service = session.server().getTextDocumentService();
        var where = SourceText.positionAt(text, introduced.offset());
        try {
            var publication = server.open(session, Map.of(path, text)).get(path);
            watch.await("jdtls to take in " + extraction.extracted + " in " + path, () -> publication);
            if (extraction == Extraction.METHOD) {
                refuseASignatureInUse(session, watch, path, introduced, JdtlsPositions.lsp(where), name, selection);
            }
            var edit = watch.await(
                    "the rename of " + extraction.extracted + " at " + where + " of " + path + " to " + name,
                    () -> JdtlsRequests.unlessDeclined(service.rename(new RenameParams(
                            new TextDocumentIdentifier(path.toUri().toString()),
                            new org.eclipse.lsp4j.Position(where.line(), where.column()),
                            name))));

            List<TextEdit> edits = editsOf(
                    path,
                    edit.orElseThrow(() -> LanguageServerException.invalidName(path, selection.start(), name)),
                    text);
            if (!leavesTheDeclarationAsItWas(text, SourceText.edited(path, text, edits), introduced, name)) {
                throw LanguageServerException.invalidName(path, selection.start(), name);
            }

            return edits;
        } finally {
            JdtlsLanguageServer.close(session, List.of(path));
        }
    }

    /**
     * Refuses the name of a method that the class already declares with the parameters of the extracted one. jdtls
     * does not decline it: its rename answers an edit that leaves two methods of one signature, which does not
     * compile. The methods are those jdtls lists for the text it was given: a flat list in the order of its outline
     * (the members of a type, then the type), each labelled {@code name(parameters)}, or {@code name(parameters)
     * <type parameters>} for a generic method, with no return type, with the range of its name and the simple name of
     * its parent (empty for an anonymous class). The extracted method is the one whose name holds the place that
     * {@code textDocument/definition} gives for the name jdtls introduced, and the class it is in is the first type
     * after it in the list that has the name of its container: two classes of one simple name are two types.
     */
    private void refuseASignatureInUse(
            JdtlsSession session,
            JdtlsWatch watch,
            Path path,
            Introduced introduced,
            org.eclipse.lsp4j.Position where,
            String name,
            Range selection)
            throws InterruptedException {
        var service = session.server().getTextDocumentService();
        var document = new TextDocumentIdentifier(path.toUri().toString());
        var definition = watch.await(
                "the declaration of the method " + introduced.text() + " in " + path,
                () -> service.definition(new DefinitionParams(document, where)));
        var declaredAt = Optional.ofNullable(definition)
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .flatMap(places -> places.stream().findFirst())
                .map(place -> place.getRange().getStart())
                .orElseThrow(() -> new JdtlsException(
                        "jdtls did not say where the method " + introduced.text() + " is declared in " + path));
        var listed = watch.await(
                "the methods of the extracted text of " + path,
                () -> service.documentSymbol(new DocumentSymbolParams(document)));
        List<SymbolInformation> symbols = Optional.ofNullable(listed).orElse(List.of()).stream()
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .toList();
        var extracted = symbols.stream()
                .filter(symbol -> symbol.getKind() == SymbolKind.Method && nameIsAt(symbol, declaredAt))
                .findFirst()
                .orElseThrow(() -> new JdtlsException("jdtls did not list the method " + introduced.text()
                        + " it extracted in " + path + ": " + symbols));
        String wanted = name + parametersOf(extracted);
        var owner = ownerOf(symbols, extracted);
        if (symbols.stream()
                .filter(symbol -> symbol.getKind() == SymbolKind.Method)
                .anyMatch(method -> signatureOf(method).equals(wanted)
                        && ownerOf(symbols, method).equals(owner))) {
            throw LanguageServerException.invalidName(path, selection.start(), name);
        }
    }

    private static boolean nameIsAt(SymbolInformation symbol, org.eclipse.lsp4j.Position place) {
        var range = symbol.getLocation().getRange();

        return JdtlsPositions.compare(range.getStart(), place) <= 0
                && JdtlsPositions.compare(place, range.getEnd()) <= 0;
    }

    /**
     * The type a member is declared in: the first type listed after it whose name is that of the container jdtls gave
     * (jdtls lists a type after its members). The listing has no other link between a member and its type.
     */
    private static Optional<SymbolInformation> ownerOf(List<SymbolInformation> symbols, SymbolInformation member) {
        String container = Objects.requireNonNullElse(member.getContainerName(), "");

        return symbols.subList(symbols.indexOf(member) + 1, symbols.size()).stream()
                .filter(symbol -> TYPES.contains(symbol.getKind()) && isNamed(symbol, container))
                .findFirst();
    }

    /** Whether the type is the one a container name is of: its label without the type parameters, none for an anonymous one. */
    private static boolean isNamed(SymbolInformation type, String container) {
        String label = type.getName();

        return container.isEmpty()
                ? !STARTS_AS_A_NAME.matcher(label).find()
                : label.replaceFirst("<.*", "").equals(container);
    }

    /** {@code name(parameters)} of a label {@code name(parameters)} or {@code name(parameters) <type parameters>}. */
    private static String signatureOf(SymbolInformation method) {
        String label = method.getName();

        return label.substring(0, label.indexOf(')') + 1);
    }

    private static String parametersOf(SymbolInformation method) {
        String signature = signatureOf(method);

        return signature.substring(signature.indexOf('('));
    }

    /**
     * Whether the rename only changed the name where jdtls introduced it. jdtls does not decline every name it should:
     * for the name of a local variable or parameter in scope it answers an edit all the same, and that edit does not
     * leave the line as it was (the space before {@code =} is lost, the end of the next line is cut). A name that jdtls
     * renames into such an edit is not valid for what was extracted. A rename never adds or removes a line, so the line
     * is looked for by its number, not at the offset: the first place that jdtls introduced the name at is not always
     * the first one the rename changes (a field is declared above the statement that assigns it), which moves the offset.
     */
    private static boolean leavesTheDeclarationAsItWas(
            String before, String after, Introduced introduced, String name) {
        int line = SourceText.positionAt(before, introduced.offset()).line();
        String restBefore =
                restOfTheLine(before, introduced.offset() + introduced.text().length());

        return after.lines().skip(line).findFirst().orElse("").endsWith(name + restBefore);
    }

    private static String restOfTheLine(String text, int from) {
        return text.substring(from).lines().findFirst().orElse("");
    }
}
