package me.internalizable.numdrassl.server;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * API for transferring players between backend servers.
 * Uses ClientReferral to tell the client to reconnect to the proxy,
 * then routes them to the new backend server.
 */
public class PlayerTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTransfer.class);

    private final ProxyServer proxyServer;

    public PlayerTransfer(@Nonnull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * Transfer a player to a different backend server.
     * This sends a ClientReferral packet which causes the client to reconnect.
     * When they reconnect, the ReferralManager routes them to the new backend.
     *
     * @param session The player's session
     * @param targetBackend The backend to transfer to
     * @return A future that completes when the referral is sent
     */
    public CompletableFuture<TransferResult> transfer(@Nonnull ProxySession session, @Nonnull BackendServer targetBackend) {
        CompletableFuture<TransferResult> future = new CompletableFuture<>();

        if (session.getState() != SessionState.CONNECTED) {
            future.complete(new TransferResult(false, "Player not connected"));
            return future;
        }

        if (session.getPlayerUuid() == null) {
            future.complete(new TransferResult(false, "Player UUID not known"));
            return future;
        }

        BackendServer currentBackend = session.getCurrentBackend();
        if (currentBackend != null && currentBackend.getName().equalsIgnoreCase(targetBackend.getName())) {
            future.complete(new TransferResult(false, "Already connected to this server"));
            return future;
        }

        LOGGER.info("Session {}: Initiating transfer for {} from {} to {}",
                session.getSessionId(),
                session.getPlayerName(),
                currentBackend != null ? currentBackend.getName() : "unknown",
                targetBackend.getName());

        // Create referral data that will tell us where to route the player on reconnect
        byte[] referralData = proxyServer.getReferralManager().createReferral(
                session.getPlayerUuid(),
                targetBackend
        );

        // Get the proxy's public address - this is where the client should reconnect
        // Use publicAddress if configured, otherwise fall back to bind address
        String proxyHost = proxyServer.getConfig().getPublicAddress();
        int proxyPort = proxyServer.getConfig().getPublicPort();

        // If public port is not configured (0), use bind port
        if (proxyPort <= 0) {
            proxyPort = proxyServer.getConfig().getBindPort();
        }

        // If public address is not configured or is 0.0.0.0, use localhost as fallback
        if (proxyHost == null || proxyHost.isEmpty() || "0.0.0.0".equals(proxyHost)) {
            String bindAddr = proxyServer.getConfig().getBindAddress();
            if (bindAddr != null && !bindAddr.isEmpty() && !"0.0.0.0".equals(bindAddr)) {
                proxyHost = bindAddr;
            } else {
                // Fallback to localhost for local testing
                proxyHost = "127.0.0.1";
                LOGGER.warn("Session {}: No publicAddress configured - using localhost. " +
                           "Set 'publicAddress' in proxy.yml for production use.", session.getSessionId());
            }
        }

        // Validate port range - ports > 32767 will overflow to negative when cast to signed short
        // The Hytale protocol uses signed short for port, so we must warn about this limitation
        if (proxyPort > 32767) {
            LOGGER.error("Session {}: Port {} exceeds maximum value for ClientReferral (32767). " +
                        "The client may fail to reconnect. Consider using a port below 32768 or " +
                        "set 'publicPort' in proxy.yml to a valid port.", session.getSessionId(), proxyPort);
            future.complete(new TransferResult(false, "Port " + proxyPort + " exceeds maximum value (32767) for player transfers"));
            return future;
        }

        // Create the ClientReferral packet
        HostAddress hostTo = new HostAddress(proxyHost, (short) proxyPort);
        ClientReferral referral = new ClientReferral(hostTo, referralData);

        LOGGER.info("Session {}: Sending ClientReferral to {} -> {}:{}",
                session.getSessionId(), session.getPlayerName(), proxyHost, proxyPort);

        // Send the referral to the client
        session.sendToClient(referral);

        // The client will disconnect and reconnect with the referral data
        // The ReferralManager will route them to the correct backend

        future.complete(new TransferResult(true, "Transfer referral sent - player will reconnect"));

        return future;
    }

    /**
     * Transfer a player to a backend server by name
     */
    public CompletableFuture<TransferResult> transfer(@Nonnull ProxySession session, @Nonnull String backendName) {
        BackendServer backend = proxyServer.getConfig().getBackendByName(backendName);
        if (backend == null) {
            CompletableFuture<TransferResult> future = new CompletableFuture<>();
            future.complete(new TransferResult(false, "Unknown backend server: " + backendName));
            return future;
        }
        return transfer(session, backend);
    }

    /**
     * Result of a player transfer attempt
     */
    public static class TransferResult {
        private final boolean success;
        private final String message;

        public TransferResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
