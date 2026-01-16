package me.internalizable.numdrassl.event;

/**
 * Direction of packet flow through the proxy
 */
public enum PacketDirection {
    /**
     * Packet traveling from the Hytale client to the backend server
     */
    CLIENT_TO_SERVER,

    /**
     * Packet traveling from the backend server to the Hytale client
     */
    SERVER_TO_CLIENT
}

