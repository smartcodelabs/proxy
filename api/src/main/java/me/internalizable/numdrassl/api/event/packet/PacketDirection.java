package me.internalizable.numdrassl.api.event.packet;

/**
 * Direction of packet flow through the proxy.
 */
public enum PacketDirection {

    /**
     * Packet traveling from the client to the backend server.
     */
    CLIENTBOUND,

    /**
     * Packet traveling from the backend server to the client.
     */
    SERVERBOUND
}

