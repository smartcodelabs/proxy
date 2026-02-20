package me.internalizable.numdrassl.pipeline.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Netty inbound handler for server-initiated backend unidirectional streams.
 *
 * <p>Bypasses the packet decode/encode pipeline entirely — raw bytes are
 * handed straight to the session's {@link UniStreamRelay} for buffering and
 * transparent forwarding to the client.</p>
 */
public final class BackendUniStreamForwarder extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendUniStreamForwarder.class);

    private final UniStreamRelay relay;

    public BackendUniStreamForwarder(@Nonnull UniStreamRelay relay) {
        this.relay = Objects.requireNonNull(relay, "relay");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        relay.registerBackendStream((QuicStreamChannel) ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            // Ownership transfers to the relay; do NOT release here.
            relay.onData(((QuicStreamChannel) ctx.channel()).streamId(), buf);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Backend uni stream {} closed",
                ((QuicStreamChannel) ctx.channel()).streamId());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Exception in backend uni stream forwarder (stream {})",
                ((QuicStreamChannel) ctx.channel()).streamId(), cause);
        ctx.close();
    }
}
