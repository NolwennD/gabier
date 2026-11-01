package fr.craft.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The catalogue is data that adapters replay against a real server: it has to be coherent before it is trusted. */
class ScenariosTest {

    private static final List<Scenario> ALL = Scenarios.all();

    private static String lineOf(Scenario scenario, Path file, int line) {
        return scenario.files().get(file).split("\n", -1)[line];
    }

    /** The identifier that starts at the position, or an empty string if there is none. */
    private static String identifierAt(Scenario scenario, Path file, Position position) {
        String line = lineOf(scenario, file, position.line());
        int end = position.column();
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        boolean startsHere =
                position.column() == 0 || !Character.isJavaIdentifierPart(line.charAt(position.column() - 1));

        return startsHere ? line.substring(position.column(), end) : "";
    }

    /** Both ends of the edit are in the text of the file, the end not before the start. */
    private static boolean isInsideItsFile(Scenario scenario, EditFile change, TextEdit edit) {
        return isInside(scenario, change.file(), edit.range().start())
                && isInside(scenario, change.file(), edit.range().end())
                && edit.range().start().compareTo(edit.range().end()) <= 0;
    }

    private static boolean isInside(Scenario scenario, Path file, Position position) {
        String[] lines = scenario.files().get(file).split("\n", -1);

        return position.line() < lines.length && position.column() <= lines[position.line()].length();
    }

    /** The name where it is a whole word: {@code greet} is in {@code greet(} and not in {@code greeter}. */
    private static Pattern wholeWord(String name) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_$])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}_$])");
    }

    /**
     * What each file of the project should hold once the rename is applied: its source with the whole word of the old
     * name replaced by the new name, at the path where the change set moves it (the same path if it does not).
     */
    private static Map<Path, String> expectedAfter(Scenario inside, String oldName, String newName) {
        List<FileChange> changes = ((Answer.Renamed) inside.answer()).changes().changes();
        Map<Path, String> expected = new HashMap<>();

        inside.files().forEach((path, source) -> {
            Path movedTo = changes.stream()
                    .filter(RenameFile.class::isInstance)
                    .map(RenameFile.class::cast)
                    .filter(rename -> rename.from().equals(path))
                    .map(RenameFile::to)
                    .findFirst()
                    .orElse(path);
            expected.put(movedTo, wholeWord(oldName).matcher(source).replaceAll(Matcher.quoteReplacement(newName)));
        });

        return expected;
    }

    private static Map<Path, String> filesUnder(Path root) throws IOException {
        Map<Path, String> files = new HashMap<>();
        try (Stream<Path> written = Files.walk(root)) {
            for (Path file : written.filter(Files::isRegularFile).toList()) {
                files.put(file, Files.readString(file));
            }
        }

        return files;
    }

    private static List<Scenario> answeredWith(Class<? extends Answer> answer) {
        return ALL.stream()
                .filter(scenario -> answer.isInstance(scenario.answer()))
                .toList();
    }

    @Test
    void holdsTheCleanFileTheErrorTheWarningTheFourRenamesAndTheRefusals() {
        assertThat(ALL.stream().map(Scenario::name))
                .containsExactly(
                        "diagnostics-of-a-clean-file",
                        "diagnostics-of-a-file-with-an-error",
                        "diagnostics-of-a-file-with-a-warning",
                        "diagnostics-while-the-server-imports",
                        "diagnostics-of-a-file-outside-the-project",
                        "diagnostics-of-a-file-that-does-not-exist",
                        "diagnostics-of-a-folder",
                        "rename-a-local-variable",
                        "rename-a-field",
                        "rename-a-method",
                        "rename-a-type",
                        "rename-where-there-is-no-symbol",
                        "rename-to-an-invalid-name",
                        "rename-while-the-server-imports",
                        "rename-a-file-outside-the-project",
                        "organize-imports-of-a-file",
                        "organize-imports-of-a-file-in-order",
                        "organize-imports-keeps-explicit-imports",
                        "organize-imports-of-a-type-two-packages-declare",
                        "organize-imports-while-the-server-imports",
                        "organize-imports-of-a-file-outside-the-project",
                        "extract-a-variable",
                        "extract-a-variable-from-something-that-is-not-an-expression",
                        "extract-a-variable-from-a-statement",
                        "extract-a-variable-from-a-void-call",
                        "extract-a-variable-to-the-name-jdtls-proposes",
                        "extract-a-variable-to-a-name-in-use",
                        "extract-a-variable-to-an-invalid-name",
                        "extract-a-variable-while-the-server-imports",
                        "extract-a-variable-of-a-file-outside-the-project",
                        "extract-a-method-that-uses-a-local-variable",
                        "extract-a-method-that-assigns-a-local-variable",
                        "extract-a-method-from-an-expression",
                        "extract-a-method-from-half-a-statement",
                        "extract-a-method-from-a-branch-that-returns",
                        "extract-a-method-to-an-invalid-name",
                        "extract-a-method-to-a-name-in-use",
                        "extract-a-method-while-the-server-imports",
                        "extract-a-method-of-a-file-outside-the-project",
                        "move-a-static-nested-type",
                        "move-an-inner-type-that-does-not-use-the-outer-instance",
                        "move-an-inner-type-that-uses-the-outer-instance",
                        "move-the-type-that-encloses-a-position-in-its-method",
                        "move-a-type-from-a-position-outside-any-type",
                        "move-a-top-level-type",
                        "move-a-type-to-a-file-that-exists",
                        "move-a-type-while-the-server-imports",
                        "move-a-type-of-a-file-outside-the-project");
    }

    @Test
    void refusesForEachReasonOfThePortOnAnOrganizeImports() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.OrganizeImports)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(
                        LanguageServerErrorKey.NOT_READY,
                        LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                        LanguageServerErrorKey.AMBIGUOUS_IMPORTS);
    }

    @Test
    void refusesForEachReasonOfThePortOnARename() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.Rename)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(
                        LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                        LanguageServerErrorKey.INVALID_NAME,
                        LanguageServerErrorKey.NO_SUCH_SYMBOL,
                        LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void refusesForEachReasonOfThePortOnAnExtractVariable() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.ExtractVariable)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(
                        LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                        LanguageServerErrorKey.INVALID_NAME,
                        LanguageServerErrorKey.INVALID_NAME,
                        LanguageServerErrorKey.NOT_AN_EXPRESSION,
                        LanguageServerErrorKey.NOT_AN_EXPRESSION,
                        LanguageServerErrorKey.NOT_AN_EXPRESSION,
                        LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void refusesForEachReasonOfThePortOnAnExtractMethod() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.ExtractMethod)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(
                        LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                        LanguageServerErrorKey.INVALID_NAME,
                        LanguageServerErrorKey.INVALID_NAME,
                        LanguageServerErrorKey.NOT_EXTRACTABLE,
                        LanguageServerErrorKey.NOT_EXTRACTABLE,
                        LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void refusesForEachReasonOfThePortOnAMoveOfAType() {
        assertThat(answeredWith(Answer.Refused.class).stream()
                        .filter(scenario -> scenario.request() instanceof Request.MoveTypeToFile)
                        .map(scenario -> ((Answer.Refused) scenario.answer()).key()))
                .containsExactlyInAnyOrder(
                        LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                        LanguageServerErrorKey.NOT_MOVABLE,
                        LanguageServerErrorKey.NOT_MOVABLE,
                        LanguageServerErrorKey.NOT_MOVABLE,
                        LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void holdsOnlyRelativeJavaSourcesAndRelativeRequests() {
        for (Scenario scenario : ALL) {
            assertThat(scenario.files().keySet())
                    .as(scenario.name())
                    .isNotEmpty()
                    .allSatisfy(file -> {
                        assertThat(file.isAbsolute()).isFalse();
                        assertThat(file.toString()).startsWith("src/main/java/").endsWith(".java");
                    });
            assertThat(scenario.request().file().isAbsolute())
                    .as(scenario.name())
                    .isFalse();
        }
    }

    @Test
    void asksAboutAFileOfTheProjectExceptWhenTheFileIsOutsideItMissingOrAFolder() {
        for (Scenario scenario : ALL) {
            Path file = scenario.request().file();
            Optional<LanguageServerErrorKey> refusal =
                    scenario.answer() instanceof Answer.Refused refused ? Optional.of(refused.key()) : Optional.empty();

            if (refusal.equals(Optional.of(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT))) {
                assertThat(file.normalize().startsWith(".."))
                        .as(scenario.name())
                        .isTrue();
            } else if (refusal.equals(Optional.of(LanguageServerErrorKey.FILE_MISSING))) {
                assertThat(file.normalize().startsWith(".."))
                        .as(scenario.name())
                        .isFalse();
                assertThat(scenario.files()).as(scenario.name()).doesNotContainKey(file);
            } else if (refusal.equals(Optional.of(LanguageServerErrorKey.NOT_A_FILE))) {
                assertThat(scenario.files().keySet())
                        .as(scenario.name())
                        .anyMatch(source -> source.startsWith(file) && !source.equals(file));
            } else {
                assertThat(scenario.files()).as(scenario.name()).containsKey(file);
            }
        }
    }

    @Test
    void keepsEveryDiagnosticInsideItsFile() {
        for (Scenario scenario : answeredWith(Answer.Diagnosed.class)) {
            Path file = scenario.request().file();
            List<Diagnostic> diagnostics = ((Answer.Diagnosed) scenario.answer()).diagnostics();

            for (Diagnostic diagnostic : diagnostics) {
                String line = lineOf(scenario, file, diagnostic.position().line());
                assertThat(diagnostic.position().column()).as(scenario.name()).isLessThan(line.length());
            }
        }
    }

    @Test
    void showsACleanFileAndAFileWithOneError() {
        Scenario clean = ALL.get(0);
        Scenario broken = ALL.get(1);

        assertThat(((Answer.Diagnosed) clean.answer()).diagnostics()).isEmpty();
        assertThat(((Answer.Diagnosed) broken.answer()).diagnostics())
                .extracting(Diagnostic::severity)
                .containsExactly(Diagnostic.Severity.ERROR);
    }

    @Test
    void showsAFileWithOnlyAWarningAtTheNameOfTheVariable() {
        Scenario sloppy = ALL.get(2);
        Diagnostic warning = ((Answer.Diagnosed) sloppy.answer()).diagnostics().getFirst();

        assertThat(((Answer.Diagnosed) sloppy.answer()).diagnostics()).hasSize(1);
        assertThat(warning.severity()).isEqualTo(Diagnostic.Severity.WARNING);
        assertThat(identifierAt(sloppy, sloppy.request().file(), warning.position()))
                .isEqualTo("unused");
    }

    /**
     * What a rename answer holds, whatever the server cuts its edits like (jdtls gives one edit per file, from the
     * first occurrence to the last, repeating the code between them): it asks for a symbol, each edit of a file is in
     * the file, starts on that symbol and has the new name as its text start. What the answer does is the business of
     * the next test.
     */
    @Test
    void startsEveryRenameOnTheSymbolAndGivesEditsThatStartWhereItIsAndFitTheirFile() {
        for (Scenario scenario : answeredWith(Answer.Renamed.class)) {
            Request.Rename request = (Request.Rename) scenario.request();
            String symbol = identifierAt(scenario, request.file(), request.position());
            List<FileChange> changes =
                    ((Answer.Renamed) scenario.answer()).changes().changes();

            assertThat(symbol).as(scenario.name()).isNotEmpty().isNotEqualTo(request.newName());
            changes.stream()
                    .filter(EditFile.class::isInstance)
                    .map(EditFile.class::cast)
                    .forEach(change -> change.edits().forEach(edit -> {
                        assertThat(isInsideItsFile(scenario, change, edit))
                                .as(scenario.name())
                                .isTrue();
                        assertThat(identifierAt(
                                        scenario, change.file(), edit.range().start()))
                                .as(scenario.name())
                                .isEqualTo(symbol);
                        assertThat(edit.newText()).as(scenario.name()).startsWith(request.newName());
                    }));
        }
    }

    @Test
    void startsTheRefusedRenamesWhereTheirReasonIs() {
        Scenario noSymbol = ALL.stream()
                .filter(scenario -> scenario.name().equals("rename-where-there-is-no-symbol"))
                .findFirst()
                .orElseThrow();
        Scenario invalidName = ALL.stream()
                .filter(scenario -> scenario.name().equals("rename-to-an-invalid-name"))
                .findFirst()
                .orElseThrow();
        Request.Rename onNothing = (Request.Rename) noSymbol.request();
        Request.Rename onASymbol = (Request.Rename) invalidName.request();

        assertThat(identifierAt(noSymbol, onNothing.file(), onNothing.position()))
                .isEmpty();
        assertThat(identifierAt(invalidName, onASymbol.file(), onASymbol.position()))
                .isNotEmpty();
        assertThat(Character.isJavaIdentifierStart(onASymbol.newName().charAt(0)))
                .isFalse();
    }

    @Test
    void leavesNoWholeWordOfTheOldNameOnceTheRenameIsApplied(@TempDir Path directory) throws IOException {
        for (Scenario scenario : answeredWith(Answer.Renamed.class)) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            Request.Rename request = (Request.Rename) scenario.request();
            String oldName = identifierAt(scenario, request.file(), request.position());
            Pattern oldWord = wholeWord(oldName);

            new ChangeApplier(new NioFileStore()).apply(((Answer.Renamed) inside.answer()).changes());

            try (Stream<Path> written = Files.walk(root)) {
                List<Path> sources = written.filter(Files::isRegularFile).toList();
                assertThat(sources).as(scenario.name()).isNotEmpty();
                for (Path source : sources) {
                    assertThat(oldWord.matcher(Files.readString(source)).find())
                            .as(scenario.name() + " in " + source)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void turnsEveryFileIntoItsSourceWithTheWholeWordOfTheOldNameReplacedByTheNewOne(@TempDir Path directory)
            throws IOException {
        for (Scenario scenario : answeredWith(Answer.Renamed.class)) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            Request.Rename request = (Request.Rename) scenario.request();
            String oldName = identifierAt(scenario, request.file(), request.position());

            new ChangeApplier(new NioFileStore()).apply(((Answer.Renamed) inside.answer()).changes());

            assertThat(filesUnder(root))
                    .as(scenario.name())
                    .isEqualTo(expectedAfter(inside, oldName, request.newName()));
        }
    }

    @Test
    void namesEveryScenarioOnce() {
        assertThat(new HashSet<>(ALL.stream().map(Scenario::name).toList())).hasSameSizeAs(ALL);
    }

    /** The import lines of a source, from the first to the last, blank lines between them included. */
    private static List<String> importBlock(String source) {
        List<String> lines = List.of(source.split("\n", -1));
        int first = IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).startsWith("import "))
                .findFirst()
                .orElseThrow();
        int last = IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).startsWith("import "))
                .max()
                .orElseThrow();

        return lines.subList(first, last + 1);
    }

    /**
     * The block the project keeps (open-java-format, Checkstyle {@code AvoidStarImport}): the static imports in ASCII
     * order, a blank line, then all the others in ASCII order, with no group of its own for {@code java} or {@code org}.
     */
    private static List<String> inTheOrderOfTheProject(List<String> block) {
        List<String> statics = block.stream()
                .filter(line -> line.startsWith("import static "))
                .sorted()
                .toList();
        List<String> others = block.stream()
                .filter(line -> line.startsWith("import ") && !line.startsWith("import static "))
                .sorted()
                .toList();
        List<String> ordered = new ArrayList<>(statics);
        if (!statics.isEmpty() && !others.isEmpty()) {
            ordered.add("");
        }
        ordered.addAll(others);

        return ordered;
    }

    @Test
    void leavesTheImportsOfAnOrganizedFileInTheOrderOfTheProjectWithoutAnyStar(@TempDir Path directory)
            throws IOException {
        for (Scenario scenario : answeredWith(Answer.Organized.class)) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);

            new ChangeApplier(new NioFileStore()).apply(((Answer.Organized) inside.answer()).changes());

            List<String> block = importBlock(Files.readString(inside.request().file()));
            assertThat(block).as(scenario.name()).isEqualTo(inTheOrderOfTheProject(block));
            assertThat(block).as(scenario.name()).noneMatch(line -> line.endsWith(".*;"));
        }
    }

    @Test
    void declaresTheNameAskedForAndUsesItInPlaceOfTheSelectedExpression(@TempDir Path directory) throws IOException {
        for (Scenario scenario : answeredWith(Answer.Extracted.class)) {
            if (!(scenario.request() instanceof Request.ExtractVariable)) {
                continue;
            }
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            var request = (Request.ExtractVariable) inside.request();
            String expression = selected(Files.readString(request.file()), request.selection());

            new ChangeApplier(new NioFileStore()).apply(((Answer.Extracted) inside.answer()).changes());

            assertThat(Files.readString(request.file()))
                    .as(scenario.name())
                    .contains(" " + request.name() + " = " + expression + ";")
                    .contains(" = " + request.name() + ";");
        }
    }

    @Test
    void declaresTheMethodWithTheNameAskedForAndCallsItWhereTheSelectionWas(@TempDir Path directory)
            throws IOException {
        List<Scenario> extractions = answeredWith(Answer.Extracted.class).stream()
                .filter(scenario -> scenario.request() instanceof Request.ExtractMethod)
                .toList();
        assertThat(extractions).hasSize(3);
        for (Scenario scenario : extractions) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            var request = (Request.ExtractMethod) inside.request();

            new ChangeApplier(new NioFileStore()).apply(((Answer.Extracted) inside.answer()).changes());

            assertThat(Files.readString(request.file()))
                    .as(scenario.name())
                    .containsPattern("\\b" + request.name() + "\\([a-z]*\\);")
                    .containsPattern("private \\w+(\\[\\])? " + request.name() + "\\(");
        }
    }

    @Test
    void createsTheFileOfTheMovedTypeInItsPackageAndTakesTheTypeOutOfItsSource(@TempDir Path directory)
            throws IOException {
        List<Scenario> moves = answeredWith(Answer.Moved.class);
        assertThat(moves).hasSize(4);
        for (Scenario scenario : moves) {
            Path root = Files.createTempDirectory(directory, "project");
            Scenario inside = scenario.inside(root);
            inside.writeProject(root);
            var request = (Request.MoveTypeToFile) inside.request();
            Path created = ((FileChange.CreateFile) ((Answer.Moved) inside.answer())
                            .changes().changes().stream()
                                    .filter(FileChange.CreateFile.class::isInstance)
                                    .findFirst()
                                    .orElseThrow())
                    .file();
            String type = created.getFileName().toString().replace(".java", "");

            new ChangeApplier(new NioFileStore()).apply(((Answer.Moved) inside.answer()).changes());

            assertThat(created.getParent())
                    .as(scenario.name())
                    .isEqualTo(request.file().getParent());
            assertThat(Files.readString(created))
                    .as(scenario.name())
                    .startsWith("package demo;\n\npublic class " + type + " {")
                    .endsWith("}");
            assertThat(Files.readString(request.file())).as(scenario.name()).doesNotContain("class " + type + " {");
        }
    }

    @Test
    void startsEveryMoveInTheTypeItMoves() {
        for (Scenario scenario : answeredWith(Answer.Moved.class)) {
            var request = (Request.MoveTypeToFile) scenario.request();
            String type = ((FileChange.CreateFile) ((Answer.Moved) scenario.answer())
                            .changes().changes().stream()
                                    .filter(FileChange.CreateFile.class::isInstance)
                                    .findFirst()
                                    .orElseThrow())
                    .file()
                    .getFileName()
                    .toString()
                    .replace(".java", "");
            String[] lines = scenario.files().get(request.file()).split("\n", -1);
            int declaration = IntStream.range(0, lines.length)
                    .filter(line -> lines[line].matches(".* class " + type + " \\{"))
                    .findFirst()
                    .orElseThrow();
            int end = IntStream.range(declaration, lines.length)
                    .filter(line ->
                            lines[line].equals(lines[declaration].substring(0, lines[declaration].indexOf('p')) + "}"))
                    .findFirst()
                    .orElseThrow();

            assertThat(request.position().line()).as(scenario.name()).isBetween(declaration, end);
        }
    }

    /** The text of a range inside one line. */
    private static String selected(String text, Range range) {
        String line = text.lines().toList().get(range.start().line());

        return line.substring(range.start().column(), range.end().column());
    }

    @Test
    void startsTheFilesThatNeedNothingInTheOrderOfTheProjectWithoutAnyStar() {
        for (Scenario scenario : answeredWith(Answer.AlreadyOrganized.class)) {
            List<String> block =
                    importBlock(scenario.files().get(scenario.request().file()));

            assertThat(block).as(scenario.name()).isNotEmpty().isEqualTo(inTheOrderOfTheProject(block));
            assertThat(block).as(scenario.name()).noneMatch(line -> line.endsWith(".*;"));
        }
    }

    @Test
    void changesTheImportsOfTheUntidyFileThatTheOrganizeScenarioAsksAbout() {
        Scenario untidy = Scenarios.ORGANIZE_IMPORTS;
        List<String> before = importBlock(untidy.files().get(untidy.request().file()));

        assertThat(before).isNotEqualTo(inTheOrderOfTheProject(before));
    }

    @Test
    void importsMoreThanTheDefaultThresholdOfOnePackageAndOfOneTypeInTheFileThatMustStayExplicit() {
        Scenario wide = Scenarios.ORGANIZE_IMPORTS_KEEPS_EXPLICIT_IMPORTS;
        List<String> block = importBlock(wide.files().get(wide.request().file()));

        // jdtls collapses 99 imports of one package into a star by default
        // (java.sources.organizeImports.starThreshold).
        assertThat(block.stream().filter(line -> line.startsWith("import demo.many.")))
                .hasSizeGreaterThan(99);
        assertThat(block.stream().filter(line -> line.startsWith("import static demo.many.Constants.")))
                .hasSizeGreaterThan(99);
    }
}
