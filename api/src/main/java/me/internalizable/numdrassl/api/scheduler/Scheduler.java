package me.internalizable.numdrassl.api.scheduler;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for running tasks asynchronously or with delays.
 */
public interface Scheduler {

    /**
     * Create a new task builder.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @return a task builder
     */
    @Nonnull
    TaskBuilder buildTask(@Nonnull Object plugin, @Nonnull Runnable task);

    /**
     * Run a task immediately on the async thread pool.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @return the scheduled task
     */
    @Nonnull
    ScheduledTask runAsync(@Nonnull Object plugin, @Nonnull Runnable task);

    /**
     * Run a task after a delay.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @param delay the delay before running
     * @param unit the time unit of the delay
     * @return the scheduled task
     */
    @Nonnull
    ScheduledTask runLater(@Nonnull Object plugin, @Nonnull Runnable task, long delay, @Nonnull TimeUnit unit);

    /**
     * Run a task repeatedly at a fixed rate.
     *
     * @param plugin the plugin scheduling the task
     * @param task the task to run
     * @param initialDelay the delay before the first run
     * @param period the period between runs
     * @param unit the time unit
     * @return the scheduled task
     */
    @Nonnull
    ScheduledTask runRepeating(@Nonnull Object plugin, @Nonnull Runnable task,
                                long initialDelay, long period, @Nonnull TimeUnit unit);

    /**
     * Cancel all tasks scheduled by a plugin.
     *
     * @param plugin the plugin whose tasks should be cancelled
     */
    void cancelAll(@Nonnull Object plugin);
}

