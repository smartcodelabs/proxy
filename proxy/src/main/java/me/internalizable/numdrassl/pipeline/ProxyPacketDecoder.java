package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.PacketRegistry;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.protocol.io.ProtocolException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Proxy-aware packet decoder that decodes Hytale protocol packets.
 * Unknown packets are forwarded as RawPacket wrappers containing the raw bytes.
 */
public class ProxyPacketDecoder extends ByteToMessageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPacketDecoder.class);
    private static final int LENGTH_PREFIX_SIZE = 4;
    private static final int PACKET_ID_SIZE = 4;
    private static final int MIN_FRAME_SIZE = 8;
    private static final int MAX_PAYLOAD_SIZE = 0x64000000; // 100MB

    private final String connectionType;
    private final boolean debugMode;

    public ProxyPacketDecoder(String connectionType, boolean debugMode) {
        this.connectionType = connectionType;
        this.debugMode = debugMode;
    }

    @Override
    protected void decode(@Nonnull ChannelHandlerContext ctx, @Nonnull ByteBuf in, @Nonnull List<Object> out) {
        // Need at least the header (length + packet id)
        if (in.readableBytes() < MIN_FRAME_SIZE) {
            return;
        }

        in.markReaderIndex();

        // Read payload length (little-endian)
        int payloadLength = in.readIntLE();

        // Validate payload length
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            LOGGER.error("[{}] Invalid payload length: {}", connectionType, payloadLength);
            in.skipBytes(in.readableBytes());
            ctx.close();
            return;
        }

        // Read packet ID (little-endian)
        int packetId = in.readIntLE();

        // Look up packet info
        PacketRegistry.PacketInfo packetInfo = PacketRegistry.getById(packetId);
        if (packetInfo == null) {
            // Still need to wait for full payload
            if (in.readableBytes() < payloadLength) {
                LOGGER.trace("[{}] Unknown packet ID: {} (0x{}) - waiting for payload ({} of {} bytes)",
                    connectionType, packetId, Integer.toHexString(packetId), in.readableBytes(), payloadLength);
                in.resetReaderIndex();
                return;
            }

            // Create a raw packet wrapper for unknown packets
            // Copy the raw bytes to a new buffer to avoid issues with the input buffer
            in.resetReaderIndex();
            int totalSize = MIN_FRAME_SIZE + payloadLength;
            ByteBuf rawCopy = ctx.alloc().buffer(totalSize);
            in.readBytes(rawCopy, totalSize);

            out.add(rawCopy);

            if (debugMode) {
                LOGGER.debug("[{}] Created RawPacket id={} (size={} bytes)",
                    connectionType, packetId, totalSize);
            }
            return;
        }

        // Check packet size against max
        if (payloadLength > packetInfo.maxSize()) {
            LOGGER.error("[{}] Packet {} payload too large: {} > {}",
                connectionType, packetInfo.name(), payloadLength, packetInfo.maxSize());
            in.skipBytes(in.readableBytes());
            ctx.close();
            return;
        }

        // Wait for full payload
        if (in.readableBytes() < payloadLength) {
            in.resetReaderIndex();
            return;
        }

        // Decode the packet
        try {
            Packet packet = com.hypixel.hytale.protocol.io.PacketIO.readFramedPacketWithInfo(
                in, payloadLength, packetInfo, PacketStatsRecorder.NOOP);

            if (debugMode) {
                LOGGER.debug("[{}] Decoded packet: {} (id={})",
                    connectionType, packetInfo.name(), packetId);
            }

            out.add(packet);

        } catch (ProtocolException e) {
            LOGGER.error("[{}] Protocol error decoding packet {}: {}",
                connectionType, packetInfo.name(), e.getMessage());
            in.skipBytes(in.readableBytes());
            ctx.close();
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("[{}] Buffer underflow decoding packet {}: {}",
                connectionType, packetInfo.name(), e.getMessage());
            in.skipBytes(in.readableBytes());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("[{}] Exception in packet decoder", connectionType, cause);
        ctx.close();
    }
}
