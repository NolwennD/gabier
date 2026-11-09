package eu.gabier.cli.domain;

import java.nio.file.Path;

/**
 * The projects a front end that does not run in the folder of one of them can reach: a project is a folder, and each
 * has a daemon of its own. The project of a file is the nearest folder above it that holds a {@code .git} (a folder in a
 * clone, a file in a worktree), else the project the front end was launched in.
 */
public interface Projects {

    /** The folder of the project the front end was launched in. */
    Path launched();

    /** The folder of the project this absolute file belongs to, whether the file exists or not. */
    Path projectOf(Path file);

    /** The daemon of the project in this folder, as {@link #projectOf} and {@link #launched} give it. */
    Daemon daemonOf(Path project);
}
