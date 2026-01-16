package me.internalizable.numdrassl.api.command;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Represents an executable command.
 */
public interface Command {

    /**
     * Get the primary name of this command.
     *
     * @return the command name
     */
    @Nonnull
    String getName();

    /**
     * Get the permission required to execute this command.
     *
     * @return the permission, or null if no permission is required
     */
    default String getPermission() {
        return null;
    }

    /**
     * Get the usage string for this command.
     *
     * @return the usage string
     */
    default String getUsage() {
        return "/" + getName();
    }

    /**
     * Get the description of this command.
     *
     * @return the description
     */
    default String getDescription() {
        return "";
    }

    /**
     * Execute this command.
     *
     * @param source the command source
     * @param args the arguments passed to the command
     * @return the result of the command
     */
    @Nonnull
    CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args);

    /**
     * Get tab completions for this command.
     *
     * @param source the command source
     * @param args the current arguments
     * @return a list of possible completions
     */
    @Nonnull
    default List<String> suggest(@Nonnull CommandSource source, @Nonnull String[] args) {
        return Collections.emptyList();
    }

    /**
     * Check if the source has permission to execute this command.
     *
     * @param source the command source
     * @return true if the source can execute this command
     */
    default boolean hasPermission(@Nonnull CommandSource source) {
        String permission = getPermission();
        return permission == null || source.hasPermission(permission);
    }
}

