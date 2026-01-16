package me.internalizable.numdrassl.api.scheduler;

import javax.annotation.Nonnull;

/**
 * Represents a scheduled task that can be cancelled.
 */
public interface ScheduledTask {

    /**
     * Get the plugin that scheduled this task.
     *
     * @return the owning plugin
     */
    @Nonnull
    Object getPlugin();

    /**
     * Get the current status of this task.
     *
     * @return the task status
     */
    @Nonnull
    TaskStatus getStatus();

    /**
     * Cancel this task.
     */
    void cancel();
}

