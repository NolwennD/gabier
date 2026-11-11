package eu.gabier.editing.fixtures;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.FileChange;
import eu.gabier.editing.domain.FileChange.CreateFile;
import eu.gabier.editing.domain.FileChange.DeleteFile;
import eu.gabier.editing.domain.FileChange.EditFile;
import eu.gabier.editing.domain.FileChange.RenameFile;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Location;
import eu.gabier.editing.domain.NotMovableReason;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.shared.error.domain.Assert;
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
            case Answer.ProjectDiagnosed _ ->
                require(request instanceof Request.ProjectDiagnostics, "project diagnosed", request);
            case Answer.Renamed _ -> require(request instanceof Request.Rename, "renamed", request);
            case Answer.Extracted _ ->
                require(
                        request instanceof Request.ExtractVariable
                                || request instanceof Request.ExtractMethod
                                || request instanceof Request.ExtractConstant
                                || request instanceof Request.ExtractField,
                        "extracted",
                        request);
            case Answer.Moved _ -> require(request instanceof Request.MoveTypeToFile, "moved", request);
            case Answer.MovedToPackage _ ->
                require(request instanceof Request.MoveToPackage, "moved to a package", request);
            case Answer.Inlined _ -> require(request instanceof Request.Inline, "inlined", request);
            case Answer.Referenced _ -> require(request instanceof Request.References, "referenced", request);
            case Answer.Created _ ->
                require(
                        request instanceof Request.CreateType || request instanceof Request.CreatePackageInfo,
                        "created",
                        request);
            case Answer.Offered _ -> require(request instanceof Request.CodeActions, "offered", request);
            case Answer.CodeActionApplied _ ->
                require(request instanceof Request.ApplyCodeAction, "code action applied", request);
            case Answer.Organized _ -> require(request instanceof Request.OrganizeImports, "organized", request);
            case Answer.AlreadyOrganized _ ->
                require(request instanceof Request.OrganizeImports, "already organized", request);
            case Answer.CleanedUp _ -> require(request instanceof Request.CleanUpFile, "cleaned up", request);
            case Answer.AlreadyClean _ -> require(request instanceof Request.CleanUpFile, "already clean", request);
            case Answer.Refused refused -> request.refusal(refused.key(), refused.details());
        }
    }

    /** The failure of the server on any request: the details are the code of its error and its message. */
    private static LanguageServerException requestFailed(List<String> details) {
        return LanguageServerException.requestFailed(Integer.parseInt(details.get(0)), details.get(1));
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
         * @param details what the refusal names: the imports the server cannot resolve, the types it cannot choose
         *     between, or the empty package and the package of the file for {@code EMPTY_PACKAGE}
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A request for diagnostics cannot get " + key);
                };
            }
        }

        /**
         * The diagnostics of every Java file of the project: no file is named, the server knows which are the
         * project's. {@link #file()} is the root of the project, which is what the request is about.
         */
        record ProjectDiagnostics() implements Request {

            @Override
            public Path file() {
                return Path.of("");
            }

            @Override
            public ProjectDiagnostics resolvedIn(Path root) {
                return this;
            }

            /** The only refusals are a server that is not ready and a server that fails on the request. */
            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT,
                            FILE_MISSING,
                            NOT_A_FILE,
                            EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException(
                                "A request for the diagnostics of the project cannot get " + key);
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case AMBIGUOUS_IMPORTS -> LanguageServerException.ambiguousImports(file, details);
                    case UNRESOLVED_IMPORTS -> LanguageServerException.unresolvedImports(file, details);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A request to organize imports cannot get " + key);
                };
            }
        }

        /**
         * The clean-ups of a file, applied in the order given.
         *
         * @param cleanUps the clean-ups to apply, at least one
         */
        record CleanUpFile(Path file, List<CleanUp> cleanUps) implements Request {

            public CleanUpFile {
                Assert.notNull("file", file);
                Assert.field("cleanUps", cleanUps).notNull().notEmpty().noNullElement();
                cleanUps = List.copyOf(cleanUps);
            }

            @Override
            public CleanUpFile resolvedIn(Path root) {
                return new CleanUpFile(resolve(root, file), cleanUps);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE -> throw new IllegalArgumentException("A clean-up cannot get " + key);
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NO_SUCH_SYMBOL -> LanguageServerException.noSuchSymbol(file, position);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, position, newName);
                    case EMPTY_PACKAGE ->
                        LanguageServerException.emptyPackage(file, position, details.get(0), details.get(1));
                    case NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_EXTRACTABLE -> LanguageServerException.notExtractable(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_EXTRACTABLE -> LanguageServerException.notExtractable(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("An extraction of a method cannot get " + key);
                };
            }
        }

        /** The extraction of the expression of a selection into a constant of a given name. */
        record ExtractConstant(Path file, Range selection, String name) implements Request {

            public ExtractConstant {
                Assert.notNull("file", file);
                Assert.notNull("selection", selection);
                Assert.notNull("name", name);
            }

            @Override
            public ExtractConstant resolvedIn(Path root) {
                return new ExtractConstant(resolve(root, file), selection, name);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_EXTRACTABLE -> LanguageServerException.notExtractable(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("An extraction of a constant cannot get " + key);
                };
            }
        }

        /** The extraction of the expression of a selection into a field of a given name. */
        record ExtractField(Path file, Range selection, String name) implements Request {

            public ExtractField {
                Assert.notNull("file", file);
                Assert.notNull("selection", selection);
                Assert.notNull("name", name);
            }

            @Override
            public ExtractField resolvedIn(Path root) {
                return new ExtractField(resolve(root, file), selection, name);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_EXTRACTABLE -> LanguageServerException.notExtractable(file, selection);
                    case INVALID_NAME -> LanguageServerException.invalidName(file, selection.start(), name);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("An extraction of a field cannot get " + key);
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_MOVABLE -> LanguageServerException.notMovable(file, position);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file());
                    case FILE_MISSING -> LanguageServerException.fileMissing(file());
                    case NOT_A_FILE -> LanguageServerException.notAFile(file());
                    case NOT_MOVABLE_TO_PACKAGE -> notMovable(details.getFirst());
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_INLINABLE -> LanguageServerException.notInlinable(file, position);
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_APPLICABLE,
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NO_SUCH_SYMBOL -> LanguageServerException.noSuchSymbol(file, position);
                    case EMPTY_PACKAGE,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            NOT_APPLICABLE,
                            NOT_CREATABLE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS ->
                        throw new IllegalArgumentException("A request for references cannot get " + key);
                };
            }
        }

        /** The code actions offered on a range of a file. */
        record CodeActions(Path file, Range range) implements Request {

            public CodeActions {
                Assert.notNull("file", file);
                Assert.notNull("range", range);
            }

            @Override
            public CodeActions resolvedIn(Path root) {
                return new CodeActions(resolve(root, file), range);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_APPLICABLE,
                            EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("A request for code actions cannot get " + key);
                };
            }
        }

        /** The edit of one of the code actions offered on a range of a file. */
        record ApplyCodeAction(Path file, Range range, OfferedAction action) implements Request {

            public ApplyCodeAction {
                Assert.notNull("file", file);
                Assert.notNull("range", range);
                Assert.notNull("action", action);
            }

            @Override
            public ApplyCodeAction resolvedIn(Path root) {
                return new ApplyCodeAction(resolve(root, file), range, action);
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case FILE_MISSING -> LanguageServerException.fileMissing(file);
                    case NOT_A_FILE -> LanguageServerException.notAFile(file);
                    case NOT_APPLICABLE -> LanguageServerException.notApplicable(file, range, action.title());
                    case EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS,
                            NOT_CREATABLE ->
                        throw new IllegalArgumentException("The edit of a code action cannot get " + key);
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
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case NOT_CREATABLE -> LanguageServerException.notCreatable(file, kind);
                    case FILE_MISSING,
                            NOT_A_FILE,
                            NOT_APPLICABLE,
                            EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
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

        /** The creation of the {@code package-info.java} of a package, in a new file. */
        record CreatePackageInfo(Path file) implements Request {

            public CreatePackageInfo {
                Assert.notNull("file", file);
            }

            @Override
            public CreatePackageInfo resolvedIn(Path root) {
                return new CreatePackageInfo(resolve(root, file));
            }

            @Override
            public LanguageServerException refusal(LanguageServerErrorKey key, List<String> details) {
                return switch (key) {
                    case NOT_READY -> LanguageServerException.notReady();
                    case REQUEST_FAILED -> requestFailed(details);
                    case FILE_OUTSIDE_PROJECT -> LanguageServerException.fileOutsideProject(file);
                    case NOT_CREATABLE -> LanguageServerException.notCreatablePackageInfo(file);
                    case FILE_MISSING,
                            NOT_A_FILE,
                            NOT_APPLICABLE,
                            EMPTY_PACKAGE,
                            NO_SUCH_SYMBOL,
                            INVALID_NAME,
                            NOT_EXTRACTABLE,
                            NOT_INLINABLE,
                            NOT_MOVABLE,
                            NOT_MOVABLE_TO_PACKAGE,
                            AMBIGUOUS_IMPORTS,
                            UNRESOLVED_IMPORTS ->
                        throw new IllegalArgumentException("A creation of a package-info cannot get " + key);
                };
            }
        }
    }

    /** What a replay compares between the answer of a scenario and the one of the real server. */
    public enum Comparison {

        /**
         * The effect: the text of the files once a change set is applied, the places of a symbol, the key of a
         * refusal. Another server that gets there with other edits passes.
         */
        EFFECT,

        /** The severity and the position of each diagnostic, not the text of its message. */
        SEVERITY_AND_POSITION,

        /** The answer as the server words it (the titles of the actions it offers): the coupling that stays. */
        EXACT
    }

    /** What the server is expected to answer. */
    public sealed interface Answer {

        /** The same answer with the paths it holds put under the root and normalized. */
        Answer resolvedIn(Path root);

        /**
         * What a replay compares between this answer and the one of the real server. By default it is the effect: what
         * the answer does to the project, the places or the refusal it gives, never the way a server chose to write
         * it.
         */
        default Comparison comparison() {
            return Comparison.EFFECT;
        }

        /** An answer that is a change set: its effect is the text of the files once the set is applied. */
        sealed interface Changing extends Answer {

            ChangeSet changes();
        }

        /** The diagnostics of a file, none when it is clean. */
        record Diagnosed(List<Diagnostic> diagnostics) implements Answer {

            public Diagnosed {
                Assert.field("diagnostics", diagnostics).notNull().noNullElement();
                diagnostics = List.copyOf(diagnostics);
            }

            @Override
            public Comparison comparison() {
                return Comparison.SEVERITY_AND_POSITION;
            }

            @Override
            public Diagnosed resolvedIn(Path root) {
                return this;
            }
        }

        /**
         * The diagnostics of every Java file of the project, a file that is clean included with none: which files are
         * answered is part of the answer. A replay compares the files and, for each, the severity and the position of
         * its diagnostics.
         */
        record ProjectDiagnosed(List<DiagnosedFile> files) implements Answer {

            public ProjectDiagnosed {
                Assert.field("files", files).notNull().noNullElement();
                files = List.copyOf(files);
            }

            @Override
            public Comparison comparison() {
                return Comparison.SEVERITY_AND_POSITION;
            }

            @Override
            public ProjectDiagnosed resolvedIn(Path root) {
                return new ProjectDiagnosed(files.stream()
                        .map(file -> new DiagnosedFile(Scenario.resolve(root, file.file()), file.diagnostics()))
                        .toList());
            }
        }

        /** The change set of a rename. */
        record Renamed(ChangeSet changes) implements Changing {

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
        record Extracted(ChangeSet changes) implements Changing {

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
        record Moved(ChangeSet changes) implements Changing {

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
        record MovedToPackage(ChangeSet changes) implements Changing {

            public MovedToPackage {
                Assert.notNull("changes", changes);
            }

            @Override
            public MovedToPackage resolvedIn(Path root) {
                return new MovedToPackage(Renamed.resolvedIn(root, changes));
            }
        }

        /** The change set that inlines a variable, a method or a constant: only edits, of the files that use it. */
        record Inlined(ChangeSet changes) implements Changing {

            public Inlined {
                Assert.notNull("changes", changes);
            }

            @Override
            public Inlined resolvedIn(Path root) {
                return new Inlined(Renamed.resolvedIn(root, changes));
            }
        }

        /** The change set that creates a type in a new file: the creation of the file and the edit that holds its text. */
        record Created(ChangeSet changes) implements Changing {

            public Created {
                Assert.notNull("changes", changes);
            }

            @Override
            public Created resolvedIn(Path root) {
                return new Created(Renamed.resolvedIn(root, changes));
            }
        }

        /**
         * The code actions the server offers on a range, none when it offers nothing there. A replay compares them as
         * the Java and the protocol say, not as one run printed them: an empty answer is exact, a non-empty one lists
         * the actions the server must offer (it may offer others, whose titles are its own).
         */
        record Offered(OfferedActions offered) implements Answer {

            public Offered {
                Assert.notNull("offered", offered);
            }

            @Override
            public Comparison comparison() {
                return Comparison.EXACT;
            }

            @Override
            public Offered resolvedIn(Path root) {
                return this;
            }
        }

        /** The change set of one code action: the edits it makes, to the files it changes. */
        record CodeActionApplied(ChangeSet changes) implements Changing {

            public CodeActionApplied {
                Assert.notNull("changes", changes);
            }

            @Override
            public CodeActionApplied resolvedIn(Path root) {
                return new CodeActionApplied(Renamed.resolvedIn(root, changes));
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
        record Organized(ChangeSet changes) implements Changing {

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

        /** The change set that cleans a file up: only edits of that file, one that covers the whole text. */
        record CleanedUp(ChangeSet changes) implements Changing {

            public CleanedUp {
                Assert.notNull("changes", changes);
            }

            @Override
            public CleanedUp resolvedIn(Path root) {
                return new CleanedUp(Renamed.resolvedIn(root, changes));
            }
        }

        /** The file has nothing to clean up for the clean-ups asked: the server has no edit to give. */
        record AlreadyClean() implements Answer {

            @Override
            public AlreadyClean resolvedIn(Path root) {
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
