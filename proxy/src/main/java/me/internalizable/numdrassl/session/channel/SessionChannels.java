package me.internalizable.numdrassl.session.channel;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.internalizable.numdrassl.pipeline.UniStreamForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the QUIC channels and streams for a proxy session.
 *
 * <p>This class encapsulates the bidirectional channel management:</p>
 * <ul>
 *   <li><b>Client side</b>: The downstream connection from the Hytale client</li>
 *   <li><b>Backend side</b>: The upstream connection to the Hytale server</li>
 * </ul>
 *
 * <p>Also manages unidirectional streams for Hytale's multi-channel architecture
 * (Chunks and WorldMap data). Backend uni-streams are buffered until all expected
 * streams arrive, then client-side streams are created in order.</p>
 *
 * <p>All operations are thread-safe via atomic references and concurrent collections.</p>
 */
public final class SessionChannels {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionChannels.class);

    /** Number of unidirectional streams the backend is expected to create (Chunks + WorldMap). */
    private static final int EXPECTED_UNI_STREAMS = 2;

    private final long sessionId;
    private final QuicChannel clientChannel;

    // Bidirectional streams (primary)
    private final AtomicReference<QuicStreamChannel> clientStream = new AtomicReference<>();
    private final AtomicReference<QuicChannel> backendChannel = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> backendStream = new AtomicReference<>();

    // Unidirectional streams: backend stream ID -> (stream, forwarder)
    private final ConcurrentSkipListMap<Long, UniStreamEntry> backendUniStreams = new ConcurrentSkipListMap<>();
    // Client uni-streams in creation order (matching sorted backend stream IDs)
    private final CopyOnWriteArrayList<QuicStreamChannel> clientUniStreams = new CopyOnWriteArrayList<>();

    public SessionChannels(long sessionId, @Nonnull QuicChannel clientChannel) {
        this.sessionId = sessionId;
        this.clientChannel = clientChannel;
    }

    /**
     * Entry tracking a backend unidirectional stream and its forwarder.
     */
    public record UniStreamEntry(QuicStreamChannel stream, UniStreamForwarder forwarder) {
    }

    // ==================== Client Side ====================

    @Nonnull
    public QuicChannel clientChannel() {
        return clientChannel;
    }

    @Nullable
    public QuicStreamChannel clientStream() {
        return clientStream.get();
    }

    public void setClientStream(@Nullable QuicStreamChannel stream) {
        clientStream.set(stream);
    }

    public boolean isClientActive() {
        return clientChannel.isActive();
    }

    public boolean isClientStreamActive() {
        QuicStreamChannel stream = clientStream.get();
        return stream != null && stream.isActive();
    }

    // ==================== Backend Side ====================

    @Nullable
    public QuicChannel backendChannel() {
        return backendChannel.get();
    }

    public void setBackendChannel(@Nullable QuicChannel channel) {
        backendChannel.set(channel);
    }

    @Nullable
    public QuicStreamChannel backendStream() {
        return backendStream.get();
    }

    public void setBackendStream(@Nullable QuicStreamChannel stream) {
        backendStream.set(stream);
    }

    public boolean isBackendActive() {
        QuicChannel channel = backendChannel.get();
        return channel != null && channel.isActive();
    }

    public boolean isBackendStreamActive() {
        QuicStreamChannel stream = backendStream.get();
        return stream != null && stream.isActive();
    }

    // ==================== Unidirectional Streams ====================

    /**
     * Registers a backend-initiated unidirectional stream.
     *
     * @return true if all expected uni-streams have arrived
     */
    public boolean addBackendUniStream(long streamId, QuicStreamChannel stream, UniStreamForwarder forwarder) {
        backendUniStreams.put(streamId, new UniStreamEntry(stream, forwarder));
        LOGGER.debug("Session {}: Registered backend uni-stream {} ({}/{})",
                sessionId, streamId, backendUniStreams.size(), EXPECTED_UNI_STREAMS);
        return backendUniStreams.size() >= EXPECTED_UNI_STREAMS;
    }

    /**
     * Returns true if all expected unidirectional streams have been received.
     */
    public boolean areAllUniStreamsReady() {
        return backendUniStreams.size() >= EXPECTED_UNI_STREAMS;
    }

    /**
     * Returns the backend uni-stream entries sorted by ascending stream ID.
     * This ensures creation order is preserved when creating client-side streams.
     */
    public List<UniStreamEntry> getSortedBackendUniStreams() {
        return new ArrayList<>(backendUniStreams.values());
    }

    /**
     * Adds a client-side unidirectional stream (created in order).
     */
    public void addClientUniStream(QuicStreamChannel stream) {
        clientUniStreams.add(stream);
    }

    // ==================== Lifecycle ====================

    /**
     * Closes all channels and streams.
     */
    public void closeAll() {
        closeBackend();
        closeClient();
    }

    /**
     * Closes only the backend connection, keeping client connected.
     * Used during server transfers.
     */
    public void closeBackend() {
        QuicStreamChannel bs = backendStream.getAndSet(null);
        if (bs != null && bs.isActive()) {
            bs.flush();
            bs.close();
            LOGGER.debug("Session {}: Closed backend stream", sessionId);
        }

        // Close backend unidirectional streams
        for (var entry : backendUniStreams.values()) {
            if (entry.stream().isActive()) {
                entry.stream().close();
            }
        }
        if (!backendUniStreams.isEmpty()) {
            LOGGER.debug("Session {}: Closed {} backend uni-streams", sessionId, backendUniStreams.size());
            backendUniStreams.clear();
        }

        QuicChannel bc = backendChannel.getAndSet(null);
        if (bc != null && bc.isActive()) {
            bc.flush();
            bc.close();
            LOGGER.debug("Session {}: Closed backend channel", sessionId);
        }
    }

    /**
     * Closes the client connection.
     */
    public void closeClient() {
        QuicStreamChannel cs = clientStream.getAndSet(null);
        if (cs != null && cs.isActive()) {
            cs.flush();
            cs.close();
            LOGGER.debug("Session {}: Closed client stream", sessionId);
        }

        // Close client unidirectional streams
        for (var stream : clientUniStreams) {
            if (stream.isActive()) {
                stream.close();
            }
        }
        if (!clientUniStreams.isEmpty()) {
            LOGGER.debug("Session {}: Closed {} client uni-streams", sessionId, clientUniStreams.size());
            clientUniStreams.clear();
        }

        if (clientChannel.isActive()) {
            clientChannel.flush();
            clientChannel.close();
            LOGGER.debug("Session {}: Closed client channel", sessionId);
        }
    }
}

