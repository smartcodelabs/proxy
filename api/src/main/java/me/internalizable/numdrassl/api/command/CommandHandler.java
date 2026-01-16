package me.internalizable.numdrassl.api.command;

import javax.annotation.Nonnull;

/**
 * Functional interface for simple command handlers.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Handle a command execution.
     *
     * @param source the command source
     * @param args the arguments passed to the command
     * @return the result of the command
     */
    @Nonnull
    CommandResult handle(@Nonnull CommandSource source, @Nonnull String[] args);
}

