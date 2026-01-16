package me.internalizable.numdrassl.api;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.EventManager;
import me.internalizable.numdrassl.event.PacketListener;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Public API for interacting with the Numdrassl proxy.
 * Use this class to:
 * - Register packet listeners/interceptors
 * - Access connected players
 * - Transfer players between servers
 * - Send packets to players or servers
 */
public final class ProxyAPI {

    private static ProxyServer server;

    private ProxyAPI() {}

    /**
     * Initialize the API with the proxy server instance.
     * Called internally by the proxy.
     */
    public static void init(@Nonnull ProxyServer proxyServer) {
        server = proxyServer;
    }

    private static ProxyServer getServer() {
        if (server == null) {
            throw new IllegalStateException("ProxyAPI not initialized. Is the proxy running?");
        }
        return server;
    }

    // =========================================
    // Event System
    // =========================================

    /**
     * Register a packet listener to intercept and modify packets
     */
    public static void registerListener(@Nonnull PacketListener listener) {
        getServer().getEventManager().registerListener(listener);
    }

    /**
     * Unregister a previously registered packet listener
     */
    public static void unregisterListener(@Nonnull PacketListener listener) {
        getServer().getEventManager().unregisterListener(listener);
    }

    // =========================================
    // Session/Player Management
    // =========================================

    /**
     * Get all connected player sessions
     */
    @Nonnull
    public static Collection<ProxySession> getAllSessions() {
        return getServer().getSessionManager().getAllSessions();
    }

    /**
     * Get a player session by UUID
     */
    @Nullable
    public static ProxySession getSession(@Nonnull UUID playerUuid) {
        return getServer().getSessionManager().getSession(playerUuid);
    }

    /**
     * Get the number of connected players
     */
    public static int getPlayerCount() {
        return getServer().getSessionManager().getSessionCount();
    }

    /**
     * Disconnect a player
     */
    public static void disconnect(@Nonnull ProxySession session, @Nonnull String reason) {
        session.disconnect(reason);
    }

    // =========================================
    // Packet Sending
    // =========================================

    /**
     * Send a packet to a player's client
     */
    public static void sendToClient(@Nonnull ProxySession session, @Nonnull Packet packet) {
        session.sendToClient(packet);
    }

    /**
     * Send a packet to the backend server on behalf of a player
     */
    public static void sendToBackend(@Nonnull ProxySession session, @Nonnull Packet packet) {
        session.sendToBackend(packet);
    }

    /**
     * Broadcast a packet to all connected players
     */
    public static void broadcast(@Nonnull Packet packet) {
        for (ProxySession session : getAllSessions()) {
            session.sendToClient(packet);
        }
    }

    // =========================================
    // Server Transfer
    // =========================================

    /**
     * Transfer a player to a different backend server by name
     */
    public static void transferPlayer(@Nonnull ProxySession session, @Nonnull String serverName) {
        BackendServer backend = getServer().getConfig().getBackendByName(serverName);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown server: " + serverName);
        }
        // Initiate transfer
        new me.internalizable.numdrassl.server.PlayerTransfer(getServer()).transfer(session, backend);
    }

    /**
     * Get all configured backend servers
     */
    @Nonnull
    public static java.util.List<BackendServer> getBackendServers() {
        return getServer().getConfig().getBackends();
    }

    /**
     * Get the default backend server
     */
    @Nullable
    public static BackendServer getDefaultBackend() {
        return getServer().getConfig().getDefaultBackend();
    }

    // =========================================
    // Proxy Information
    // =========================================

    /**
     * Check if the proxy is running
     */
    public static boolean isRunning() {
        return server != null && server.isRunning();
    }

    /**
     * Get the proxy configuration
     */
    @Nonnull
    public static ProxyConfig getConfig() {
        return getServer().getConfig();
    }
}

