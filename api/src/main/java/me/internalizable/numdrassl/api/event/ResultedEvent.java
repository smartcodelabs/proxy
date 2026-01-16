package me.internalizable.numdrassl.api.event;

/**
 * An event that allows the result to be modified.
 *
 * @param <R> the result type
 */
public interface ResultedEvent<R extends ResultedEvent.Result> {

    /**
     * Get the current result.
     *
     * @return the result
     */
    R getResult();

    /**
     * Set the result.
     *
     * @param result the new result
     */
    void setResult(R result);

    /**
     * Base interface for event results.
     */
    interface Result {

        /**
         * Check if this result allows the action to proceed.
         *
         * @return true if allowed
         */
        boolean isAllowed();
    }

    /**
     * A simple allowed/denied result.
     */
    final class GenericResult implements Result {

        private static final GenericResult ALLOWED = new GenericResult(true);
        private static final GenericResult DENIED = new GenericResult(false);

        private final boolean allowed;

        private GenericResult(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Get the allowed result.
         *
         * @return the allowed result
         */
        public static GenericResult allowed() {
            return ALLOWED;
        }

        /**
         * Get the denied result.
         *
         * @return the denied result
         */
        public static GenericResult denied() {
            return DENIED;
        }
    }
}

