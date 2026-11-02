package eu.gabier.daemon;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A jdtls that has been started: its process, and the two streams the language server protocol travels on. They are
 * given apart from the process so that the conversation can be held with something that is not a process.
 *
 * @param process the jdtls process, whose exit and whose tree the session watches and ends
 * @param fromJdtls what jdtls writes: the messages the client reads
 * @param toJdtls what jdtls reads: the messages the client writes
 */
record JdtlsConnection(Process process, InputStream fromJdtls, OutputStream toJdtls) {}
