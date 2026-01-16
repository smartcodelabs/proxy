package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Handles packets from the downstream Hytale client.
 * Intercepts, processes, and forwards packets to the backend server.
 */
public class ClientPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketHandler.class);

    private final ProxyServer proxyServer;
    private final ProxySession session;

    public ClientPacketHandler(@Nonnull ProxyServer proxyServer, @Nonnull ProxySession session) {
        this.proxyServer = proxyServer;
        this.session = session;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Handle raw ByteBuf - unknown packets forwarded directly
        if (msg instanceof ByteBuf) {
            ByteBuf raw = (ByteBuf) msg;
            if (session.getState() == SessionState.CONNECTED || session.getState() == SessionState.AUTHENTICATING) {
                // Use the thread-safe sendToBackend method
                session.sendToBackend(raw.retain());
            } else {
                raw.release();
            }
            return;
        }

        if (!(msg instanceof Packet)) {
            LOGGER.warn("Session {}: Received unknown message type from client: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }

        Packet packet = (Packet) msg;

        // Handle Connect packet specially
        if (packet instanceof Connect) {
            handleConnect((Connect) packet);
            return;
        }

        // Handle Disconnect packet
        if (packet instanceof Disconnect) {
            handleDisconnect((Disconnect) packet);
            return;
        }

        // Handle AuthToken - if proxy is authenticated, we're handling auth ourselves
        if (packet instanceof com.hypixel.hytale.protocol.packets.auth.AuthToken) {
            if (session.isProxyAuthEnabled()) {
                com.hypixel.hytale.protocol.packets.auth.AuthToken clientAuthToken =
                    (com.hypixel.hytale.protocol.packets.auth.AuthToken) packet;

                LOGGER.info("Session {}: Received client AuthToken - proxy handling auth exchange",
                    session.getSessionId());

                // Mark that we received client's AuthToken
                session.setClientAuthTokenReceived(true);

                // The client included a serverAuthorizationGrant that WE need to exchange
                // to prove our identity to the client
                String clientServerAuthGrant = clientAuthToken.serverAuthorizationGrant;

                if (clientServerAuthGrant != null && !clientServerAuthGrant.isEmpty()) {
                    var authenticator = proxyServer.getAuthenticator();
                    if (authenticator != null) {
                        // Exchange the client's grant for a token bound to the PROXY's certificate
                        LOGGER.info("Session {}: Exchanging client's serverAuthGrant for proxy-bound token",
                            session.getSessionId());

                        authenticator.exchangeAuthGrantForToken(clientServerAuthGrant)
                            .thenAccept(proxyAccessToken -> {
                                if (proxyAccessToken != null) {
                                    LOGGER.info("Session {}: Got proxy-bound token, sending ServerAuthToken to client",
                                        session.getSessionId());
                                    // Create OUR OWN ServerAuthToken with a token bound to the proxy's cert
                                    com.hypixel.hytale.protocol.packets.auth.ServerAuthToken proxyServerAuthToken =
                                        new com.hypixel.hytale.protocol.packets.auth.ServerAuthToken(proxyAccessToken, null);
                                    session.sendToClient(proxyServerAuthToken);
                                    LOGGER.info("Session {}: Sent ServerAuthToken to client - auth flow complete",
                                        session.getSessionId());

                                    // Mark client auth as complete and flush pending packets
                                    completeClientAuth();
                                } else {
                                    LOGGER.error("Session {}: Failed to exchange client's auth grant!",
                                        session.getSessionId());
                                    session.disconnect("Server authentication failed");
                                }
                            })
                            .exceptionally(ex -> {
                                LOGGER.error("Session {}: Error exchanging client's auth grant",
                                    session.getSessionId(), ex);
                                session.disconnect("Server authentication failed");
                                return null;
                            });
                    } else {
                        LOGGER.error("Session {}: No authenticator available!", session.getSessionId());
                        session.disconnect("Server authentication unavailable");
                    }
                } else {
                    // No server auth grant from client - just send pending ServerAuthToken if we have one
                    LOGGER.warn("Session {}: Client AuthToken has no serverAuthGrant, using pending token",
                        session.getSessionId());
                    var pendingToken = session.getPendingServerAuthToken();
                    if (pendingToken != null) {
                        session.setPendingServerAuthToken(null);
                        session.sendToClient(pendingToken);
                        // Mark client auth as complete and flush pending packets
                        completeClientAuth();
                    }
                }
                return;
            }
            // Otherwise fall through and forward
        }

        // For all other packets, dispatch through event system and forward
        if (session.getState() == SessionState.CONNECTED || session.getState() == SessionState.AUTHENTICATING) {
            Packet toForward = proxyServer.getEventManager().dispatchClientPacket(session, packet);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        } else {
            LOGGER.debug("Session {}: Dropping client packet {} - not connected (state={})",
                session.getSessionId(), packet.getClass().getSimpleName(), session.getState());
        }
    }

    private void handleConnect(Connect connect) {
        LOGGER.info("Session {}: Received Connect from {} ({})",
            session.getSessionId(), connect.username, connect.uuid);

        // Update session with player info
        session.handleConnectPacket(connect);
        session.setState(SessionState.CONNECTING);

        // Register UUID mapping
        proxyServer.getSessionManager().registerPlayerUuid(session);

        // Dispatch through event system (allows modification)
        Connect toForward = proxyServer.getEventManager().dispatchClientPacket(session, connect);
        if (toForward == null) {
            session.disconnect("Connection cancelled");
            return;
        }

        // Check if this is a referral (player being transferred to a new backend)
        BackendServer backend = null;
        if (connect.uuid != null) {
            backend = proxyServer.getReferralManager().consumeReferral(connect.uuid, connect.referralData);
            if (backend != null) {
                LOGGER.info("Session {}: Player {} is being transferred to backend {}",
                    session.getSessionId(), connect.username, backend.getName());
            }
        }

        // If no referral, use default backend
        if (backend == null) {
            backend = proxyServer.getConfig().getDefaultBackend();
        }

        if (backend == null) {
            LOGGER.error("Session {}: No default backend configured!", session.getSessionId());
            session.disconnect("No backend server available");
            return;
        }

        session.setCurrentBackend(backend);
        LOGGER.info("Session {}: Connecting to backend {}", session.getSessionId(), backend);

        // Initiate backend connection
        proxyServer.getBackendConnector().connect(session, backend, toForward);
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Client disconnecting", session.getSessionId());

        // Forward to backend if connected
        if (session.getState() == SessionState.CONNECTED) {
            Packet toForward = proxyServer.getEventManager().dispatchClientPacket(session, disconnect);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        }

        session.disconnect("Client disconnected");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Session {}: Client stream active", session.getSessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Client stream closed", session.getSessionId());
        session.close();
        proxyServer.getSessionManager().removeSession(session);
        proxyServer.getEventManager().dispatchSessionClosed(session);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in client handler", session.getSessionId(), cause);
        session.disconnect("Internal error");
    }

    /**
     * Called when the client auth flow is complete.
     * Marks the session as auth complete and flushes any pending backend packets.
     */
    private void completeClientAuth() {
        session.setClientAuthComplete(true);
        LOGGER.info("Session {}: Client auth complete, flushing {} pending backend packets",
            session.getSessionId(), session.hasPendingBackendPackets() ? "some" : "no");

        // Flush any buffered backend packets to the client now
        session.flushPendingBackendPackets();
    }
}

