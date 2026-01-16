package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired after a player has successfully logged in and authenticated.
 * At this point, the player's UUID and name are known.
 *
 * <p>This event is not cancellable. To prevent a player from logging in,
 * use {@link PreLoginEvent} or {@link LoginEvent}.</p>
 */
public class PostLoginEvent {

    private final Player player;

    public PostLoginEvent(@Nonnull Player player) {
        this.player = player;
    }

    /**
     * Get the player who logged in.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }
}

