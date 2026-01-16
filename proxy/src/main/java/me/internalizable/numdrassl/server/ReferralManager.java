package me.internalizable.numdrassl.server;

import me.internalizable.numdrassl.config.BackendServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages pending server referrals for player transfers.
 * When a player is transferred between servers, we use ClientReferral to tell
 * the client to reconnect. This manager tracks where players should be routed
 * when they reconnect with referral data.
 */
public class ReferralManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferralManager.class);

    // Referrals expire after 30 seconds
    private static final long REFERRAL_EXPIRY_SECONDS = 30;

    // Prefix for referral data to identify our referrals
    private static final String REFERRAL_PREFIX = "NUMDRASSL:";

    private final ProxyServer proxyServer;
    private final Map<UUID, PendingReferral> pendingReferrals = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public ReferralManager(@Nonnull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Referral-Cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule cleanup of expired referrals every 10 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredReferrals, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Create referral data for a player transfer to a specific backend.
     *
     * @param playerUuid The player's UUID
     * @param targetBackend The backend server to transfer to
     * @return The referral data bytes to include in ClientReferral packet
     */
    @Nonnull
    public byte[] createReferral(@Nonnull UUID playerUuid, @Nonnull BackendServer targetBackend) {
        // Store the pending referral
        PendingReferral referral = new PendingReferral(playerUuid, targetBackend, System.currentTimeMillis());
        pendingReferrals.put(playerUuid, referral);

        LOGGER.info("Created referral for {} to backend {}", playerUuid, targetBackend.getName());

        // Create referral data: PREFIX + backend name
        String data = REFERRAL_PREFIX + targetBackend.getName();
        return data.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Check if a player has a pending referral and return the target backend.
     * Consumes the referral (removes it from pending).
     *
     * @param playerUuid The player's UUID
     * @param referralData The referral data from Connect packet (may be null)
     * @return The target backend if this is a valid referral, null otherwise
     */
    @Nullable
    public BackendServer consumeReferral(@Nonnull UUID playerUuid, @Nullable byte[] referralData) {
        // First check if we have a pending referral for this player
        PendingReferral pending = pendingReferrals.remove(playerUuid);

        if (pending == null) {
            // No pending referral - this is a fresh connection
            return null;
        }

        // Check if the referral hasn't expired
        if (System.currentTimeMillis() - pending.createdAt > REFERRAL_EXPIRY_SECONDS * 1000) {
            LOGGER.warn("Referral for {} has expired", playerUuid);
            return null;
        }

        // Validate referral data if provided
        if (referralData != null && referralData.length > 0) {
            String data = new String(referralData, StandardCharsets.UTF_8);
            if (data.startsWith(REFERRAL_PREFIX)) {
                String backendName = data.substring(REFERRAL_PREFIX.length());
                // Verify it matches the pending referral
                if (!backendName.equals(pending.targetBackend.getName())) {
                    LOGGER.warn("Referral data mismatch for {}: expected {}, got {}",
                        playerUuid, pending.targetBackend.getName(), backendName);
                    // Trust the pending referral over the data
                }
            }
        }

        LOGGER.info("Consumed referral for {} -> backend {}", playerUuid, pending.targetBackend.getName());
        return pending.targetBackend;
    }

    /**
     * Check if a player has a pending referral (without consuming it).
     */
    public boolean hasPendingReferral(@Nonnull UUID playerUuid) {
        PendingReferral pending = pendingReferrals.get(playerUuid);
        if (pending == null) {
            return false;
        }
        // Check if not expired
        return System.currentTimeMillis() - pending.createdAt <= REFERRAL_EXPIRY_SECONDS * 1000;
    }

    /**
     * Cancel a pending referral for a player.
     */
    public void cancelReferral(@Nonnull UUID playerUuid) {
        PendingReferral removed = pendingReferrals.remove(playerUuid);
        if (removed != null) {
            LOGGER.debug("Cancelled referral for {}", playerUuid);
        }
    }

    /**
     * Clean up expired referrals
     */
    private void cleanupExpiredReferrals() {
        long now = System.currentTimeMillis();
        pendingReferrals.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt > REFERRAL_EXPIRY_SECONDS * 1000) {
                LOGGER.debug("Expired referral for {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Shutdown the referral manager
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pendingReferrals.clear();
    }

    /**
     * Represents a pending referral for a player
     */
    private static class PendingReferral {
        final UUID playerUuid;
        final BackendServer targetBackend;
        final long createdAt;

        PendingReferral(UUID playerUuid, BackendServer targetBackend, long createdAt) {
            this.playerUuid = playerUuid;
            this.targetBackend = targetBackend;
            this.createdAt = createdAt;
        }
    }
}

