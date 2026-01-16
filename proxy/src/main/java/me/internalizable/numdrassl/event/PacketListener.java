package me.internalizable.numdrassl.event;

import com.hypixel.hytale.protocol.Packet;

import javax.annotation.Nonnull;

/**
 * Listener interface for packet events.
 * Implement this to intercept, modify, or cancel packets flowing through the proxy.
 */
public interface PacketListener {

    /**
     * Called when a packet is received from a client heading to the backend server.
     *
     * @param event The packet event containing the packet and session info
     * @return The packet to forward (may be modified), or null to cancel forwarding
     */
    @SuppressWarnings("unchecked")
    default <T extends Packet> T onClientPacket(@Nonnull PacketEvent<T> event) {
        return event.isCancelled() ? null : event.getPacket();
    }

    /**
     * Called when a packet is received from the backend server heading to the client.
     *
     * @param event The packet event containing the packet and session info
     * @return The packet to forward (may be modified), or null to cancel forwarding
     */
    @SuppressWarnings("unchecked")
    default <T extends Packet> T onServerPacket(@Nonnull PacketEvent<T> event) {
        return event.isCancelled() ? null : event.getPacket();
    }

    /**
     * Called when a new session is established (client connected)
     */
    default void onSessionCreated(@Nonnull me.internalizable.numdrassl.session.ProxySession session) {
    }

    /**
     * Called when a session is closed
     */
    default void onSessionClosed(@Nonnull me.internalizable.numdrassl.session.ProxySession session) {
    }
}

