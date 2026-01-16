package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired before a player connects to a backend server.
 * This includes initial connections and server transfers.
 *
 * <p>Setting the result allows you to redirect the player to a different server
 * or cancel the connection entirely.</p>
 */
public class ServerPreConnectEvent implements ResultedEvent<ServerPreConnectEvent.ServerResult> {

    private final Player player;
    private final RegisteredServer originalServer;
    private ServerResult result;

    public ServerPreConnectEvent(@Nonnull Player player, @Nonnull RegisteredServer server) {
        this.player = player;
        this.originalServer = server;
        this.result = ServerResult.allowed(server);
    }

    /**
     * Get the player being connected.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the server that was originally requested.
     *
     * @return the original target server
     */
    @Nonnull
    public RegisteredServer getOriginalServer() {
        return originalServer;
    }

    @Override
    public ServerResult getResult() {
        return result;
    }

    @Override
    public void setResult(ServerResult result) {
        this.result = result;
    }

    /**
     * Result for a server pre-connect event.
     */
    public static final class ServerResult implements Result {

        private static final ServerResult DENIED = new ServerResult(false, null, null);

        private final boolean allowed;
        private final RegisteredServer server;
        private final String denyReason;

        private ServerResult(boolean allowed, @Nullable RegisteredServer server, @Nullable String denyReason) {
            this.allowed = allowed;
            this.server = server;
            this.denyReason = denyReason;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Get the server to connect to.
         *
         * @return the target server, or null if denied
         */
        @Nullable
        public RegisteredServer getServer() {
            return server;
        }

        /**
         * Get the reason for denying the connection.
         *
         * @return the deny reason, or null if allowed
         */
        @Nullable
        public String getDenyReason() {
            return denyReason;
        }

        /**
         * Allow the connection to the specified server.
         *
         * @param server the server to connect to
         * @return an allowed result
         */
        public static ServerResult allowed(@Nonnull RegisteredServer server) {
            return new ServerResult(true, server, null);
        }

        /**
         * Deny the connection.
         *
         * @return a denied result
         */
        public static ServerResult denied() {
            return DENIED;
        }

        /**
         * Deny the connection with a reason.
         *
         * @param reason the reason to show the player
         * @return a denied result
         */
        public static ServerResult denied(@Nonnull String reason) {
            return new ServerResult(false, null, reason);
        }
    }
}

