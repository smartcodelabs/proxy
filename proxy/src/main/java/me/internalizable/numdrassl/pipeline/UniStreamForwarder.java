package me.internalizable.numdrassl.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Transparent byte forwarder for unidirectional QUIC streams.
 *
 * <p>Reads raw {@link ByteBuf} data from a backend unidirectional stream and writes it
 * 1:1 to the corresponding client unidirectional stream. Data is buffered until
 * the client stream becomes available, then the backlog is flushed.</p>
 *
 * <p>Used for Hytale's multi-channel architecture where Chunks and WorldMap data
 * are sent on dedicated server-initiated unidirectional streams.</p>
 */
public final class UniStreamForwarder extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniStreamForwarder.class);
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB

    private final long sessionId;
    private final long backendStreamId;
    private final AtomicReference<QuicStreamChannel> clientStream = new AtomicReference<>();
    private CompositeByteBuf pendingBuffer;
    private boolean closed;

    public UniStreamForwarder(long sessionId, long backendStreamId) {
        this.sessionId = sessionId;
        this.backendStreamId = backendStreamId;
    }

    /**
     * Sets the target client stream and flushes any buffered data.
     */
    public void setClientStream(QuicStreamChannel stream) {
        clientStream.set(stream);
        LOGGER.debug("Session {}: UniStream forwarder backend={} -> client={} linked",
                sessionId, backendStreamId, stream.streamId());
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        pendingBuffer = ctx.alloc().compositeBuffer(256);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (closed) {
            if (msg instanceof ByteBuf buf) {
                buf.release();
            }
            return;
        }

        if (!(msg instanceof ByteBuf data)) {
            ctx.fireChannelRead(msg);
            return;
        }

        QuicStreamChannel target = clientStream.get();
        if (target != null && target.isActive()) {
            // Flush any pending buffer first
            flushPendingBuffer(target);
            // Forward directly
            target.writeAndFlush(data.retain());
        } else {
            // Buffer until client stream is ready
            if (pendingBuffer.readableBytes() + data.readableBytes() > MAX_BUFFER_SIZE) {
                LOGGER.error("Session {}: UniStream buffer overflow (backend={}), disconnecting",
                        sessionId, backendStreamId);
                data.release();
                ctx.close();
                return;
            }
            pendingBuffer.addComponent(true, data.retain());
            LOGGER.debug("Session {}: Buffering {} bytes for backend uni-stream {} (total buffered: {})",
                    sessionId, data.readableBytes(), backendStreamId, pendingBuffer.readableBytes());
        }

        data.release();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        QuicStreamChannel target = clientStream.get();
        if (target != null && target.isActive()) {
            flushPendingBuffer(target);
        }
    }

    private void flushPendingBuffer(QuicStreamChannel target) {
        if (pendingBuffer != null && pendingBuffer.isReadable()) {
            LOGGER.debug("Session {}: Flushing {} buffered bytes to client uni-stream",
                    sessionId, pendingBuffer.readableBytes());
            ByteBuf copy = pendingBuffer.copy();
            target.writeAndFlush(copy);
            pendingBuffer.clear();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closed = true;
        LOGGER.debug("Session {}: Backend uni-stream {} closed", sessionId, backendStreamId);
        if (pendingBuffer != null) {
            pendingBuffer.release();
            pendingBuffer = null;
        }
        // Close the corresponding client stream if it exists
        QuicStreamChannel target = clientStream.get();
        if (target != null && target.isActive()) {
            target.close();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: UniStream forwarder error (backend={})",
                sessionId, backendStreamId, cause);
        ctx.close();
    }
}



