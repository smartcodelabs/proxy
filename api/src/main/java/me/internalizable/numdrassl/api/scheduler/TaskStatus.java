package me.internalizable.numdrassl.api.scheduler;

/**
 * Status of a scheduled task.
 */
public enum TaskStatus {

    /**
     * The task is scheduled and waiting to run.
     */
    SCHEDULED,

    /**
     * The task is currently running.
     */
    RUNNING,

    /**
     * The task has completed.
     */
    FINISHED,

    /**
     * The task was cancelled.
     */
    CANCELLED
}

