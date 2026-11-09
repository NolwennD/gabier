package eu.gabier.cli.infrastructure.primary;

import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.util.List;

/** {@code init-agent} did not write: the request is refused, or the file system failed. Nothing is left half written. */
final class InitAgentException extends GeneratorException {

    private InitAgentException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** The file is there and holds no block of gabier: it is not overwritten without {@code --force}. */
    static InitAgentException fileExists(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_FILE_EXISTS)
                .message("The file exists and gabier did not write it (it holds no gabier block); "
                        + "--force overwrites it")
                .addParameter("file", file.toString()));
    }

    /** The file ends inside a code fence: a block appended to it would be part of the fence, and not found again. */
    static InitAgentException fenceUnclosed(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_FENCE_UNCLOSED)
                .message("The file ends inside a code fence that is never closed; close the fence, "
                        + "or put the block in the file by hand")
                .addParameter("file", file.toString()));
    }

    /** The file did not exist when gabier looked and does now: someone made it since, and gabier does not take it. */
    static InitAgentException fileAppeared(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_FILE_EXISTS)
                .message("The file was created while gabier was writing it; nothing was written, "
                        + "run the command again")
                .addParameter("file", file.toString()));
    }

    /** The file has the start of the block and no end after it: gabier does not guess what to cut. */
    static InitAgentException blockUnfinished(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_BLOCK_UNFINISHED)
                .message("The file has the start of a gabier block and no end after it; mend or remove the block")
                .addParameter("file", file.toString()));
    }

    /** The file has more than one start of the block: gabier does not guess which one is its own. */
    static InitAgentException blockRepeated(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_BLOCK_REPEATED)
                .message("The file has more than one gabier block; keep one and remove the others")
                .addParameter("file", file.toString()));
    }

    /** A name of {@code --only} is no command of gabier. */
    static InitAgentException unknownCommand(String command, List<String> known) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_UNKNOWN_COMMAND)
                .message("--only names " + command + ", which is no command; the commands are "
                        + String.join(", ", known))
                .addParameter("command", command));
    }

    /** The target is no folder. */
    static InitAgentException noSuchFolder(Path folder) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_NO_SUCH_FOLDER)
                .message("The target is not a folder")
                .addParameter("target", folder.toString()));
    }

    /** The file, or a folder of its path, is a link that leads out of the target folder: nothing is written there. */
    static InitAgentException outsideFolder(Path file) {
        return new InitAgentException(GeneratorException.badRequest(CliErrorKey.INIT_AGENT_OUTSIDE_FOLDER)
                .message("The file is, or lies under, a link that leads out of the target folder; "
                        + "nothing is written outside it")
                .addParameter("file", file.toString()));
    }

    /** The file system failed: the environment, not the request. */
    static InitAgentException writeFailed(Path file, Exception cause) {
        return new InitAgentException(GeneratorException.internalServerError(CliErrorKey.INIT_AGENT_WRITE_FAILED)
                .message("The file could not be read or written ("
                        + cause.getClass().getSimpleName() + ")")
                .addParameter("file", file.toString())
                .cause(cause));
    }
}
