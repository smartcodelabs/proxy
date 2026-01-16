package me.internalizable.numdrassl.session;

import io.netty.incubator.codec.quic.QuicChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active proxy sessions
 */
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    private final Map<Long, ProxySession> sessionsById = new ConcurrentHashMap<>();
    private final Map<QuicChannel, ProxySession> sessionsByChannel = new ConcurrentHashMap<>();
    private final Map<UUID, ProxySession> sessionsByUuid = new ConcurrentHashMap<>();

    /**
     * Register a new session
     */
    public void addSession(@Nonnull ProxySession session) {
        sessionsById.put(session.getSessionId(), session);
        sessionsByChannel.put(session.getClientChannel(), session);
        LOGGER.info("Session registered: {}", session.getSessionId());
    }

    /**
     * Update session UUID mapping (called after backend connection is established)
     * Only kicks existing session once the new session is fully connected to backend
     */
    public void registerPlayerUuid(@Nonnull ProxySession session, boolean kickExisting) {
        UUID uuid = session.getPlayerUuid();
        if (uuid != null) {
            if (kickExisting) {
                // Disconnect existing session with same UUID
                ProxySession existing = sessionsByUuid.get(uuid);
                if (existing != null && existing != session) {
                    LOGGER.info("Disconnecting existing session for UUID: {}", uuid);
                    existing.disconnect("Another connection with same account");
                }
            }
            sessionsByUuid.put(uuid, session);
        }
    }

    /**
     * Update session UUID mapping (called after Connect packet, doesn't kick yet)
     */
    public void registerPlayerUuid(@Nonnull ProxySession session) {
        registerPlayerUuid(session, false);
    }

    /**
     * Remove a session
     */
    public void removeSession(@Nonnull ProxySession session) {
        sessionsById.remove(session.getSessionId());
        sessionsByChannel.remove(session.getClientChannel());
        UUID uuid = session.getPlayerUuid();
        if (uuid != null) {
            sessionsByUuid.remove(uuid, session);
        }
        LOGGER.info("Session removed: {}", session.getSessionId());
    }

    /**
     * Get session by ID
     */
    @Nullable
    public ProxySession getSession(long sessionId) {
        return sessionsById.get(sessionId);
    }

    /**
     * Get session by QUIC channel
     */
    @Nullable
    public ProxySession getSession(@Nonnull QuicChannel channel) {
        return sessionsByChannel.get(channel);
    }

    /**
     * Get session by player UUID
     */
    @Nullable
    public ProxySession getSession(@Nonnull UUID uuid) {
        return sessionsByUuid.get(uuid);
    }

    /**
     * Get all active sessions
     */
    @Nonnull
    public Collection<ProxySession> getAllSessions() {
        return sessionsById.values();
    }

    /**
     * Get the number of active sessions
     */
    public int getSessionCount() {
        return sessionsById.size();
    }

    /**
     * Close all sessions
     */
    public void closeAll() {
        LOGGER.info("Closing all {} sessions", sessionsById.size());
        for (ProxySession session : sessionsById.values()) {
            try {
                session.close();
            } catch (Exception e) {
                LOGGER.error("Error closing session {}", session.getSessionId(), e);
            }
        }
        sessionsById.clear();
        sessionsByChannel.clear();
        sessionsByUuid.clear();
    }
}

