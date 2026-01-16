package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Event fired after a player has successfully connected to a backend server.
 */
public class ServerConnectedEvent {

    private final Player player;
    private final RegisteredServer server;
    private final RegisteredServer previousServer;

    public ServerConnectedEvent(@Nonnull Player player, @Nonnull RegisteredServer server,
                                 @Nullable RegisteredServer previousServer) {
        this.player = player;
        this.server = server;
        this.previousServer = previousServer;
    }

    /**
     * Get the player who connected.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the server the player connected to.
     *
     * @return the current server
     */
    @Nonnull
    public RegisteredServer getServer() {
        return server;
    }

    /**
     * Get the server the player was previously connected to, if any.
     * This will be empty for initial connections.
     *
     * @return the previous server, or empty
     */
    @Nonnull
    public Optional<RegisteredServer> getPreviousServer() {
        return Optional.ofNullable(previousServer);
    }
}

