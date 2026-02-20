package me.internalizable.numdrassl.pipeline.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Buffers server-initiated unidirectional streams arriving from the backend,
 * then creates matching streams on the client QUIC channel in ascending backend
 * stream-ID order once all expected streams have arrived.
 *
 * <p>QUIC assigns server-initiated unidirectional stream IDs in creation order
 * (3, 7, 11 …). The client maps each stream by its <em>creation index</em>
 * (NetworkChannel mapping), so the proxy must open client-side streams in the
 * same ascending order regardless of the arrival order at the proxy.</p>
 *
 * <p>Data that arrives on a backend stream before its paired client stream is
 * ready is buffered in memory and drained once the client stream is wired up.</p>
 */
public final class UniStreamRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniStreamRelay.class);

    /**
     * Number of server-initiated unidirectional streams expected per connection.
     * Stream 3 (chunks/terrain) and stream 7 (world map) = 2 streams.
     */
    public static final int DEFAULT_EXPECTED_STREAMS = 2;

    private final ProxySession session;
    private final int expectedStreams;

    // All access to these three maps must be synchronised on `this`.
    private final TreeMap<Long, QuicStreamChannel> backendStreams = new TreeMap<>();
    private final TreeMap<Long, List<ByteBuf>> pendingBuffers = new TreeMap<>();
    private final TreeMap<Long, QuicStreamChannel> clientStreams = new TreeMap<>();

    /** Set to true once we start creating client streams; prevents double-creation. */
    private boolean creationStarted = false;

    public UniStreamRelay(@Nonnull ProxySession session) {
        this(session, DEFAULT_EXPECTED_STREAMS);
    }

    public UniStreamRelay(@Nonnull ProxySession session, int expectedStreams) {
        this.session = Objects.requireNonNull(session, "session");
        this.expectedStreams = expectedStreams;
    }

    // ==================== Backend stream registration ====================

    /**
     * Called when a new server-initiated backend unidirectional stream becomes active.
     * Buffers the stream and triggers client-side stream creation once enough streams
     * have been collected.
     */
    public void registerBackendStream(@Nonnull QuicStreamChannel backendStream) {
        long streamId = backendStream.streamId();
        List<Long> orderedIds = null;
        synchronized (this) {
            backendStreams.put(streamId, backendStream);
            pendingBuffers.put(streamId, new ArrayList<>());
            if (backendStreams.size() >= expectedStreams && !creationStarted) {
                creationStarted = true;
                // Capture the sorted key set under the lock to get a consistent snapshot.
                orderedIds = new ArrayList<>(backendStreams.keySet());
            }
        }
        LOGGER.debug("Session {}: Backend uni stream {} registered ({}/{})",
                session.getSessionId(), streamId, backendStreams.size(), expectedStreams);

        if (orderedIds != null) {
            createClientStreams(orderedIds, 0);
        }
    }

    // ==================== Data forwarding ====================

    /**
     * Called when raw bytes arrive on a backend unidirectional stream.
     * Either buffers them (if the client stream is not yet wired) or forwards
     * them directly to the corresponding client stream.
     *
     * <p>Ownership of {@code data} transfers to this method — the caller must
     * not release it.</p>
     */
    public void onData(long backendStreamId, @Nonnull ByteBuf data) {
        QuicStreamChannel clientStream;
        synchronized (this) {
            clientStream = clientStreams.get(backendStreamId);
            if (clientStream == null) {
                List<ByteBuf> buffer = pendingBuffers.get(backendStreamId);
                if (buffer != null) {
                    buffer.add(data);
                    return;
                }
                // Stream not registered yet — should not happen; drop the data.
                LOGGER.warn("Session {}: Data on unregistered backend uni stream {}, dropping",
                        session.getSessionId(), backendStreamId);
                data.release();
                return;
            }
        }
        forwardToClient(clientStream, data);
    }

    // ==================== Client stream creation ====================

    /**
     * Recursively creates client unidirectional streams in ascending backend stream-ID
     * order so that the client's creation-order → NetworkChannel mapping is preserved.
     */
    private void createClientStreams(List<Long> orderedIds, int index) {
        if (index >= orderedIds.size()) {
            LOGGER.info("Session {}: All {} client uni streams created",
                    session.getSessionId(), expectedStreams);
            return;
        }

        long backendStreamId = orderedIds.get(index);
        QuicChannel clientChannel = session.getClientChannel();

        clientChannel.createStream(QuicStreamType.UNIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                // Raw forwarding channel — no packet codec needed.
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                LOGGER.error("Session {}: Failed to create client uni stream for backend stream {}",
                        session.getSessionId(), backendStreamId, future.cause());
                return;
            }

            QuicStreamChannel clientStream = (QuicStreamChannel) future.getNow();
            LOGGER.debug("Session {}: Created client uni stream {} for backend stream {}",
                    session.getSessionId(), clientStream.streamId(), backendStreamId);

            List<ByteBuf> buffered;
            synchronized (UniStreamRelay.this) {
                clientStreams.put(backendStreamId, clientStream);
                buffered = pendingBuffers.remove(backendStreamId);
            }

            // Drain any data that arrived before the client stream was ready.
            // buffered may be null if release() was called concurrently (session closed
            // while client streams were still being created).
            if (buffered != null && !buffered.isEmpty()) {
                drainOnEventLoop(clientStream, buffered);
            }

            // Create the next client stream only after this one is fully set up,
            // so stream IDs on the client side are assigned in ascending order.
            createClientStreams(orderedIds, index + 1);
        });
    }

    private void drainOnEventLoop(QuicStreamChannel clientStream, List<ByteBuf> buffered) {
        if (clientStream.eventLoop().inEventLoop()) {
            drainBuffered(clientStream, buffered);
        } else {
            clientStream.eventLoop().execute(() -> drainBuffered(clientStream, buffered));
        }
    }

    private void drainBuffered(QuicStreamChannel clientStream, List<ByteBuf> buffered) {
        if (!clientStream.isActive()) {
            buffered.forEach(ReferenceCountUtil::safeRelease);
            return;
        }
        for (ByteBuf buf : buffered) {
            clientStream.write(buf, clientStream.voidPromise());
        }
        clientStream.flush();
    }

    private void forwardToClient(QuicStreamChannel clientStream, ByteBuf data) {
        if (!clientStream.isActive()) {
            data.release();
            return;
        }
        if (clientStream.eventLoop().inEventLoop()) {
            clientStream.writeAndFlush(data);
        } else {
            clientStream.eventLoop().execute(() -> {
                if (clientStream.isActive()) {
                    clientStream.writeAndFlush(data);
                } else {
                    data.release();
                }
            });
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Releases all pending (not-yet-forwarded) buffers.
     * Must be called when the session is closed to prevent memory leaks.
     */
    public synchronized void release() {
        for (List<ByteBuf> buffers : pendingBuffers.values()) {
            buffers.forEach(ReferenceCountUtil::safeRelease);
        }
        pendingBuffers.clear();
    }
}
