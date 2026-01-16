package me.internalizable.numdrassl.api.scheduler;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * Builder for configuring and scheduling tasks.
 */
public interface TaskBuilder {

    /**
     * Set the delay before the task runs.
     *
     * @param delay the delay
     * @param unit the time unit
     * @return this builder
     */
    @Nonnull
    TaskBuilder delay(long delay, @Nonnull TimeUnit unit);

    /**
     * Set the task to repeat at a fixed rate.
     *
     * @param period the period between runs
     * @param unit the time unit
     * @return this builder
     */
    @Nonnull
    TaskBuilder repeat(long period, @Nonnull TimeUnit unit);

    /**
     * Clear any scheduled repeat for this task.
     *
     * @return this builder
     */
    @Nonnull
    TaskBuilder clearRepeat();

    /**
     * Schedule the task.
     *
     * @return the scheduled task
     */
    @Nonnull
    ScheduledTask schedule();
}

