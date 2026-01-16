package me.internalizable.numdrassl.api.command;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Represents a source that can execute commands.
 * This can be a player, the console, or another command source.
 */
public interface CommandSource {

    /**
     * Send a message to this command source.
     *
     * @param message the message to send
     */
    void sendMessage(@Nonnull String message);

    /**
     * Check if this source has a permission.
     *
     * @param permission the permission to check
     * @return true if the source has the permission
     */
    boolean hasPermission(@Nonnull String permission);

    /**
     * Get this source as a player, if applicable.
     *
     * @return the player, or empty if this is not a player
     */
    @Nonnull
    Optional<Player> asPlayer();

    /**
     * Check if this source is a player.
     *
     * @return true if this is a player
     */
    default boolean isPlayer() {
        return asPlayer().isPresent();
    }

    /**
     * Check if this source is the console.
     *
     * @return true if this is the console
     */
    boolean isConsole();
}

