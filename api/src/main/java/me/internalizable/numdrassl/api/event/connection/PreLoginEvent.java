package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * Event fired when a player attempts to connect to the proxy.
 * This is fired before authentication, so the player's UUID and name may not be known yet.
 *
 * <p>Cancelling this event or setting the result to denied will disconnect the player.</p>
 */
public class PreLoginEvent implements ResultedEvent<PreLoginEvent.PreLoginResult> {

    private final InetSocketAddress address;
    private PreLoginResult result;

    public PreLoginEvent(@Nonnull InetSocketAddress address) {
        this.address = address;
        this.result = PreLoginResult.allowed();
    }

    /**
     * Get the address the player is connecting from.
     *
     * @return the player's address
     */
    @Nonnull
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public PreLoginResult getResult() {
        return result;
    }

    @Override
    public void setResult(PreLoginResult result) {
        this.result = result;
    }

    /**
     * Result for a pre-login event.
     */
    public static final class PreLoginResult implements Result {

        private static final PreLoginResult ALLOWED = new PreLoginResult(true, null);

        private final boolean allowed;
        private final String denyReason;

        private PreLoginResult(boolean allowed, @Nullable String denyReason) {
            this.allowed = allowed;
            this.denyReason = denyReason;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Get the reason for denying the login.
         *
         * @return the deny reason, or null if allowed
         */
        @Nullable
        public String getDenyReason() {
            return denyReason;
        }

        /**
         * Allow the login.
         *
         * @return an allowed result
         */
        public static PreLoginResult allowed() {
            return ALLOWED;
        }

        /**
         * Deny the login with a reason.
         *
         * @param reason the reason to show the player
         * @return a denied result
         */
        public static PreLoginResult denied(@Nonnull String reason) {
            return new PreLoginResult(false, reason);
        }
    }
}

