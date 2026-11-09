package eu.gabier.cli.application;

import eu.gabier.cli.application.InvalidValuesException.OperationName;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.cli.domain.Projects;
import eu.gabier.protocol.DaemonStatus;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Runs an operation on the project of the files it names, for a front end that does not know the folder it is called
 * from (so it takes no relative path) and may be asked about files of several projects. The values are checked as
 * {@link OperationRunner} checks them, and each path must be absolute. The call goes to the daemon of the project that
 * holds its files, which must be one; an operation that names no file is for the project the front end was launched in.
 */
public final class ProjectOperations {

    private final Projects projects;

    /**
     * The operations of these projects.
     *
     * @param projects the projects, and the daemon of each
     */
    public ProjectOperations(Projects projects) {
        this.projects = projects;
    }

    /**
     * An operation that is run once the daemon of its project answers.
     *
     * @param run what asks the daemon, which is not asked yet
     * @param project the folder of the project it is run in
     */
    public record Prepared(Supplier<OperationResult> run, Path project) {}

    /**
     * Checks the values and finds the project of the call, and gives what asks its daemon, which is not asked yet.
     *
     * @throws eu.gabier.shared.error.domain.GeneratorException (the key {@code operation-invalid-values}) when {@link
     *     OperationRunner#run} would, when a path is not absolute, or when the files are not all in one project
     * @throws IllegalArgumentException when the operation is not one that asks the daemon for a result to decode
     */
    public Prepared prepare(Operation operation, Map<String, Object> values) {
        var parameters = OperationRunner.parameters(operation, values);
        var project = projectOf(operation, parameters);

        return new Prepared(new OperationRunner(projects.daemonOf(project)).prepare(operation, values), project);
    }

    /** The folder of the project the front end was launched in, where an operation that names no file runs. */
    public Path launched() {
        return projects.launched();
    }

    /** The status of the daemon of this project, which runs: none is started to tell it. */
    public DaemonStatus statusOf(Path project) {
        var reported = new OperationRunner(projects.daemonOf(project)).run(Operations.STATUS, Map.of());

        return ((OperationResult.Reported) reported).status();
    }

    private Path projectOf(Operation operation, Map<String, String> parameters) {
        @Nullable Path project = null;
        for (var parameter : operation.parameters()) {
            var value = parameters.get(parameter.name());
            if (value == null) {
                continue;
            }
            for (var file : filesOf(parameter, value)) {
                var path = absolute(operation, parameter, file);
                var holder = projects.projectOf(path);
                if (project == null) {
                    project = holder;
                } else if (!project.equals(holder)) {
                    throw InvalidValuesException.invalid(
                            OperationName.of(operation),
                            parameter.name(),
                            "be in one project: " + path + " is in " + holder + ", the other files of the call are in "
                                    + project);
                }
            }
        }

        return project == null ? projects.launched() : project;
    }

    private static List<String> filesOf(Parameter parameter, String value) {
        return switch (parameter.type()) {
            case PATH -> List.of(value);
            case PATHS -> value.lines().toList();
            case STRING, INTEGER, POSITION, BOOLEAN, ARRAY -> List.of();
        };
    }

    private static Path absolute(Operation operation, Parameter parameter, String file) {
        try {
            var path = Path.of(file);
            if (path.isAbsolute()) {
                return path;
            }
        } catch (InvalidPathException notAPath) {
            // Not a path at all: refused below, as a relative one is.
        }

        throw InvalidValuesException.invalid(
                OperationName.of(operation),
                parameter.name(),
                "be an absolute path: the folder the call is made from is not known");
    }
}
