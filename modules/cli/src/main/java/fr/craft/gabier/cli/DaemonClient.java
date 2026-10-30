package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.RpcRequest;
import fr.craft.gabier.protocol.RpcResponse;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * One conversation with the daemon, in JSON-RPC 2.0 on its Unix socket, one JSON object per line. Plain JDK, written
 * by hand: the native image of the cli has no library to lean on.
 */
final class DaemonClient implements AutoCloseable {

    private final BufferedReader input;
    private final OutputStream output;
    private final Closeable connection;

    DaemonClient(InputStream input, OutputStream output, Closeable connection) {
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
        this.connection = connection;
    }

    /**
     * Connects to the daemon listening on the socket.
     *
     * @throws DaemonException when nothing answers on the socket (it does not wait: a connection to a socket that
     *     nobody listens on is refused at once)
     */
    static DaemonClient connect(Path socket) {
        try {
            var channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(socket));

            return new DaemonClient(Channels.newInputStream(channel), Channels.newOutputStream(channel), channel);
        } catch (IOException e) {
            throw DaemonException.notRunning(socket);
        }
    }

    /** Asks the daemon to run a method that takes no parameters, and reads its response. */
    RpcResponse call(String method) {
        try {
            output.write((new RpcRequest("1", method).toLine() + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            var line = input.readLine();
            if (line == null) {
                throw DaemonException.connectionFailed("the daemon hung up without answering", null);
            }

            return RpcResponse.parse(line);
        } catch (IOException e) {
            throw DaemonException.connectionFailed("the connection was lost", e);
        }
    }

    /** Waits until the daemon closes the connection, which a daemon that stops does once its socket is removed. */
    void awaitEnd() {
        try {
            while (input.readLine() != null) {
                // Whatever the daemon says on its way out is of no interest.
            }
        } catch (IOException e) {
            throw DaemonException.connectionFailed("the connection was lost", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (IOException e) {
            // The conversation is over: there is nothing to do about a connection that does not close.
        }
    }
}
