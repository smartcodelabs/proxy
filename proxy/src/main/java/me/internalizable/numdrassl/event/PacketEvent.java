package me.internalizable.numdrassl.event;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;

/**
 * Represents a packet event that can be intercepted and modified
 */
public class PacketEvent<T extends Packet> {

    private final ProxySession session;
    private final PacketDirection direction;
    private T packet;
    private boolean cancelled;

    public PacketEvent(@Nonnull ProxySession session, @Nonnull PacketDirection direction, @Nonnull T packet) {
        this.session = session;
        this.direction = direction;
        this.packet = packet;
        this.cancelled = false;
    }

    @Nonnull
    public ProxySession getSession() {
        return session;
    }

    @Nonnull
    public PacketDirection getDirection() {
        return direction;
    }

    @Nonnull
    public T getPacket() {
        return packet;
    }

    public void setPacket(@Nonnull T packet) {
        this.packet = packet;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * @return true if this packet is traveling from client to server
     */
    public boolean isClientToServer() {
        return direction == PacketDirection.CLIENT_TO_SERVER;
    }

    /**
     * @return true if this packet is traveling from server to client
     */
    public boolean isServerToClient() {
        return direction == PacketDirection.SERVER_TO_CLIENT;
    }
}

