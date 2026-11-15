package eu.gabier.daemon;

import java.nio.file.Path;

/**
 * The workspace of jdtls that a project keeps between two daemons: a folder of its own that survives the stop, and what
 * a start must find the same to reuse it.
 *
 * @param folder the {@code -data} folder jdtls is started on
 * @param fingerprint what the workspace was made with (the jdtls, the JDK it ran under): a start whose fingerprint
 *     differs from the one the folder was kept with starts on an empty folder. A single line.
 * @param projectRoot the project the workspace is of, which tells the workspace of a project that is gone
 */
record KeptWorkspace(Path folder, String fingerprint, Path projectRoot) {}
