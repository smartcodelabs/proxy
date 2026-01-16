package me.internalizable.numdrassl.api.event;

/**
 * Base interface for cancellable events.
 * When an event is cancelled, subsequent handlers may choose to ignore it
 * (based on their {@link Subscribe#ignoreCancelled()} setting), and the
 * default action associated with the event will not occur.
 */
public interface Cancellable {

    /**
     * Check if this event has been cancelled.
     *
     * @return true if the event is cancelled
     */
    boolean isCancelled();

    /**
     * Set whether this event is cancelled.
     *
     * @param cancelled true to cancel the event
     */
    void setCancelled(boolean cancelled);
}

