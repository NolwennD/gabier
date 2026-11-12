package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.RpcNotification;
import eu.gabier.protocol.TerminalText;
import java.util.function.Consumer;

/**
 * What the commands that follow something share ({@code diagnostics --watch}, {@code affected-tests --watch}): every
 * line is printed and flushed as soon as it comes, a notice of the daemon is told to the person who reads, and the
 * command ends with the problem the daemon ended the subscription for, or with the failure of the connection when the
 * daemon goes away. What a command is told that is its own (the problems, the tests) is for the command to show.
 */
final class Streaming {

    private Streaming() {}

    /**
     * Shows what the daemon tells, each line as soon as it comes, until the daemon ends the subscription (its problem
     * is the refusal of the command) or goes away (the failure of the connection). What ended it is returned, for the
     * caller to throw: the command never ends any other way. A notification of a later version of the daemon is not
     * shown.
     *
     * @param own shows a notification that is not a notice and does not end the subscription
     */
    static RuntimeException readUntilItEnds(
            Invocation invocation, Daemon.Following following, Consumer<RpcNotification> own) {
        while (true) {
            RpcNotification message;
            try {
                var notification = following.next();
                message = new RpcNotification(notification.method(), notification.params());
            } catch (RuntimeException goneAway) {
                return goneAway;
            }
            switch (message.method()) {
                case JsonRpc.NOTICE ->
                    print(
                            invocation,
                            invocation.json()
                                    ? "{\"notice\":" + Json.quote(message.notice()) + "}"
                                    : "gabier: notice: " + TerminalText.neutralise(message.notice()));
                case JsonRpc.ENDED -> {
                    var problem = message.ended();

                    return new DaemonRefusal(
                            problem.type(),
                            problem.title(),
                            problem.detail(),
                            problem.key(),
                            problem.parameters(),
                            problem.category());
                }
                default -> own.accept(message);
            }
        }
    }

    /** Prints the line and flushes it: the person who reads, or the program that pipes it, sees it at once. */
    static void print(Invocation invocation, String line) {
        invocation.out().println(line);
        invocation.out().flush();
    }
}
