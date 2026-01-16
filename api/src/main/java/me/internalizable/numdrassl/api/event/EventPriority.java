package me.internalizable.numdrassl.api.event;

/**
 * Priority levels for event handlers.
 * Handlers with lower priority values are called first.
 */
public enum EventPriority {

    /**
     * Handlers that should run first, typically for monitoring or logging.
     */
    FIRST(-100),

    /**
     * Handlers that run early, before most modifications.
     */
    EARLY(-50),

    /**
     * The default priority for most handlers.
     */
    NORMAL(0),

    /**
     * Handlers that run late, after most modifications.
     */
    LATE(50),

    /**
     * Handlers that should run last, typically for final decisions.
     */
    LAST(100);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    /**
     * Get the numeric priority value.
     *
     * @return the priority value (lower = earlier)
     */
    public int getValue() {
        return value;
    }
}

