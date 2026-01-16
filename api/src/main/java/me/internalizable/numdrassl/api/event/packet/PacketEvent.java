package me.internalizable.numdrassl.api.event.packet;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired when a packet is received by the proxy.
 * This event allows plugins to inspect, modify, or cancel packets flowing
 * between clients and backend servers.
 *
 * <p>The proxy intercepts all packets flowing in both directions, allowing
 * plugins to inspect, modify, or relay them as needed while keeping the
 * connection transparent to both endpoints.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Subscribe
 * public void onPacket(PacketEvent event) {
 *     Object packet = event.getPacket();
 *
 *     if (packet instanceof ChatMessage) {
 *         ChatMessage chat = (ChatMessage) packet;
 *         // Inspect or modify the chat message
 *         if (chat.getMessage().contains("bad word")) {
 *             event.setCancelled(true); // Block the packet
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>To replace a packet with a different one:</p>
 * <pre>{@code
 * @Subscribe
 * public void onPacket(PacketEvent event) {
 *     if (event.getPacket() instanceof SomePacket) {
 *         // Replace with a modified version
 *         event.setPacket(new SomePacket(modifiedData));
 *     }
 * }
 * }</pre>
 */
public class PacketEvent implements Cancellable {

    private final Player player;
    private final PacketDirection direction;
    private Object packet;
    private boolean cancelled;

    public PacketEvent(@Nonnull Player player, @Nonnull PacketDirection direction, @Nonnull Object packet) {
        this.player = player;
        this.direction = direction;
        this.packet = packet;
        this.cancelled = false;
    }

    /**
     * Get the player associated with this packet.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the direction of the packet.
     *
     * @return the packet direction
     */
    @Nonnull
    public PacketDirection getDirection() {
        return direction;
    }

    /**
     * Check if this packet is traveling from the client to the server.
     *
     * @return true if clientbound
     */
    public boolean isClientbound() {
        return direction == PacketDirection.CLIENTBOUND;
    }

    /**
     * Check if this packet is traveling from the server to the client.
     *
     * @return true if serverbound
     */
    public boolean isServerbound() {
        return direction == PacketDirection.SERVERBOUND;
    }

    /**
     * Get the packet being transmitted.
     *
     * @return the packet object
     */
    @Nonnull
    public Object getPacket() {
        return packet;
    }

    /**
     * Get the packet as a specific type.
     *
     * @param type the expected packet class
     * @param <T> the packet type
     * @return the packet, or null if it's not the expected type
     */
    @Nullable
    public <T> T getPacketAs(@Nonnull Class<T> type) {
        if (type.isInstance(packet)) {
            return type.cast(packet);
        }
        return null;
    }

    /**
     * Check if the packet is of a specific type.
     *
     * @param type the packet class to check
     * @return true if the packet is an instance of the type
     */
    public boolean isPacketType(@Nonnull Class<?> type) {
        return type.isInstance(packet);
    }

    /**
     * Replace the packet with a different one.
     *
     * @param packet the new packet
     */
    public void setPacket(@Nonnull Object packet) {
        this.packet = packet;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

