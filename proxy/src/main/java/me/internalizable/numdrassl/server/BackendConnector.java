package me.internalizable.numdrassl.server;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.pipeline.BackendPacketHandler;
import me.internalizable.numdrassl.pipeline.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.ProxyPacketEncoder;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Handles establishing QUIC connections to backend Hytale servers
 */
public class BackendConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendConnector.class);

    private final ProxyServer proxyServer;
    private final EventLoopGroup group;
    private QuicSslContext sslContext;

    public BackendConnector(@Nonnull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        this.group = new NioEventLoopGroup(2);
        // SSL context will be initialized when initSslContext is called
    }

    /**
     * Initialize the SSL context using the same certificate as the proxy server.
     * This is critical for certificate binding to work - the client sees the proxy's
     * server certificate, so we must present the same certificate to the backend.
     */
    public void initSslContext(String certPath, String keyPath) {
        try {
            File certFile = new File(certPath);
            File keyFile = new File(keyPath);

            if (!certFile.exists() || !keyFile.exists()) {
                throw new RuntimeException("Certificate files not found: " + certPath + ", " + keyPath);
            }

            LOGGER.info("Using proxy server certificate for backend connections: {}", certPath);

            // Log the certificate fingerprint for debugging
            try {
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate)
                    cf.generateCertificate(new java.io.FileInputStream(certFile));
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(cert.getEncoded());
                String fingerprint = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                LOGGER.info("Proxy certificate fingerprint: {}", fingerprint);
                LOGGER.info("This fingerprint MUST match what the client's JWT contains!");
            } catch (Exception e) {
                LOGGER.warn("Could not compute certificate fingerprint for logging", e);
            }

            this.sslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .keyManager(keyFile, null, certFile)
                .applicationProtocols("hytale/1")
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create client SSL context", e);
        }
    }

    /**
     * Connect a session to a backend server
     */
    public void connect(@Nonnull ProxySession session, @Nonnull BackendServer backend, @Nonnull Connect connectPacket) {
        LOGGER.info("Session {}: Initiating connection to backend {} ({}:{})",
            session.getSessionId(), backend.getName(), backend.getHost(), backend.getPort());

        boolean debugMode = proxyServer.getConfig().isDebugMode();

        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(proxyServer.getConfig().getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec);

            InetSocketAddress backendAddress = new InetSocketAddress(backend.getHost(), backend.getPort());

            // Create the datagram channel first
            Channel datagramChannel = bootstrap.bind(0).sync().channel();

            // Connect to the backend QUIC server
            QuicChannel.newBootstrap(datagramChannel)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        // This handles server-initiated streams (if any)
                        ch.pipeline().addLast(new ProxyPacketDecoder("backend-server", debugMode));
                        ch.pipeline().addLast(new ProxyPacketEncoder("backend-server", debugMode));
                        ch.pipeline().addLast(new BackendPacketHandler(proxyServer, session));
                    }
                })
                .remoteAddress(backendAddress)
                .connect()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        QuicChannel backendChannel = (QuicChannel) future.getNow();
                        handleBackendConnected(session, backendChannel, connectPacket, debugMode);
                    } else {
                        LOGGER.error("Session {}: Failed to connect to backend",
                            session.getSessionId(), future.cause());
                        session.disconnect("Failed to connect to backend server");
                    }
                });

        } catch (Exception e) {
            LOGGER.error("Session {}: Error connecting to backend", session.getSessionId(), e);
            session.disconnect("Error connecting to backend server");
        }
    }

    private void handleBackendConnected(ProxySession session, QuicChannel backendChannel,
                                        Connect connectPacket, boolean debugMode) {
        LOGGER.info("Session {}: Connected to backend QUIC channel", session.getSessionId());
        session.setBackendChannel(backendChannel);

        // Create a bidirectional stream to the backend
        backendChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                ch.pipeline().addLast(new ProxyPacketDecoder("backend", debugMode));
                ch.pipeline().addLast(new ProxyPacketEncoder("backend", debugMode));
                ch.pipeline().addLast(new BackendPacketHandler(proxyServer, session));
            }
        }).addListener(streamFuture -> {
            if (streamFuture.isSuccess()) {
                QuicStreamChannel backendStream = (QuicStreamChannel) streamFuture.getNow();
                session.setBackendStream(backendStream);

                LOGGER.info("Session {}: Backend stream created, forwarding Connect packet",
                    session.getSessionId());

                // Forward the Connect packet to the backend
                backendStream.writeAndFlush(connectPacket);
                session.setState(SessionState.AUTHENTICATING);

            } else {
                LOGGER.error("Session {}: Failed to create backend stream",
                    session.getSessionId(), streamFuture.cause());
                session.disconnect("Failed to establish backend stream");
            }
        });
    }

    /**
     * Reconnect a session to a new backend server (for server switching).
     * This is called when a player transfers from one server to another.
     */
    public void reconnect(@Nonnull ProxySession session, @Nonnull BackendServer backend, @Nonnull Connect connectPacket) {
        LOGGER.info("Session {}: Reconnecting to backend {} ({}:{})",
            session.getSessionId(), backend.getName(), backend.getHost(), backend.getPort());

        boolean debugMode = proxyServer.getConfig().isDebugMode();

        try {
            ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(proxyServer.getConfig().getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .build();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec);

            InetSocketAddress backendAddress = new InetSocketAddress(backend.getHost(), backend.getPort());

            // Create the datagram channel first
            Channel datagramChannel = bootstrap.bind(0).sync().channel();

            // Connect to the backend QUIC server
            QuicChannel.newBootstrap(datagramChannel)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        // This handles server-initiated streams (if any)
                        ch.pipeline().addLast(new ProxyPacketDecoder("backend-server", debugMode));
                        ch.pipeline().addLast(new ProxyPacketEncoder("backend-server", debugMode));
                        ch.pipeline().addLast(new BackendPacketHandler(proxyServer, session));
                    }
                })
                .remoteAddress(backendAddress)
                .connect()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        QuicChannel backendChannel = (QuicChannel) future.getNow();
                        handleReconnectSuccess(session, backendChannel, backend, connectPacket, debugMode);
                    } else {
                        LOGGER.error("Session {}: Failed to reconnect to backend {}",
                            session.getSessionId(), backend.getName(), future.cause());
                        // Send error message to client and revert state
                        sendTransferFailedMessage(session, backend.getName());
                    }
                });

        } catch (Exception e) {
            LOGGER.error("Session {}: Error reconnecting to backend", session.getSessionId(), e);
            sendTransferFailedMessage(session, backend.getName());
        }
    }

    private void handleReconnectSuccess(ProxySession session, QuicChannel backendChannel,
                                        BackendServer backend, Connect connectPacket, boolean debugMode) {
        LOGGER.info("Session {}: Reconnected to backend {} QUIC channel",
            session.getSessionId(), backend.getName());
        session.setBackendChannel(backendChannel);

        // Create a bidirectional stream to the backend
        backendChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                ch.pipeline().addLast(new ProxyPacketDecoder("backend", debugMode));
                ch.pipeline().addLast(new ProxyPacketEncoder("backend", debugMode));
                ch.pipeline().addLast(new BackendPacketHandler(proxyServer, session));
            }
        }).addListener(streamFuture -> {
            if (streamFuture.isSuccess()) {
                QuicStreamChannel backendStream = (QuicStreamChannel) streamFuture.getNow();
                session.setBackendStream(backendStream);
                session.setCurrentBackend(backend);

                LOGGER.info("Session {}: Backend stream created for {}, forwarding Connect packet",
                    session.getSessionId(), backend.getName());

                // Forward the Connect packet to the backend
                backendStream.writeAndFlush(connectPacket);
                session.setState(SessionState.AUTHENTICATING);

                // Send transfer success message to client
                sendTransferSuccessMessage(session, backend.getName());

            } else {
                LOGGER.error("Session {}: Failed to create backend stream for {}",
                    session.getSessionId(), backend.getName(), streamFuture.cause());
                sendTransferFailedMessage(session, backend.getName());
            }
        });
    }

    private void sendTransferSuccessMessage(ProxySession session, String serverName) {
        // Create a formatted message informing the player about the transfer
        com.hypixel.hytale.protocol.FormattedMessage formattedMessage =
            new com.hypixel.hytale.protocol.FormattedMessage(
                null,
                null,
                new com.hypixel.hytale.protocol.FormattedMessage[] {
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        "Connecting to ",
                        null, null, null, null,
                        "#FFAA00",  // Gold color
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    ),
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        serverName,
                        null, null, null, null,
                        "#55FF55",  // Green color
                        com.hypixel.hytale.protocol.MaybeBool.True,  // Bold
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    ),
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        "...",
                        null, null, null, null,
                        "#FFAA00",
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    )
                },
                null, null, null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                null, false
            );

        com.hypixel.hytale.protocol.packets.interface_.ServerMessage serverMessage =
            new com.hypixel.hytale.protocol.packets.interface_.ServerMessage(
                com.hypixel.hytale.protocol.packets.interface_.ChatType.Chat,
                formattedMessage
            );

        session.sendToClient(serverMessage);
    }

    private void sendTransferFailedMessage(ProxySession session, String serverName) {
        // Revert state back to CONNECTED if possible
        if (session.getState() == SessionState.TRANSFERRING) {
            session.setState(SessionState.CONNECTED);
        }

        // Create a formatted error message
        com.hypixel.hytale.protocol.FormattedMessage formattedMessage =
            new com.hypixel.hytale.protocol.FormattedMessage(
                null,
                null,
                new com.hypixel.hytale.protocol.FormattedMessage[] {
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        "Failed to connect to ",
                        null, null, null, null,
                        "#FF5555",  // Red color
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    ),
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        serverName,
                        null, null, null, null,
                        "#FFAA00",  // Gold color
                        com.hypixel.hytale.protocol.MaybeBool.True,  // Bold
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    ),
                    new com.hypixel.hytale.protocol.FormattedMessage(
                        ". Please try again later.",
                        null, null, null, null,
                        "#FF5555",
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        com.hypixel.hytale.protocol.MaybeBool.Null,
                        null, false
                    )
                },
                null, null, null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                com.hypixel.hytale.protocol.MaybeBool.Null,
                null, false
            );

        com.hypixel.hytale.protocol.packets.interface_.ServerMessage serverMessage =
            new com.hypixel.hytale.protocol.packets.interface_.ServerMessage(
                com.hypixel.hytale.protocol.packets.interface_.ChatType.Chat,
                formattedMessage
            );

        session.sendToClient(serverMessage);
    }

    /**
     * Shutdown the connector
     */
    public void shutdown() {
        group.shutdownGracefully();
    }
}

