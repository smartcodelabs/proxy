package me.internalizable.numdrassl.session;

/**
 * Represents the state of a proxy session
 */
public enum SessionState {
    /**
     * Initial state - waiting for Connect packet
     */
    HANDSHAKING,

    /**
     * Connect packet received, authenticating with backend
     */
    AUTHENTICATING,

    /**
     * Connecting to backend server
     */
    CONNECTING,

    /**
     * Fully connected and proxying packets
     */
    CONNECTED,

    /**
     * Session is being transferred to another backend
     */
    TRANSFERRING,

    /**
     * Session has been disconnected
     */
    DISCONNECTED
}

