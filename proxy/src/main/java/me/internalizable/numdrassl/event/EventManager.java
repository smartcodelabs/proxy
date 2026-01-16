package me.internalizable.numdrassl.event;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages packet event listeners and dispatches events to them
 */
public class EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

    private final List<PacketListener> listeners = new CopyOnWriteArrayList<>();

    public void registerListener(@Nonnull PacketListener listener) {
        listeners.add(listener);
        LOGGER.info("Registered packet listener: {}", listener.getClass().getSimpleName());
    }

    public void unregisterListener(@Nonnull PacketListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    /**
     * Dispatch a client-to-server packet through all listeners
     *
     * @return The final packet to forward, or null if cancelled
     */
    @Nullable
    public <T extends Packet> T dispatchClientPacket(@Nonnull ProxySession session, @Nonnull T packet) {
        PacketEvent<T> event = new PacketEvent<>(session, PacketDirection.CLIENT_TO_SERVER, packet);

        for (PacketListener listener : listeners) {
            try {
                T result = listener.onClientPacket(event);
                if (result == null || event.isCancelled()) {
                    return null;
                }
                event.setPacket(result);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} processing client packet",
                    listener.getClass().getSimpleName(), e);
            }
        }

        return event.getPacket();
    }

    /**
     * Dispatch a server-to-client packet through all listeners
     *
     * @return The final packet to forward, or null if cancelled
     */
    @Nullable
    public <T extends Packet> T dispatchServerPacket(@Nonnull ProxySession session, @Nonnull T packet) {
        PacketEvent<T> event = new PacketEvent<>(session, PacketDirection.SERVER_TO_CLIENT, packet);

        for (PacketListener listener : listeners) {
            try {
                T result = listener.onServerPacket(event);
                if (result == null || event.isCancelled()) {
                    return null;
                }
                event.setPacket(result);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} processing server packet",
                    listener.getClass().getSimpleName(), e);
            }
        }

        return event.getPacket();
    }

    /**
     * Notify all listeners of a new session
     */
    public void dispatchSessionCreated(@Nonnull ProxySession session) {
        for (PacketListener listener : listeners) {
            try {
                listener.onSessionCreated(session);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} handling session created",
                    listener.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Notify all listeners of a closed session
     */
    public void dispatchSessionClosed(@Nonnull ProxySession session) {
        for (PacketListener listener : listeners) {
            try {
                listener.onSessionClosed(session);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} handling session closed",
                    listener.getClass().getSimpleName(), e);
            }
        }
    }
}

