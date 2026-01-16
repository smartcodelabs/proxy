package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player disconnects from the proxy.
 *
 * <p>This event is not cancellable since the disconnection has already occurred.</p>
 */
public class DisconnectEvent {

    private final Player player;
    private final DisconnectReason reason;

    public DisconnectEvent(@Nonnull Player player, @Nonnull DisconnectReason reason) {
        this.player = player;
        this.reason = reason;
    }

    /**
     * Get the player who disconnected.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the reason for the disconnection.
     *
     * @return the disconnect reason
     */
    @Nonnull
    public DisconnectReason getReason() {
        return reason;
    }

    /**
     * Reasons for a player disconnection.
     */
    public enum DisconnectReason {
        /**
         * The player disconnected normally (quit).
         */
        DISCONNECTED,

        /**
         * The connection timed out.
         */
        TIMEOUT,

        /**
         * The player was kicked by the proxy or a plugin.
         */
        KICKED,

        /**
         * The backend server closed the connection.
         */
        SERVER_DISCONNECT,

        /**
         * The connection was closed due to an error.
         */
        ERROR,

        /**
         * The player is being transferred to another server.
         */
        TRANSFER
    }
}

