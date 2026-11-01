package fr.craft.gabier.editing.fixtures;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Location;
import fr.craft.gabier.editing.domain.NotMovableReason;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.ReferenceLocations;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a language server is expected to answer to one request on a small project: the contract between the fake and
 * the real server. It is data, written once (see {@link Scenarios}) and used in two places: the fake is scripted with
 * it in {@code check}, and the integration tests of an adapter replay it against a real server and fail if the server
 * answers differently. A scripted answer that no replay checks is a guess.
 *
 * <p>To replay a scenario: create a project in a fresh directory ({@link #writeProject(Path)}, plus whatever build
 * files the server needs to import it, which are the harness's business and not part of the scenario), wait for the
 * server to be ready, take {@link #inside(Path) inside(directory)}, send its {@link #request()} to the adapter and
 * compare the result with its {@link #answer()}: the answer, or the refusal with this key.
 *
 * @param name what the scenario shows, in kebab-case
 * @param files the files of the project, as plain text: paths relative to the root of the project, which is where the
 *     server is started
 * @param request what is asked, with paths relative to the root
 * @param answer what the server is expected to answer, in domain types, with paths relative to the root
 */
public record Scenario(String name, Map<Path, String> files, Request request, Answer answer) {

    public Scenario {
        Assert.notBlank("name", name);
        Assert.notNull("files", files);
        Assert.notNull("request", request);
        Assert.notNull("answer", answer);
        files = Map.copyOf(files);
        check(request, answer);
    }

    /** The same scenario with every path put under the root and normalized, the project files included. */
    public Scenario inside(Path root) {
        Map<Path, String> resolved = files.entrySet().stream()
                .collect(Collectors.toMap(entry -> resolve(root, entry.getKey()), Map.Entry::getValue));

        return new Scenario(name, resolved, request.resolvedIn(root), answer.resolvedIn(root));
    }

    /** Writes the files of the project under the directory, creating the folders they need. */
    public void writeProject(Path directory) throws IOException {
        for (Map.Entry<Path, String> file : files.entrySet()) {
            Path path = directory.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }

    private static void check(Request request, Answer answer) {
        switch (answer) {
            case Answer.Diagnosed _ -> require(request instanceof Request.Diagnostics, "diagnosed", request);
            case Answer.Renamed _ -> require(request instanceof Request.Rename, "renamed", request);
            case Answer.Extracted _ ->
                require(
                        request instanceof Request.ExtractVariable || request instanceof Request.ExtractMethod,
                        "extracted",
                        request);
            case Answer.Moved _ -> require(request instanceof Request.MoveTypeToFile, "moved", request);
            case Answer.MovedToPackage _ ->
                require(request instanceof Request.MoveToPackage, "moved to a package", request);
            case Answer.Inlined _ -> require(request instanceof Request.Inline, "inlined", request);
            case Answer.Referenced _ -> require(request instanceof Request.References, "referenced", request);
            case Answer.Created _ -> require(request instanceof Request.CreateType, "created", request);
            case Answer.Organized _ -> require(request instanceof Request.OrganizeImports, "organized", request);
            case Answer.AlreadyOrganized _ ->
                require(request instanceof Request.OrganizeImports, "already organized", request);
            case Answer.Refused refused -> request.refusal(refused.key(), refused.details());
        }
    }

    private static void require(boolean fits, String answer, Request request) {
        if (!fits) {
            throw new IllegalArgumentException("A " + answer + " answer does not fit the request " + request);
        }
    }

    private static Path resolve(Path root, Path path) {
        return root.resolve(path).normalize();
    }

    /** What is asked of the server. */
    public sealed interface Request {

        Path file();

        /** The same request with its file put under the root and normalized. */
        Request resolvedIn(Path root);

        /**
         * The exception that a refusal with this key is for this request.
         *
         * @throws IllegalArgumentException if the request cannot be refused for this reason
         */
        default LanguageServerException refusal(LanguageServerErrorKey key) {
            return refusal(key, List.of());
        }

        /**
         * The exception that a refusal with this key and these details is for this request.
         *
         * @param details what the refusal names: the imports the server cannot resolve, or the types it cannot choose
         *     between
         * @throws IllegalArgumentException if the request cannot be refused for this reason
         */
        LanguageServerException refusal(LanguageServerErrorKey key, List<String> details);

        /** The diagnostics of a file. */
        record Diagnostics(Path file) implements Request {

            public Diagnostics {
                Assert.notNull("file", file);
            }

            @Override
            public Diagnostics resolvedIn(Path root) {
                return new Diagnostics(resolve(root, file));
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A request for diagnostics cannot get " + key);
                };
            }
        }

        /** The organization of the imports of a file. */
        record OrganizeImports(Path file) implements Request {

            public OrganizeImports {
                Assert.notNull("file", file);
            }

            @Override
            public OrganizeImports resolvedIn(Path root) {
                return new OrganizeImports(resolve(root, file));
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case AMBIGUOUS_IMPORTS -> LanguageServerException.ambiguousImports(file, details);
                    case UNRESOLVED_IMPORTS -> LanguageServerException.unresolvedImports(file, details);
                    case NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A request to organize imports cannot get " + key);
                };
            }
        }

        /** The rename of the symbol at a position. */
        record Rename(Path file, Position position, String newName) implements Request {

            public Rename {
                Assert.notNull("file", file);
                Assert.notNull("position", position);
                Assert.notNull("newName", newName);
            }

            @Override
            public Rename resolvedIn(Path root) {
                return new Rename(resolve(root, file), position, newName);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NO_SUCH_SYMBOL -> LanguageServerException.noSuchSymbol(file, position);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, position, newName);
                    case NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE -> throw new IllegalArgumentException("A rename cannot get " + key);
                };
            }
        }

        /** The extraction of the expression of a selection into a local variable of a given name. */
        record ExtractVariable(Path file, Range selection, String name) implements Request {

            public ExtractVariable {
                Assert.notNull("file", file);
                Assert.notNull("selection", selection);
                Assert.notNull("name", name);
            }

            @Override
            public ExtractVariable resolvedIn(Path root) {
                return new ExtractVariable(resolve(root, file), selection, name);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_AN_EXPRESSION -> LanguageServerException.notAnExpression(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case NO_SUCH_SYMBOL,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("An extraction of a variable cannot get " + key);
                };
            }
        }

        /** The extraction of the statements, or the expression, of a selection into a method of a given name. */
        record ExtractMethod(Path file, Range selection, String name) implements Request {

            public ExtractMethod {
                Assert.notNull("file", file);
                Assert.notNull("selection", selection);
                Assert.notNull("name", name);
            }

            @Override
            public ExtractMethod resolvedIn(Path root) {
                return new ExtractMethod(resolve(root, file), selection, name);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_EXTRACTABLE -> LanguageServerException.notExtractable(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case NO_SUCH_SYMBOL,
                            NOT_AN_EXPRESSION,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("An extraction of a method cannot get " + key);
                };
            }
        }

        /** The move of the nested type that encloses a position to a file of its own. */
        record MoveTypeToFile(Path file, Position position) implements Request {

            public MoveTypeToFile {
                Assert.notNull("file", file);
                Assert.notNull("position", position);
            }

            @Override
            public MoveTypeToFile resolvedIn(Path root) {
                return new MoveTypeToFile(resolve(root, file), position);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_MOVABLE -> LanguageServerException.notMovable(file, position);
                    case NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE -> throw new IllegalArgumentException("A move of a type cannot get " + key);
                };
            }
        }

        /**
         * The move of files to a package.
         *
         * @param files the files, at least one; the first is the one a refusal about a file names
         * @param target the package
         */
        record MoveToPackage(List<Path> files, PackageName target) implements Request {

            public MoveToPackage {
                Assert.field("files", files).notNull().notEmpty().noNullElement();
                Assert.notNull("target", target);
                files = List.copyOf(files);
            }

            @Override
            public Path file() {
                return files.getFirst();
            }

            @Override
            public MoveToPackage resolvedIn(Path root) {
                return new MoveToPackage(
                        files.stream().map(file -> resolve(root, file)).toList(), target);
            }

            /**
             * The details of a refusal for {@code NOT_MOVABLE_TO_PACKAGE} are the name of its reason. The exception
             * names the first file, except for the reasons about the set of files, which name them all.
             */
            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file());
                    case FILE_MISSING -> LanguageServerException.fileMissing(file());
                    case NOT_A_FILE -> LanguageServerException.notAFile(file());
                    case NOT_MOVABLE_TO_PACKAGE -> notMovable(details.getFirst());
                    case NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A move to a package cannot get " + key);
                };
            }

            private LanguageServerException notMovable(String reasonName) {
                NotMovableReason reason = Arrays.stream(NotMovableReason.values())
                        .filter(candidate -> candidate.get().equals(reasonName))
                        .findFirst()
                        .orElseThrow();
                boolean aboutTheSet =
                        reason == NotMovableReason.SEVERAL_SOURCE_FOLDERS || reason == NotMovableReason.NOTHING_TO_MOVE;

                return LanguageServerException.notMovableToPackage(
                        aboutTheSet ? files : List.of(file()), target, reason);
            }
        }

        /** The inlining of the local variable, the method or the constant at a position. */
        record Inline(Path file, Position position) implements Request {

            public Inline {
                Assert.notNull("file", file);
                Assert.notNull("position", position);
            }

            @Override
            public Inline resolvedIn(Path root) {
                return new Inline(resolve(root, file), position);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_INLINABLE -> LanguageServerException.notInlinable(file, position);
                    case NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE -> throw new IllegalArgumentException("An inlining cannot get " + key);
                };
            }
        }

        /** The places where the symbol at a position is used, and declared when that is asked for. */
        record References(Path file, Position position, boolean includeDeclaration) implements Request {

            public References {
                Assert.notNull("file", file);
                Assert.notNull("position", position);
            }

            @Override
            public References resolvedIn(Path root) {
                return new References(resolve(root, file), position, includeDeclaration);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NO_SUCH_SYMBOL -> LanguageServerException.noSuchSymbol(file, position);
                    case INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            NOT_CREATABLE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS ->
                        throw new IllegalArgumentException("A request for references cannot get " + key);
                };
            }
        }

        /** The creation of a type of a kind in a new file. */
        record CreateType(Path file, TypeKind kind) implements Request {

            public CreateType {
                Assert.notNull("file", file);
                Assert.notNull("kind", kind);
            }

            @Override
            public CreateType resolvedIn(Path root) {
                return new CreateType(resolve(root, file), kind);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case NOT_CREATABLE -> LanguageServerException.notCreatable(file, kind);
                    case FILE_MISSING,
                            NOT_A_FILE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_AN_EXPRESSION,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS ->
                        throw new IllegalArgumentException("A creation of a type cannot get " + key);
                };
            }
        }
    }

    /** What the server is expected to answer. */
    public sealed interface Answer {

        /** The same answer with the paths it holds put under the root and normalized. */
        Answer resolvedIn(Path root);

        /** The diagnostics of a file, none when it is clean. */
        record Diagnosed(List<Diagnostic> diagnostics) implements Answer {

            public Diagnosed {
                Assert.field("diagnostics", diagnostics).notNull().noNullElement();
                diagnostics = List.copyOf(diagnostics);
            }

            @Override
            public Diagnosed resolvedIn(Path root) {
                return this;
            }
        }

        /** The change set of a rename. */
        record Renamed(ChangeSet changes) implements Answer {

            public Renamed {
                Assert.notNull("changes", changes);
            }

            @Override
            public Renamed resolvedIn(Path root) {
                return new Renamed(resolvedIn(root, changes));
            }

            private static ChangeSet resolvedIn(Path root, ChangeSet changes) {
                return new ChangeSet(changes.changes().stream()
                        .map(change -> resolve(root, change))
                        .toList());
            }

            private static FileChange resolve(Path root, FileChange change) {
                return switch (change) {
                    case EditFile edit -> new EditFile(Scenario.resolve(root, edit.file()), edit.edits());
                    case CreateFile create -> new CreateFile(Scenario.resolve(root, create.file()));
                    case RenameFile rename ->
                        new RenameFile(Scenario.resolve(root, rename.from()), Scenario.resolve(root, rename.to()));
                    case DeleteFile delete -> new DeleteFile(Scenario.resolve(root, delete.file()));
                };
            }
        }

        /** The change set that extracts an expression into a variable, or a selection into a method: only edits of its file. */
        record Extracted(ChangeSet changes) implements Answer {

            public Extracted {
                Assert.notNull("changes", changes);
            }

            @Override
            public Extracted resolvedIn(Path root) {
                return new Extracted(Renamed.resolvedIn(root, changes));
            }
        }

        /**
         * The change set that moves a nested type to a file of its own: the creation of the file, its text, and the edits
         * of the files that refer to the type.
         */
        record Moved(ChangeSet changes) implements Answer {

            public Moved {
                Assert.notNull("changes", changes);
            }

            @Override
            public Moved resolvedIn(Path root) {
                return new Moved(Renamed.resolvedIn(root, changes));
            }
        }

        /**
         * The change set that moves files to a package: the edits of the {@code package} line of each file and of the
         * files that refer to it, and the move of each file to the folder of the package.
         */
        record MovedToPackage(ChangeSet changes) implements Answer {

            public MovedToPackage {
                Assert.notNull("changes", changes);
            }

            @Override
            public MovedToPackage resolvedIn(Path root) {
                return new MovedToPackage(Renamed.resolvedIn(root, changes));
            }
        }

        /** The change set that inlines a variable, a method or a constant: only edits, of the files that use it. */
        record Inlined(ChangeSet changes) implements Answer {

            public Inlined {
                Assert.notNull("changes", changes);
            }

            @Override
            public Inlined resolvedIn(Path root) {
                return new Inlined(Renamed.resolvedIn(root, changes));
            }
        }

        /** The change set that creates a type in a new file: the creation of the file and the edit that holds its text. */
        record Created(ChangeSet changes) implements Answer {

            public Created {
                Assert.notNull("changes", changes);
            }

            @Override
            public Created resolvedIn(Path root) {
                return new Created(Renamed.resolvedIn(root, changes));
            }
        }

        /** The places where a symbol is used, and where it is declared when that was asked for. */
        record Referenced(ReferenceLocations locations) implements Answer {

            public Referenced {
                Assert.notNull("locations", locations);
            }

            @Override
            public Referenced resolvedIn(Path root) {
                return new Referenced(new ReferenceLocations(
                        resolved(root, locations.declarations()), resolved(root, locations.references())));
            }

            private static List<Location> resolved(Path root, List<Location> locations) {
                return locations.stream()
                        .map(location -> new Location(Scenario.resolve(root, location.file()), location.position()))
                        .toList();
            }
        }

        /** The change set that organizes the imports of a file: only edits of that file. */
        record Organized(ChangeSet changes) implements Answer {

            public Organized {
                Assert.notNull("changes", changes);
            }

            @Override
            public Organized resolvedIn(Path root) {
                return new Organized(Renamed.resolvedIn(root, changes));
            }
        }

        /** The imports of the file are as they should be: the server has no edit to give. */
        record AlreadyOrganized() implements Answer {

            @Override
            public AlreadyOrganized resolvedIn(Path root) {
                return this;
            }
        }

        /**
         * The server declines the request: the exception to expect, which the request builds from this key and these
         * details.
         *
         * @param details what the refusal names, none for most: the imports the server cannot resolve, or the types it
         *     cannot choose between
         */
        record Refused(LanguageServerErrorKey key, List<String> details) implements Answer {

            public Refused {
                Assert.notNull("key", key);
                Assert.field("details", details).notNull().noNullElement();
                details = List.copyOf(details);
            }

            public Refused(LanguageServerErrorKey key) {
                this(key, List.of());
            }

            @Override
            public Refused resolvedIn(Path root) {
                return this;
            }
        }
    }
}
