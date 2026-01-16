package me.internalizable.numdrassl.api.server;

import javax.annotation.Nullable;

/**
 * Result of pinging a backend server.
 */
public final class PingResult {

    private final boolean online;
    private final long latencyMs;
    private final String error;

    private PingResult(boolean online, long latencyMs, @Nullable String error) {
        this.online = online;
        this.latencyMs = latencyMs;
        this.error = error;
    }

    /**
     * Check if the server is online.
     *
     * @return true if online
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Get the latency to the server in milliseconds.
     *
     * @return the latency, or -1 if offline
     */
    public long getLatencyMs() {
        return latencyMs;
    }

    /**
     * Get the error message if the ping failed.
     *
     * @return the error message, or null if successful
     */
    @Nullable
    public String getError() {
        return error;
    }

    /**
     * Create a successful ping result.
     *
     * @param latencyMs the latency in milliseconds
     * @return a success result
     */
    public static PingResult success(long latencyMs) {
        return new PingResult(true, latencyMs, null);
    }

    /**
     * Create a failed ping result.
     *
     * @param error the error message
     * @return a failure result
     */
    public static PingResult failure(String error) {
        return new PingResult(false, -1, error);
    }
}

