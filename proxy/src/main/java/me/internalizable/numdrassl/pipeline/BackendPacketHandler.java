package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ConnectAccept;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.internalizable.numdrassl.auth.ProxyAuthenticator;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Handles packets from the upstream backend server.
 * Intercepts, processes, and forwards packets to the client.
 */
public class BackendPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendPacketHandler.class);

    private final ProxyServer proxyServer;
    private final ProxySession session;

    public BackendPacketHandler(@Nonnull ProxyServer proxyServer, @Nonnull ProxySession session) {
        this.proxyServer = proxyServer;
        this.session = session;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Handle raw ByteBuf - unknown packets forwarded directly
        if (msg instanceof ByteBuf) {
            ByteBuf raw = (ByteBuf) msg;

            // Extract packet ID for logging (bytes 4-7 in little-endian)
            int packetId = -1;
            if (raw.readableBytes() >= 8) {
                packetId = raw.getIntLE(4);
            }

            boolean proxyAuthEnabled = session.isProxyAuthEnabled();
            boolean clientAuthComplete = session.isClientAuthComplete();
            boolean isServerTransfer = session.isServerTransfer();

            if (proxyServer.getConfig().isDebugMode()) {
                LOGGER.debug("Session {}: Raw ByteBuf (packet id={}) received, proxyAuthEnabled={}, clientAuthComplete={}, isTransfer={}",
                    session.getSessionId(), packetId, proxyAuthEnabled, clientAuthComplete, isServerTransfer);
            }

            // During server transfer, client is already authenticated - forward immediately
            // If proxy is handling auth and client hasn't completed auth yet (initial connection), buffer the packet
            if (proxyAuthEnabled && !clientAuthComplete && !isServerTransfer) {
                if (proxyServer.getConfig().isDebugMode()) {
                    LOGGER.debug("Session {}: BUFFERING raw packet id={}", session.getSessionId(), packetId);
                }
                session.queueBackendPacket(raw.retain());
                return;
            }

            // Forward raw bytes directly to client
            if (proxyServer.getConfig().isDebugMode()) {
                LOGGER.debug("Session {}: FORWARDING raw packet id={} directly to client", session.getSessionId(), packetId);
            }
            session.sendToClient(raw.retain());
            return;
        }

        if (!(msg instanceof Packet)) {
            LOGGER.warn("Session {}: Received unknown message type from backend: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }

        Packet packet = (Packet) msg;

        // Handle AuthGrant - intercept and handle authentication at proxy level
        if (packet instanceof AuthGrant) {
            handleAuthGrant((AuthGrant) packet);
            return;
        }

        // Handle ServerAuthToken - authentication completed
        if (packet instanceof ServerAuthToken) {
            handleServerAuthToken((ServerAuthToken) packet);
            return;
        }

        // Handle ConnectAccept - backend accepted the connection
        if (packet instanceof ConnectAccept) {
            handleConnectAccept((ConnectAccept) packet);
            return;
        }

        // Handle Disconnect from backend
        if (packet instanceof Disconnect) {
            handleDisconnect((Disconnect) packet);
            return;
        }

        // During server transfer, client is already authenticated - forward immediately
        // If proxy is handling auth and client hasn't completed auth yet (initial connection), buffer the packet
        if (session.isProxyAuthEnabled() && !session.isClientAuthComplete() && !session.isServerTransfer()) {
            session.queueBackendPacket(packet);
            return;
        }

        // Dispatch through event system and forward to client
        Packet toForward = proxyServer.getEventManager().dispatchServerPacket(session, packet);
        if (toForward != null) {
            session.sendToClient(toForward);
        }
    }

    private void handleAuthGrant(AuthGrant authGrant) {
        LOGGER.info("Session {}: Received AuthGrant from backend", session.getSessionId());

        // Store the auth grant
        session.setAuthGrant(authGrant);

        // Check if proxy is authenticated
        var authenticator = proxyServer.getAuthenticator();
        if (authenticator != null && authenticator.isAuthenticated()) {
            LOGGER.info("Session {}: Proxy is authenticated - will handle auth exchange", session.getSessionId());

            // Mark that proxy is handling auth for this session
            session.setProxyAuthEnabled(true);

            // Extract the authorization grant
            String grant = authGrant.authorizationGrant;
            if (grant == null || grant.isEmpty()) {
                LOGGER.error("Session {}: AuthGrant has no authorization grant!", session.getSessionId());
                session.disconnect("Authentication failed - no grant");
                return;
            }

            // Extract the server identity token for mutual authentication
            String serverIdentityToken = authGrant.serverIdentityToken;
            boolean needsMutualAuth = serverIdentityToken != null && !serverIdentityToken.isEmpty();

            if (needsMutualAuth) {
                LOGGER.info("Session {}: Server requires mutual authentication", session.getSessionId());
            }

            // Exchange the grant for an access token bound to PROXY's certificate
            authenticator.exchangeAuthGrantForToken(grant).thenCompose(accessToken -> {
                if (accessToken == null) {
                    LOGGER.error("Session {}: Failed to exchange auth grant for token", session.getSessionId());
                    session.disconnect("Authentication failed - token exchange failed");
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }

                LOGGER.info("Session {}: Got access token bound to proxy cert", session.getSessionId());

                // If mutual auth is required, also get the server auth grant
                if (needsMutualAuth) {
                    String serverAudience = extractAudienceFromToken(serverIdentityToken);
                    if (serverAudience == null) {
                        serverAudience = "";
                    }

                    LOGGER.info("Session {}: Requesting server authorization grant for mutual auth...", session.getSessionId());

                    return authenticator.requestServerAuthGrant(serverIdentityToken, serverAudience)
                        .thenApply(serverAuthGrant -> {
                            if (serverAuthGrant == null) {
                                LOGGER.warn("Session {}: Failed to get server auth grant, trying without", session.getSessionId());
                            }
                            return new AuthTokenPair(accessToken, serverAuthGrant);
                        });
                } else {
                    return java.util.concurrent.CompletableFuture.completedFuture(new AuthTokenPair(accessToken, null));
                }
            }).thenAccept(tokenPair -> {
                if (tokenPair == null || tokenPair.accessToken == null) {
                    return; // Already handled error
                }

                LOGGER.info("Session {}: Sending AuthToken to backend (with serverAuthGrant: {})",
                    session.getSessionId(), tokenPair.serverAuthGrant != null);

                // Store the proxy tokens for later (when we send ServerAuthToken to client)
                session.setProxyAccessToken(tokenPair.accessToken);
                if (tokenPair.serverAuthGrant != null) {
                    session.setProxyServerAuthGrant(tokenPair.serverAuthGrant);
                }

                // Create AuthToken with our token and server auth grant
                AuthToken authToken = new AuthToken(tokenPair.accessToken, tokenPair.serverAuthGrant);
                session.sendToBackend(authToken);

            }).exceptionally(ex -> {
                LOGGER.error("Session {}: Error exchanging auth grant", session.getSessionId(), ex);
                session.disconnect("Authentication failed - exchange error");
                return null;
            });

            // DON'T forward AuthGrant to client and DON'T send synthetic one
            // The client will just wait. When we receive ServerAuthToken from backend,
            // we'll send a complete auth response to the client.
            LOGGER.info("Session {}: Holding auth flow - will complete when backend responds", session.getSessionId());

        } else {
            // Proxy not authenticated - forward to client (will likely fail due to cert mismatch)
            LOGGER.warn("Session {}: Proxy not authenticated! Forwarding AuthGrant to client (will likely fail)",
                session.getSessionId());
            LOGGER.warn("Session {}: Use 'auth login' command to authenticate the proxy first!",
                session.getSessionId());
            session.sendToClient(authGrant);
        }
    }

    private void handleServerAuthToken(ServerAuthToken serverAuthToken) {
        LOGGER.info("Session {}: Received ServerAuthToken from backend - authentication phase complete",
            session.getSessionId());

        session.setState(SessionState.CONNECTED);

        // If this is a server transfer, client is already authenticated - just forward packets
        if (session.isServerTransfer()) {
            LOGGER.info("Session {}: Server transfer complete - client already authenticated, starting packet forwarding",
                session.getSessionId());

            // Clear the transfer flag
            session.setServerTransfer(false);

            // Mark proxy auth as enabled for this new connection
            session.setProxyAuthEnabled(true);

            // Client is already authenticated from the initial connection
            // No need to send AuthGrant or ServerAuthToken again
            // Just start forwarding packets
            return;
        }

        // If proxy is handling auth, we need to complete client's auth flow
        if (session.isProxyAuthEnabled()) {
            AuthGrant originalAuthGrant = session.getAuthGrant();

            // Store ServerAuthToken for later - will be sent after client completes auth
            session.setPendingServerAuthToken(serverAuthToken);

            if (originalAuthGrant != null) {
                LOGGER.info("Session {}: Backend auth complete, requesting NEW auth grant for client", session.getSessionId());

                // We need to request a NEW authorization grant for the client
                // The original grant was consumed by us when we exchanged it
                var authenticator = proxyServer.getAuthenticator();
                String clientIdentityToken = session.getClientIdentityToken();

                if (authenticator != null && clientIdentityToken != null) {
                    // Extract the server audience from the original AuthGrant's server identity token
                    String serverAudience = extractAudienceFromToken(originalAuthGrant.serverIdentityToken);
                    if (serverAudience == null) {
                        serverAudience = "";
                    }

                    // Request a new grant for the client
                    authenticator.requestNewAuthGrantForClient(clientIdentityToken, serverAudience)
                        .thenAccept(newGrant -> {
                            if (newGrant != null) {
                                LOGGER.info("Session {}: Got new auth grant for client, sending AuthGrant", session.getSessionId());
                                // Create a new AuthGrant with the fresh grant
                                AuthGrant clientAuthGrant = new AuthGrant(newGrant, originalAuthGrant.serverIdentityToken);
                                session.sendToClient(clientAuthGrant);
                            } else {
                                LOGGER.error("Session {}: Failed to get new auth grant for client!", session.getSessionId());
                                // Fallback: send original (will fail but at least client knows what happened)
                                session.sendToClient(originalAuthGrant);
                            }
                        })
                        .exceptionally(ex -> {
                            LOGGER.error("Session {}: Error getting new auth grant for client", session.getSessionId(), ex);
                            session.sendToClient(originalAuthGrant);
                            return null;
                        });
                } else {
                    LOGGER.warn("Session {}: Cannot request new grant - missing authenticator or identity token", session.getSessionId());
                    session.sendToClient(originalAuthGrant);
                }
            } else {
                LOGGER.warn("Session {}: No AuthGrant stored, sending ServerAuthToken directly", session.getSessionId());
                session.sendToClient(serverAuthToken);
            }
        } else {
            // Forward to client immediately
            session.sendToClient(serverAuthToken);
        }
    }

    private void handleConnectAccept(ConnectAccept accept) {
        LOGGER.info("Session {}: Backend accepted connection", session.getSessionId());

        session.setState(SessionState.CONNECTED);

        // Now that backend accepted, kick any existing session with same UUID
        proxyServer.getSessionManager().registerPlayerUuid(session, true);

        // Dispatch through event system
        Packet toForward = proxyServer.getEventManager().dispatchServerPacket(session, accept);
        if (toForward != null) {
            session.sendToClient(toForward);
        }
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Backend disconnecting", session.getSessionId());

        // If we're in the process of transferring to another server, don't disconnect the client
        if (session.getState() == SessionState.TRANSFERRING || session.isServerTransfer()) {
            LOGGER.info("Session {}: Ignoring disconnect during server transfer", session.getSessionId());
            return;
        }

        // Forward to client
        Packet toForward = proxyServer.getEventManager().dispatchServerPacket(session, disconnect);
        if (toForward != null) {
            session.sendToClient(toForward);
        }

        session.disconnect("Backend disconnected");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Session {}: Backend stream active", session.getSessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Backend stream closed", session.getSessionId());

        // If we're transferring or already disconnected, don't try to disconnect again
        SessionState currentState = session.getState();
        if (currentState != SessionState.DISCONNECTED &&
            currentState != SessionState.TRANSFERRING &&
            !session.isServerTransfer()) {
            session.disconnect("Backend connection lost");
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in backend handler", session.getSessionId(), cause);
        session.disconnect("Backend error");
    }

    /**
     * Extract the audience (aud) claim from a JWT token.
     * This is typically the server's session ID.
     */
    private String extractAudienceFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();

            if (json.has("aud")) {
                com.google.gson.JsonElement aud = json.get("aud");
                if (aud.isJsonPrimitive()) {
                    return aud.getAsString();
                } else if (aud.isJsonArray() && !aud.getAsJsonArray().isEmpty()) {
                    return aud.getAsJsonArray().get(0).getAsString();
                }
            }

            // Fallback: try to extract subject (sub) which might be the session ID
            if (json.has("sub")) {
                return json.get("sub").getAsString();
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to extract audience from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper class to hold both tokens for the async flow.
     */
    private static class AuthTokenPair {
        final String accessToken;
        final String serverAuthGrant;

        AuthTokenPair(String accessToken, String serverAuthGrant) {
            this.accessToken = accessToken;
            this.serverAuthGrant = serverAuthGrant;
        }
    }
}

