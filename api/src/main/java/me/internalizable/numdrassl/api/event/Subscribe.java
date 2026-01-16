package me.internalizable.numdrassl.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler.
 *
 * <p>The method must have exactly one parameter, which is the event type to handle.
 * The method can be public, protected, or package-private.</p>
 *
 * <pre>{@code
 * public class MyListener {
 *     @Subscribe
 *     public void onPlayerConnect(PlayerConnectEvent event) {
 *         // Handle event
 *     }
 *
 *     @Subscribe(priority = EventPriority.EARLY)
 *     public void onPacket(PacketEvent event) {
 *         // Handle packet early
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /**
     * The priority of this event handler.
     * Handlers with lower priority values are called first.
     *
     * @return the handler priority
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Whether this handler should receive cancelled events.
     *
     * @return true to receive cancelled events
     */
    boolean ignoreCancelled() default false;
}

