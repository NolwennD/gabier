package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.Json;
import eu.gabier.protocol.TerminalText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * {@code init-agent [--dry-run] [--force] [--target=<folder>] [--only=<command>,...] <skill|rule|snippet>}: writes the
 * instructions of {@link AgentInstructions} into the project in the form the maintainers choose. It never talks to the
 * daemon: the cli does it alone, like {@code help}. The options may stand anywhere after the command.
 */
final class InitAgent implements Capability {

    static final Command COMMAND = Command.called("init-agent")
            .usage("""
                gabier [--json] init-agent [--dry-run] [--force] [--target=<folder>] [--only=<command>,...] \
                <skill|rule|snippet>\
                """)
            .example("""
                $ gabier init-agent --dry-run --only=rename,diagnostics snippet
                dry run, nothing written
                would create /work/demo/AGENTS.md

                <!-- gabier:start -->
                <!-- gabier 0.4.0 wrote this block (gabier init-agent snippet); run it again after an upgrade -->
                ## Java with gabier
                ...
                <!-- gabier:end -->\
                """)
            .standalone(
                    "write the instructions for a coding agent into the project, as a skill, a rule or a snippet",
                    Refusal.USAGE,
                    "init-agent-file-exists: the file exists and gabier did not write it (it holds no gabier block);"
                            + " --force overwrites it",
                    "init-agent-block-unfinished: the file has the start of a gabier block and no end after it",
                    "init-agent-block-repeated: the file has more than one start of a gabier block",
                    "init-agent-fence-unclosed: a snippet cannot be appended to a file that ends inside a code fence",
                    "init-agent-unknown-command: a name of --only is no command of gabier",
                    "init-agent-no-such-folder: the target is not a folder",
                    "init-agent-outside-folder: the file, or a folder of its path, is a link that leads out of the"
                            + " target folder",
                    "init-agent-write-failed: the file could not be read or written (exit code 1)")
            .withNotes("""
                The text is the one of gabier help agents, written in the form the project chooses:
                  skill    .claude/skills/gabier/SKILL.md, with its frontmatter (name, description)
                  rule     .claude/rules/gabier.md
                  snippet  a block of AGENTS.md, appended to the file when it has none, else replaced in place
                The block is marked <!-- gabier:start --> ... <!-- gabier:end --> and says the version of gabier that
                wrote it. A second run replaces the block in place and keeps what is around it, its line endings (a file
                with Windows ones gets them, taken from the start line of the block) and its mode. A marker is a
                line of its own, outside a code fence (CommonMark: three or more backticks or tildes, closed by
                the same character); a file with more than one start is refused, and so is a snippet for a file
                that ends inside a fence that is never closed. A skill that holds a block and lost its frontmatter gets
                it back. A skill or a rule that exists with no such block is not overwritten without --force; --force
                has no other effect.
                --only=<command>,... keeps the lines that cite no command and the bullets that cite one of these
                commands (help included); a name that is no command is refused.
                --only cuts per bullet: a bullet that cites several commands is kept whole when one is named, so
                --only=rename keeps the other commands that the same bullet cites.
                --target=<folder> is the project folder, the current folder by default. The files are written in a
                temporary file, then moved, and never outside that folder: a link that leads out of it is refused.
                A dry run writes nothing, makes no folder, and prints what it would write.\
                """);

    private static final String FORCE = "--force";
    private static final String TARGET = "--target=";
    private static final String ONLY = "--only=";

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var form = form(arguments);
        var only = only(arguments);
        var folder = folder(arguments);
        var dryRun = arguments.dryRun();

        var block = form.block(only.isEmpty() ? AgentInstructions.text() : AgentInstructions.text(only));
        var plan = AgentFile.plan(form, folder, block, arguments.has(FORCE));
        if (!dryRun) {
            AgentFile.write(plan);
        }
        invocation.out().println(invocation.json() ? json(form, plan, dryRun) : text(plan, dryRun));
    }

    private static AgentForm form(Arguments arguments) {
        var operands =
                arguments.withoutOptions(Arguments.DRY_RUN, FORCE, TARGET, ONLY).exactly(1);

        return AgentForm.named(operands.operand(0))
                .orElseThrow(() -> UsageException.unsupportedArguments(arguments.all()));
    }

    /** The commands of {@code --only}, each one known; none when the option is not given. */
    private static List<String> only(Arguments arguments) {
        var given = arguments.valueOf(ONLY);
        if (given.isEmpty()) {
            return List.of();
        }
        var names = Arrays.asList(given.get().split(",", -1));
        if (names.stream().anyMatch(String::isBlank)) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        names.stream()
                .filter(name -> Capabilities.named(name).isEmpty())
                .findFirst()
                .ifPresent(name -> {
                    throw InitAgentException.unknownCommand(
                            name,
                            Capabilities.all().stream()
                                    .map(capability -> capability.command().commandName())
                                    .toList());
                });

        return names;
    }

    /** The project folder: the one {@code --target=} names, else the current one, absolute; it must be a folder. */
    private static Path folder(Arguments arguments) {
        var given = arguments.valueOf(TARGET);
        if (given.isPresent() && given.get().isBlank()) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        var folder = Path.of(given.orElse(".")).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw InitAgentException.noSuchFolder(folder);
        }

        return folder;
    }

    private static String text(AgentFile.Plan plan, boolean dryRun) {
        var done = plan.action().verb(dryRun) + " "
                + TerminalText.neutralise(plan.file().toString());

        return dryRun
                ? "dry run, nothing written\n" + done + "\n\n" + plan.written().stripTrailing()
                : done;
    }

    private static String json(AgentForm form, AgentFile.Plan plan, boolean dryRun) {
        return "{\"form\":" + Json.quote(form.word()) + ",\"file\":"
                + Json.quote(plan.file().toString())
                + ",\"action\":" + Json.quote(plan.action().word()) + ",\"dryRun\":" + dryRun
                + (dryRun ? ",\"content\":" + Json.quote(plan.written()) : "") + "}";
    }
}
