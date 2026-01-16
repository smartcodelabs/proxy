package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired during the login process after the player's identity is known
 * but before they are fully connected.
 *
 * <p>Setting the result to denied will disconnect the player.</p>
 */
public class LoginEvent implements ResultedEvent<LoginEvent.LoginResult> {

    private final Player player;
    private LoginResult result;

    public LoginEvent(@Nonnull Player player) {
        this.player = player;
        this.result = LoginResult.allowed();
    }

    /**
     * Get the player attempting to log in.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    @Override
    public LoginResult getResult() {
        return result;
    }

    @Override
    public void setResult(LoginResult result) {
        this.result = result;
    }

    /**
     * Result for a login event.
     */
    public static final class LoginResult implements Result {

        private static final LoginResult ALLOWED = new LoginResult(true, null);

        private final boolean allowed;
        private final String denyReason;

        private LoginResult(boolean allowed, @Nullable String denyReason) {
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
        public static LoginResult allowed() {
            return ALLOWED;
        }

        /**
         * Deny the login with a reason.
         *
         * @param reason the reason to show the player
         * @return a denied result
         */
        public static LoginResult denied(@Nonnull String reason) {
            return new LoginResult(false, reason);
        }
    }
}

