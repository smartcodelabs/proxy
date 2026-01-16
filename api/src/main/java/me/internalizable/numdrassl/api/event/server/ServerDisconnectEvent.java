package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired when a player disconnects from a backend server.
 */
public class ServerDisconnectEvent {

    private final Player player;
    private final RegisteredServer server;
    private final String reason;

    public ServerDisconnectEvent(@Nonnull Player player, @Nonnull RegisteredServer server,
                                  @Nullable String reason) {
        this.player = player;
        this.server = server;
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
     * Get the server the player disconnected from.
     *
     * @return the server
     */
    @Nonnull
    public RegisteredServer getServer() {
        return server;
    }

    /**
     * Get the reason for the disconnection, if any.
     *
     * @return the disconnect reason, or null
     */
    @Nullable
    public String getReason() {
        return reason;
    }
}

