package me.internalizable.numdrassl.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

import javax.annotation.Nonnull;

/**
 * Wrapper for raw packet data that couldn't be decoded.
 * Used to forward unknown packets without modification.
 */
public class RawPacket implements ReferenceCounted {

    private final int packetId;
    private final ByteBuf data;

    public RawPacket(int packetId, @Nonnull ByteBuf data) {
        this.packetId = packetId;
        this.data = data;
    }

    public int getPacketId() {
        return packetId;
    }

    @Nonnull
    public ByteBuf getData() {
        return data;
    }

    @Override
    public int refCnt() {
        return data.refCnt();
    }

    @Override
    public RawPacket retain() {
        data.retain();
        return this;
    }

    @Override
    public RawPacket retain(int increment) {
        data.retain(increment);
        return this;
    }

    @Override
    public RawPacket touch() {
        data.touch();
        return this;
    }

    @Override
    public RawPacket touch(Object hint) {
        data.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return data.release();
    }

    @Override
    public boolean release(int decrement) {
        return data.release(decrement);
    }
}

