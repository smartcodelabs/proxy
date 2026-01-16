package me.internalizable.numdrassl.api.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the result of a command execution.
 */
public final class CommandResult {

    private static final CommandResult SUCCESS = new CommandResult(Status.SUCCESS, null);
    private static final CommandResult FAILURE = new CommandResult(Status.FAILURE, null);
    private static final CommandResult NOT_FOUND = new CommandResult(Status.NOT_FOUND, "Command not found");
    private static final CommandResult NO_PERMISSION = new CommandResult(Status.NO_PERMISSION, "You don't have permission");

    private final Status status;
    private final String message;

    private CommandResult(@Nonnull Status status, @Nullable String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * Get the status of this result.
     *
     * @return the status
     */
    @Nonnull
    public Status getStatus() {
        return status;
    }

    /**
     * Get the message associated with this result.
     *
     * @return the message, or null
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * Check if the command was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * A successful command result.
     *
     * @return success result
     */
    public static CommandResult success() {
        return SUCCESS;
    }

    /**
     * A successful command result with a message.
     *
     * @param message the success message
     * @return success result
     */
    public static CommandResult success(@Nonnull String message) {
        return new CommandResult(Status.SUCCESS, message);
    }

    /**
     * A failed command result.
     *
     * @return failure result
     */
    public static CommandResult failure() {
        return FAILURE;
    }

    /**
     * A failed command result with a message.
     *
     * @param message the error message
     * @return failure result
     */
    public static CommandResult failure(@Nonnull String message) {
        return new CommandResult(Status.FAILURE, message);
    }

    /**
     * A result indicating the command was not found.
     *
     * @return not found result
     */
    public static CommandResult notFound() {
        return NOT_FOUND;
    }

    /**
     * A result indicating insufficient permissions.
     *
     * @return no permission result
     */
    public static CommandResult noPermission() {
        return NO_PERMISSION;
    }

    /**
     * Status of a command result.
     */
    public enum Status {
        SUCCESS,
        FAILURE,
        NOT_FOUND,
        NO_PERMISSION
    }
}

