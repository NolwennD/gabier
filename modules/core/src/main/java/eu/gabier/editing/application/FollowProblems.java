package eu.gabier.editing.application;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.JavaLanguageServer;
import eu.gabier.editing.domain.ProblemWatch;
import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Following the problems of the project as they change: the state a client starts from, and the project as it is when
 * the client is to be told what changed ({@link ProblemWatch#changesIn}).
 */
public final class FollowProblems {

    private final JavaLanguageServer server;

    public FollowProblems(JavaLanguageServer server) {
        Assert.notNull("server", server);

        this.server = server;
    }

    /**
     * What a client that follows the whole project starts from: the files that have a problem now.
     *
     * @throws eu.gabier.editing.domain.LanguageServerException if the server is not ready
     */
    public Following start() {
        var told = server.projectDiagnostics();

        return new Following(told, ProblemWatch.ofProject(told));
    }

    /**
     * What a client that follows files starts from: the problems of each file, one element per file in the order given,
     * a clean file included.
     *
     * @throws eu.gabier.editing.domain.LanguageServerException if the server is not ready or a file is not in its
     *     project (nothing is followed then)
     */
    public Following start(List<Path> files) {
        Assert.field("files", files).notNull().notEmpty().noNullElement();

        var found = server.diagnostics(files);
        var told = IntStream.range(0, files.size())
                .mapToObj(index -> new DiagnosedFile(files.get(index), found.get(index)))
                .toList();

        return new Following(told, ProblemWatch.ofFiles(told));
    }

    /**
     * The problems of the project as they are now, once the server has built it.
     *
     * @throws eu.gabier.editing.domain.LanguageServerException if the server is not ready
     */
    public List<DiagnosedFile> read() {
        return server.projectDiagnostics();
    }

    /**
     * Where a client starts.
     *
     * @param told what it is told first
     * @param watch what tells it the changes from there
     */
    public record Following(List<DiagnosedFile> told, ProblemWatch watch) {

        public Following {
            Assert.field("told", told).notNull().noNullElement();
            Assert.notNull("watch", watch);

            told = List.copyOf(told);
        }
    }
}
