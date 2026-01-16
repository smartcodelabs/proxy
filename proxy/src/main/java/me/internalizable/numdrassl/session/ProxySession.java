package me.internalizable.numdrassl.session;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import me.internalizable.numdrassl.auth.CertificateExtractor;

/**
 * Represents a proxy session for a connected Hytale client.
 * Manages both the downstream (client) and upstream (backend server) connections.
 */
public class ProxySession {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxySession.class);
    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);

    private final long sessionId;
    private final ProxyServer proxyServer;
    private final QuicChannel clientChannel;
    private final InetSocketAddress clientAddress;

    // Client's TLS certificate (extracted from mTLS handshake)
    private volatile X509Certificate clientCertificate;
    private volatile String clientCertificateFingerprint;

    // Authentication state
    private volatile com.hypixel.hytale.protocol.packets.auth.AuthGrant authGrant;

    // Proxy authentication - when enabled, we replace client's AuthToken with proxy's tokens
    private volatile boolean proxyAuthEnabled = false;
    private volatile String proxyAccessToken;
    private volatile String proxyServerAuthGrant;

    // Pending ServerAuthToken - held until client sends AuthToken to keep protocol in sync
    private volatile com.hypixel.hytale.protocol.packets.auth.ServerAuthToken pendingServerAuthToken;

    // Flag to indicate client has sent AuthToken (for timing synchronization)
    private volatile boolean clientAuthTokenReceived = false;

    // Flag to indicate client auth flow is complete (ServerAuthToken sent to client)
    private volatile boolean clientAuthComplete = false;

    // Flag to indicate this is a server transfer (no need to re-auth client)
    private volatile boolean isServerTransfer = false;

    // Buffer for backend packets while client auth is pending
    private final java.util.Queue<Object> pendingBackendPackets = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.HANDSHAKING);
    private final AtomicReference<QuicChannel> backendChannel = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> clientStream = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> backendStream = new AtomicReference<>();

    // Player info (populated after Connect packet)
    private volatile UUID playerUuid;
    private volatile String playerName;
    private volatile String protocolHash;
    private volatile String clientIdentityToken; // Client's identity token from Connect packet
    private volatile BackendServer currentBackend;

    public ProxySession(@Nonnull ProxyServer proxyServer, @Nonnull QuicChannel clientChannel) {
        this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
        this.proxyServer = proxyServer;
        this.clientChannel = clientChannel;
        // QUIC channels may return QuicConnectionAddress, extract the underlying address
        java.net.SocketAddress remoteAddr = clientChannel.remoteAddress();
        if (remoteAddr instanceof InetSocketAddress) {
            this.clientAddress = (InetSocketAddress) remoteAddr;
        } else {
            // Fallback for QuicConnectionAddress - create a placeholder
            this.clientAddress = new InetSocketAddress("0.0.0.0", 0);
        }

        // Extract client certificate from mTLS handshake
        this.clientCertificate = CertificateExtractor.extractClientCertificate(clientChannel);
        if (this.clientCertificate != null) {
            this.clientCertificateFingerprint = CertificateExtractor.computeCertificateFingerprint(this.clientCertificate);
            LOGGER.info("Session {}: Client certificate fingerprint: {}", sessionId, clientCertificateFingerprint);
            LOGGER.info("Session {}: Client certificate subject: {}", sessionId, clientCertificate.getSubjectX500Principal().getName());
        } else {
            LOGGER.warn("Session {}: No client certificate available from mTLS handshake", sessionId);
        }
    }

    @Nullable
    public X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    @Nullable
    public String getClientCertificateFingerprint() {
        return clientCertificateFingerprint;
    }

    @Nullable
    public com.hypixel.hytale.protocol.packets.auth.AuthGrant getAuthGrant() {
        return authGrant;
    }

    public void setAuthGrant(@Nullable com.hypixel.hytale.protocol.packets.auth.AuthGrant authGrant) {
        this.authGrant = authGrant;
    }

    public boolean isProxyAuthEnabled() {
        return proxyAuthEnabled;
    }

    public void setProxyAuthEnabled(boolean enabled) {
        this.proxyAuthEnabled = enabled;
    }

    @Nullable
    public String getProxyAccessToken() {
        return proxyAccessToken;
    }

    public void setProxyAccessToken(@Nullable String token) {
        this.proxyAccessToken = token;
    }

    @Nullable
    public String getProxyServerAuthGrant() {
        return proxyServerAuthGrant;
    }

    public void setProxyServerAuthGrant(@Nullable String grant) {
        this.proxyServerAuthGrant = grant;
    }

    @Nullable
    public com.hypixel.hytale.protocol.packets.auth.ServerAuthToken getPendingServerAuthToken() {
        return pendingServerAuthToken;
    }

    public void setPendingServerAuthToken(@Nullable com.hypixel.hytale.protocol.packets.auth.ServerAuthToken token) {
        this.pendingServerAuthToken = token;
    }

    public boolean isClientAuthTokenReceived() {
        return clientAuthTokenReceived;
    }

    public void setClientAuthTokenReceived(boolean received) {
        this.clientAuthTokenReceived = received;
    }

    public boolean isClientAuthComplete() {
        return clientAuthComplete;
    }

    public void setClientAuthComplete(boolean complete) {
        this.clientAuthComplete = complete;
    }

    public boolean isServerTransfer() {
        return isServerTransfer;
    }

    public void setServerTransfer(boolean serverTransfer) {
        this.isServerTransfer = serverTransfer;
    }

    /**
     * Queue a backend packet to be sent later when client auth is complete.
     */
    public void queueBackendPacket(Object packet) {
        pendingBackendPackets.add(packet);
        LOGGER.warn("Added packet to pending backend queue for session {}. Queue size: {}", sessionId, pendingBackendPackets.size());
    }

    /**
     * Flush all pending backend packets to the client.
     * Must be called after client auth is complete.
     */
    public void flushPendingBackendPackets() {
        QuicStreamChannel stream = clientStream.get();
        if (stream == null || !stream.isActive()) {
            LOGGER.warn("Session {}: Cannot flush pending packets - client stream not active", sessionId);
            // Release all buffered packets
            Object packet;
            while ((packet = pendingBackendPackets.poll()) != null) {
                if (packet instanceof io.netty.buffer.ByteBuf) {
                    ((io.netty.buffer.ByteBuf) packet).release();
                }
            }
            return;
        }

        // Execute on the client stream's event loop to avoid threading issues
        if (stream.eventLoop().inEventLoop()) {
            doFlushPendingPackets(stream);
        } else {
            stream.eventLoop().execute(() -> doFlushPendingPackets(stream));
        }
    }

    private void doFlushPendingPackets(QuicStreamChannel stream) {
        int count = pendingBackendPackets.size();
        if (count > 0) {
            LOGGER.info("Session {}: Flushing {} pending backend packets to client", sessionId, count);
        }
        Object packet;
        while ((packet = pendingBackendPackets.poll()) != null) {
            if (stream.isActive()) {
                stream.writeAndFlush(packet);
            } else {
                LOGGER.warn("Session {}: Client stream closed during flush", sessionId);
                // Release remaining packets
                if (packet instanceof io.netty.buffer.ByteBuf) {
                    ((io.netty.buffer.ByteBuf) packet).release();
                }
                while ((packet = pendingBackendPackets.poll()) != null) {
                    if (packet instanceof io.netty.buffer.ByteBuf) {
                        ((io.netty.buffer.ByteBuf) packet).release();
                    }
                }
                break;
            }
        }
    }

    /**
     * Check if there are pending backend packets.
     */
    public boolean hasPendingBackendPackets() {
        return !pendingBackendPackets.isEmpty();
    }

    public long getSessionId() {
        return sessionId;
    }

    @Nonnull
    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    @Nonnull
    public QuicChannel getClientChannel() {
        return clientChannel;
    }

    @Nonnull
    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    @Nonnull
    public SessionState getState() {
        return state.get();
    }

    public void setState(@Nonnull SessionState newState) {
        SessionState oldState = state.getAndSet(newState);
        LOGGER.debug("Session {} state changed: {} -> {}", sessionId, oldState, newState);
    }

    @Nullable
    public QuicChannel getBackendChannel() {
        return backendChannel.get();
    }

    public void setBackendChannel(@Nullable QuicChannel channel) {
        backendChannel.set(channel);
    }

    @Nullable
    public QuicStreamChannel getClientStream() {
        return clientStream.get();
    }

    public void setClientStream(@Nullable QuicStreamChannel stream) {
        clientStream.set(stream);
    }

    @Nullable
    public QuicStreamChannel getBackendStream() {
        return backendStream.get();
    }

    public void setBackendStream(@Nullable QuicStreamChannel stream) {
        backendStream.set(stream);
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getProtocolHash() {
        return protocolHash;
    }

    @Nullable
    public String getClientIdentityToken() {
        return clientIdentityToken;
    }

    public void setClientIdentityToken(@Nullable String token) {
        this.clientIdentityToken = token;
    }

    @Nullable
    public BackendServer getCurrentBackend() {
        return currentBackend;
    }

    public void setCurrentBackend(@Nullable BackendServer backend) {
        this.currentBackend = backend;
    }

    /**
     * Update session info from a Connect packet
     */
    public void handleConnectPacket(@Nonnull Connect connect) {
        this.playerUuid = connect.uuid;
        this.playerName = connect.username;
        this.protocolHash = connect.protocolHash;
        this.clientIdentityToken = connect.identityToken;
        LOGGER.info("Session {} identified: {} ({})", sessionId, playerName, playerUuid);
    }

    /**
     * Send a packet to the connected client.
     * Thread-safe: will execute on the client stream's event loop.
     */
    public void sendToClient(@Nonnull Packet packet) {
        QuicStreamChannel stream = clientStream.get();
        if (stream != null && stream.isActive()) {
            // Execute on the correct event loop to avoid threading issues
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(packet);
            } else {
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(packet);
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to client - stream not active", sessionId);
        }
    }

    /**
     * Send an arbitrary object (Packet or ByteBuf) to the client.
     * Thread-safe: will execute on the client stream's event loop.
     */
    public void sendToClient(@Nonnull Object obj) {
        if (obj instanceof Packet) {
            sendToClient((Packet) obj);
        } else {
            // Assume it's a ByteBuf - send directly
            QuicStreamChannel stream = clientStream.get();
            if (stream != null && stream.isActive()) {
                // Execute on the correct event loop to avoid threading issues
                if (stream.eventLoop().inEventLoop()) {
                    stream.writeAndFlush(obj);
                } else {
                    stream.eventLoop().execute(() -> {
                        if (stream.isActive()) {
                            stream.writeAndFlush(obj);
                        } else {
                            // Release if it's a ByteBuf
                            if (obj instanceof io.netty.buffer.ByteBuf) {
                                ((io.netty.buffer.ByteBuf) obj).release();
                            }
                        }
                    });
                }
            } else {
                LOGGER.warn("Session {}: Cannot send to client - stream not active", sessionId);
                // Release if it's a ByteBuf
                if (obj instanceof io.netty.buffer.ByteBuf) {
                    ((io.netty.buffer.ByteBuf) obj).release();
                }
            }
        }
    }

    /**
     * Send a packet to the backend server.
     * Thread-safe: will execute on the backend stream's event loop.
     */
    public void sendToBackend(@Nonnull Packet packet) {
        QuicStreamChannel stream = backendStream.get();
        if (stream != null && stream.isActive()) {
            // Execute on the correct event loop to avoid threading issues
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(packet);
            } else {
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(packet);
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to backend - stream not active", sessionId);
        }
    }

    /**
     * Send an arbitrary object (Packet or ByteBuf) to the backend server.
     * Thread-safe: will execute on the backend stream's event loop.
     */
    public void sendToBackend(@Nonnull Object obj) {
        if (obj instanceof Packet) {
            sendToBackend((Packet) obj);
        } else {
            // Assume it's a ByteBuf - send directly
            QuicStreamChannel stream = backendStream.get();
            if (stream != null && stream.isActive()) {
                // Execute on the correct event loop to avoid threading issues
                if (stream.eventLoop().inEventLoop()) {
                    stream.writeAndFlush(obj);
                } else {
                    stream.eventLoop().execute(() -> {
                        if (stream.isActive()) {
                            stream.writeAndFlush(obj);
                        } else {
                            // Release if it's a ByteBuf
                            if (obj instanceof io.netty.buffer.ByteBuf) {
                                ((io.netty.buffer.ByteBuf) obj).release();
                            }
                        }
                    });
                }
            } else {
                LOGGER.warn("Session {}: Cannot send to backend - stream not active", sessionId);
                // Release if it's a ByteBuf
                if (obj instanceof io.netty.buffer.ByteBuf) {
                    ((io.netty.buffer.ByteBuf) obj).release();
                }
            }
        }
    }

    /**
     * Disconnect the client with a reason
     */
    public void disconnect(@Nonnull String reason) {
        LOGGER.info("Session {} disconnecting: {}", sessionId, reason);
        state.set(SessionState.DISCONNECTED);

        // Close backend connection first
        QuicChannel backend = backendChannel.get();
        if (backend != null && backend.isActive()) {
            backend.close();
        }

        // Then close client connection
        if (clientChannel.isActive()) {
            clientChannel.close();
        }

        proxyServer.getSessionManager().removeSession(this);
    }

    /**
     * Close all connections for this session
     */
    public void close() {
        state.set(SessionState.DISCONNECTED);

        QuicStreamChannel cs = clientStream.get();
        if (cs != null && cs.isActive()) {
            cs.close();
        }

        QuicStreamChannel bs = backendStream.get();
        if (bs != null && bs.isActive()) {
            bs.close();
        }

        QuicChannel bc = backendChannel.get();
        if (bc != null && bc.isActive()) {
            bc.close();
        }

        if (clientChannel.isActive()) {
            clientChannel.close();
        }
    }

    /**
     * Switch this session to a different backend server.
     * This disconnects from the current backend and connects to the new one.
     *
     * @param newBackend The backend server to switch to
     * @return true if the switch was initiated successfully
     */
    public boolean switchToServer(@Nonnull BackendServer newBackend) {
        SessionState currentState = state.get();
        if (currentState != SessionState.CONNECTED) {
            LOGGER.warn("Session {}: Cannot switch servers - not in CONNECTED state (current: {})",
                sessionId, currentState);
            return false;
        }

        if (currentBackend != null && currentBackend.getName().equalsIgnoreCase(newBackend.getName())) {
            LOGGER.warn("Session {}: Already connected to server {}", sessionId, newBackend.getName());
            return false;
        }

        LOGGER.info("Session {}: Initiating server switch from {} to {}",
            sessionId, currentBackend != null ? currentBackend.getName() : "none", newBackend.getName());

        // Set state to transferring
        setState(SessionState.TRANSFERRING);

        // Mark this as a server transfer - client is already authenticated
        this.isServerTransfer = true;

        // Reset authentication state for the new BACKEND connection only
        // Client auth state is preserved (they're already authenticated)
        resetAuthStateForTransfer();

        // Close the current backend connection
        closeBackendConnection();

        // Create a new Connect packet with the player's info
        Connect connectPacket = new Connect();
        connectPacket.uuid = playerUuid;
        connectPacket.username = playerName;
        connectPacket.protocolHash = protocolHash;
        connectPacket.identityToken = clientIdentityToken;

        // Initiate connection to the new backend
        proxyServer.getBackendConnector().reconnect(this, newBackend, connectPacket);

        return true;
    }

    /**
     * Reset authentication state for a server transfer (backend auth only).
     * Preserves client auth state since they're already authenticated.
     */
    private void resetAuthStateForTransfer() {
        this.authGrant = null;
        this.proxyAuthEnabled = false;
        this.proxyAccessToken = null;
        this.proxyServerAuthGrant = null;
        this.pendingServerAuthToken = null;
        // DO NOT reset clientAuthTokenReceived or clientAuthComplete - client is already authenticated
        // Clear any pending packets from the old backend
        pendingBackendPackets.clear();
    }

    /**
     * Close the backend connection without disconnecting the client
     */
    private void closeBackendConnection() {
        QuicStreamChannel bs = backendStream.get();
        if (bs != null && bs.isActive()) {
            bs.close();
        }
        backendStream.set(null);

        QuicChannel bc = backendChannel.get();
        if (bc != null && bc.isActive()) {
            bc.close();
        }
        backendChannel.set(null);
    }

    @Override
    public String toString() {
        return "ProxySession{" +
            "id=" + sessionId +
            ", player=" + playerName +
            ", uuid=" + playerUuid +
            ", state=" + state.get() +
            ", clientAddress=" + clientAddress +
            '}';
    }
}

