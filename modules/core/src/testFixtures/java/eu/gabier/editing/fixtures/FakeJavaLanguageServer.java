package eu.gabier.editing.fixtures;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.PackageName;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.ReferenceLocations;
import eu.gabier.editing.domain.TypeKind;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A hand-written fake of {@link JavaLanguageServer}, for the tests of the core and of the modules above it: no
 * process, no protocol, no file read. It answers what a {@link Scenario} says the real server answers, and nothing
 * else: a test scripts it with scenarios (the only way in), so that every answer it gives is one that an adapter's
 * integration test replays against the real server.
 *
 * <p>A fake is bound to the root of the project it plays: the scenarios have relative paths, which it puts under the
 * root, and the requests it receives are recognized once their path is normalized under the root too. A request that no
 * scenario scripted is a mistake of the test, not a case of the server: it fails with an {@link AssertionError} that
 * says what was asked and what was scripted, instead of answering with something that looks plausible.
 */
public final class FakeJavaLanguageServer implements JavaLanguageServer {

    private final Path root;
    private final Map<Request, Answer> script = new HashMap<>();
    private final List<Request> asked = new ArrayList<>();

    /**
     * Creates a fake that knows no answer yet.
     *
     * @param root the root of the project the scenarios play on: the directory where a test writes their files
     */
    public FakeJavaLanguageServer(Path root) {
        Assert.notNull("root", root);

        this.root = root;
    }

    /**
     * Scripts the answer of a scenario to its request.
     *
     * @return this fake, to script the next scenario
     * @throws IllegalArgumentException if another scenario already scripted an answer to the same request
     */
    public FakeJavaLanguageServer scripted(Scenario scenario) {
        Assert.notNull("scenario", scenario);
        Scenario inside = scenario.inside(root);
        if (script.putIfAbsent(inside.request(), inside.answer()) != null) {
            throw new IllegalArgumentException("A scenario already scripted the answer to " + inside.request());
        }

        return this;
    }

    /** The requests received so far, in order, with their paths under the root: the unscripted ones included. */
    public List<Request> asked() {
        return List.copyOf(asked);
    }

    // A scenario never pairs a request with an answer of the other kind (see its constructor): the casts hold.

    @Override
    public List<List<Diagnostic>> diagnostics(List<Path> files) {
        return files.stream()
                .map(file -> new Request.Diagnostics(file).resolvedIn(root))
                .map(request -> ((Answer.Diagnosed) answerTo(request)).diagnostics())
                .toList();
    }

    @Override
    public ChangeSet rename(Path file, Position position, String newName) {
        Request request = new Request.Rename(file, position, newName).resolvedIn(root);

        return ((Answer.Renamed) answerTo(request)).changes();
    }

    @Override
    public ChangeSet extractVariable(Path file, Range selection, String name) {
        Request request = new Request.ExtractVariable(file, selection, name).resolvedIn(root);

        return ((Answer.Extracted) answerTo(request)).changes();
    }

    @Override
    public ChangeSet extractConstant(Path file, Range selection, String name) {
        Request request = new Request.ExtractConstant(file, selection, name).resolvedIn(root);

        return ((Answer.Extracted) answerTo(request)).changes();
    }

    @Override
    public ChangeSet extractField(Path file, Range selection, String name) {
        Request request = new Request.ExtractField(file, selection, name).resolvedIn(root);

        return ((Answer.Extracted) answerTo(request)).changes();
    }

    @Override
    public ChangeSet extractMethod(Path file, Range selection, String name) {
        Request request = new Request.ExtractMethod(file, selection, name).resolvedIn(root);

        return ((Answer.Extracted) answerTo(request)).changes();
    }

    @Override
    public ChangeSet moveTypeToFile(Path file, Position position) {
        Request request = new Request.MoveTypeToFile(file, position).resolvedIn(root);

        return ((Answer.Moved) answerTo(request)).changes();
    }

    @Override
    public ChangeSet moveToPackage(List<Path> files, PackageName target) {
        Request request = new Request.MoveToPackage(files, target).resolvedIn(root);

        return ((Answer.MovedToPackage) answerTo(request)).changes();
    }

    @Override
    public ChangeSet inline(Path file, Position position) {
        Request request = new Request.Inline(file, position).resolvedIn(root);

        return ((Answer.Inlined) answerTo(request)).changes();
    }

    @Override
    public ChangeSet createType(Path file, TypeKind kind) {
        Request request = new Request.CreateType(file, kind).resolvedIn(root);

        return ((Answer.Created) answerTo(request)).changes();
    }

    @Override
    public ChangeSet createPackageInfo(Path file) {
        Request request = new Request.CreatePackageInfo(file).resolvedIn(root);

        return ((Answer.Created) answerTo(request)).changes();
    }

    @Override
    public ReferenceLocations references(Path file, Position position, boolean includeDeclaration) {
        Request request = new Request.References(file, position, includeDeclaration).resolvedIn(root);

        return ((Answer.Referenced) answerTo(request)).locations();
    }

    @Override
    public OfferedActions codeActions(Path file, Range range) {
        Request request = new Request.CodeActions(file, range).resolvedIn(root);

        return ((Answer.Offered) answerTo(request)).offered();
    }

    @Override
    public ChangeSet codeActionEdit(Path file, Range range, OfferedAction action) {
        Request request = new Request.ApplyCodeAction(file, range, action).resolvedIn(root);

        return ((Answer.CodeActionApplied) answerTo(request)).changes();
    }

    @Override
    public Optional<ChangeSet> organizeImports(Path file) {
        Request request = new Request.OrganizeImports(file).resolvedIn(root);

        return switch (answerTo(request)) {
            case Answer.Organized organized -> Optional.of(organized.changes());
            case Answer.AlreadyOrganized _ -> Optional.empty();
            default -> throw new AssertionError("An organize imports request got a different kind of answer");
        };
    }

    @Override
    public Optional<ChangeSet> cleanUp(Path file, List<CleanUp> cleanUps) {
        Request request = new Request.CleanUpFile(file, cleanUps).resolvedIn(root);

        return switch (answerTo(request)) {
            case Answer.CleanedUp cleaned -> Optional.of(cleaned.changes());
            case Answer.AlreadyClean _ -> Optional.empty();
            default -> throw new AssertionError("A clean-up request got a different kind of answer");
        };
    }

    /** The scripted answer to the request, or the refusal it scripted, thrown. */
    private Answer answerTo(Request request) {
        asked.add(request);
        Answer answer = script.get(request);
        if (answer == null) {
            throw new AssertionError("The fake language server was not scripted for " + request + ", only for "
                    + script.keySet() + ": add the scenario that says what the real server answers");
        }
        if (answer instanceof Answer.Refused refused) {
            throw request.refusal(refused.key(), refused.details());
        }

        return answer;
    }
}
