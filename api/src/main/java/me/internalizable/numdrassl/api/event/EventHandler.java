package me.internalizable.numdrassl.api.event;

/**
 * A handler for a specific event type.
 *
 * @param <E> the event type this handler processes
 */
@FunctionalInterface
public interface EventHandler<E> {

    /**
     * Handle the event.
     *
     * @param event the event to handle
     */
    void handle(E event);
}

