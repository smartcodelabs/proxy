package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Proxy-aware packet encoder that encodes Hytale protocol packets.
 * Based on the decompiled PacketEncoder but with additional logging.
 */
@ChannelHandler.Sharable
public class ProxyPacketEncoder extends MessageToByteEncoder<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPacketEncoder.class);

    private final String connectionType;
    private final boolean debugMode;

    public ProxyPacketEncoder(String connectionType, boolean debugMode) {
        this.connectionType = connectionType;
        this.debugMode = debugMode;
    }

    @Override
    protected void encode(@Nonnull ChannelHandlerContext ctx, @Nonnull Object msg, @Nonnull ByteBuf out) {
        if (msg instanceof ByteBuf) {
            // Forward raw ByteBuf as-is (unknown packets)
            ByteBuf raw = (ByteBuf) msg;
            try {
                // Extract packet ID for logging (bytes 4-7 in little-endian)
                int packetId = -1;
                if (raw.readableBytes() >= 8) {
                    packetId = raw.getIntLE(raw.readerIndex() + 4);
                }

                out.writeBytes(raw);

                if (debugMode) {
                    LOGGER.debug("[{}] Forwarding raw packet id={}", connectionType, packetId);
                }
            } finally {
                raw.release();
            }
            return;
        }

        if (msg instanceof Packet) {
            Packet packet = (Packet) msg;

            if (debugMode) {
                LOGGER.debug("[{}] Encoding packet: {} (id={})",
                    connectionType, packet.getClass().getSimpleName(), packet.getId());
            }

            PacketIO.writeFramedPacket(packet, packet.getClass(), out, PacketStatsRecorder.NOOP);
            return;
        }

        LOGGER.warn("[{}] Unknown message type: {}", connectionType, msg.getClass().getName());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("[{}] Exception in packet encoder", connectionType, cause);
        ctx.close();
    }
}

