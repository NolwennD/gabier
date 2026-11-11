package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonNotification;
import eu.gabier.cli.domain.DaemonReply;
import eu.gabier.protocol.RpcNotification;

/** A conversation with the daemon that goes on after the answer: what it writes next is read from the same connection. */
final class FollowedDaemon implements Daemon.Following {

    private final DaemonClient client;
    private final DaemonReply reply;

    FollowedDaemon(DaemonClient client, DaemonReply reply) {
        this.client = client;
        this.reply = reply;
    }

    @Override
    public DaemonReply reply() {
        return reply;
    }

    @Override
    public DaemonNotification next() {
        var message = client.next()
                .orElseThrow(() ->
                        DaemonException.connectionFailed("the daemon stopped: nothing is followed any more", null));
        if (message instanceof RpcNotification notification) {
            return new DaemonNotification(notification.method(), notification.params());
        }

        throw DaemonException.connectionFailed("the daemon answered again instead of telling what changed", null);
    }

    @Override
    public void close() {
        client.close();
    }
}
