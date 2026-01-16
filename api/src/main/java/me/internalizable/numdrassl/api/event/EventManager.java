package me.internalizable.numdrassl.api.event;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Manages event registration and dispatching for the proxy.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Register a listener
 * eventManager.register(plugin, new MyListener());
 *
 * // Unregister all listeners for a plugin
 * eventManager.unregisterAll(plugin);
 *
 * // Fire an event
 * eventManager.fire(new MyEvent()).thenAccept(event -> {
 *     // Event has been processed by all listeners
 * });
 * }</pre>
 */
public interface EventManager {

    /**
     * Register an event listener object.
     * All methods annotated with {@link Subscribe} will be registered as listeners.
     *
     * @param plugin the plugin registering the listener
     * @param listener the listener object
     */
    void register(@Nonnull Object plugin, @Nonnull Object listener);

    /**
     * Register a single event handler.
     *
     * @param plugin the plugin registering the handler
     * @param eventClass the event class to listen for
     * @param handler the handler to call when the event fires
     * @param <E> the event type
     */
    <E> void register(@Nonnull Object plugin, @Nonnull Class<E> eventClass, @Nonnull EventHandler<E> handler);

    /**
     * Register a single event handler with a specific priority.
     *
     * @param plugin the plugin registering the handler
     * @param eventClass the event class to listen for
     * @param priority the priority of this handler
     * @param handler the handler to call when the event fires
     * @param <E> the event type
     */
    <E> void register(@Nonnull Object plugin, @Nonnull Class<E> eventClass,
                      @Nonnull EventPriority priority, @Nonnull EventHandler<E> handler);

    /**
     * Unregister a specific listener object.
     *
     * @param listener the listener to unregister
     */
    void unregister(@Nonnull Object listener);

    /**
     * Unregister all listeners registered by a plugin.
     *
     * @param plugin the plugin whose listeners should be unregistered
     */
    void unregisterAll(@Nonnull Object plugin);

    /**
     * Fire an event and return a future that completes when all handlers have processed it.
     *
     * @param event the event to fire
     * @param <E> the event type
     * @return a future that completes with the event after all handlers have processed it
     */
    @Nonnull
    <E> CompletableFuture<E> fire(@Nonnull E event);

    /**
     * Fire an event synchronously, blocking until all handlers have processed it.
     * Use this only when you know all handlers will complete synchronously.
     *
     * @param event the event to fire
     * @param <E> the event type
     * @return the event after all handlers have processed it
     */
    @Nonnull
    <E> E fireSync(@Nonnull E event);
}

