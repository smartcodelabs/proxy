package me.internalizable.numdrassl.l4;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 4 (UDP) proxy for Hytale QUIC traffic.
 *
 * This forwards raw UDP packets without terminating TLS, which allows
 * Hytale's certificate binding authentication to work correctly.
 *
 * IMPORTANT: This proxy CANNOT inspect or modify packet contents because
 * QUIC traffic is encrypted. It can only:
 * - Route traffic between clients and backends
 * - See connection metadata (IPs, ports)
 * - Count packets/bytes
 *
 * For packet inspection, the backend server must be modified to trust the proxy.
 */
public class Layer4UdpProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(Layer4UdpProxy.class);

    private final InetSocketAddress bindAddress;
    private final InetSocketAddress backendAddress;
    private final EventLoopGroup group;
    private Channel serverChannel;

    // Map client addresses to their backend channels
    private final Map<InetSocketAddress, Channel> clientToBackend = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, InetSocketAddress> backendToClient = new ConcurrentHashMap<>();

    public Layer4UdpProxy(String bindHost, int bindPort, String backendHost, int backendPort) {
        this.bindAddress = new InetSocketAddress(bindHost, bindPort);
        this.backendAddress = new InetSocketAddress(backendHost, backendPort);
        this.group = new NioEventLoopGroup(4);
    }

    public void start() throws Exception {
        LOGGER.info("Starting Layer 4 UDP Proxy...");
        LOGGER.info("  Bind: {}", bindAddress);
        LOGGER.info("  Backend: {}", backendAddress);

        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .handler(new ClientHandler());

        serverChannel = bootstrap.bind(bindAddress).sync().channel();

        LOGGER.info("Layer 4 UDP Proxy started on {}", bindAddress);
        LOGGER.info("Forwarding QUIC traffic to {}", backendAddress);
        LOGGER.info("");
        LOGGER.info("NOTE: This proxy forwards encrypted traffic - packet inspection is NOT possible.");
        LOGGER.info("      For packet inspection, the backend server must be modified to trust the proxy.");
    }

    public void stop() {
        LOGGER.info("Stopping Layer 4 UDP Proxy...");

        // Close all backend connections
        for (Channel ch : clientToBackend.values()) {
            ch.close();
        }
        clientToBackend.clear();
        backendToClient.clear();

        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        group.shutdownGracefully().syncUninterruptibly();
        LOGGER.info("Layer 4 UDP Proxy stopped");
    }

    /**
     * Handles packets from clients, forwards to backend
     */
    private class ClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            InetSocketAddress clientAddr = packet.sender();
            ByteBuf content = packet.content().retain();

            // Get or create backend channel for this client
            Channel backendChannel = clientToBackend.get(clientAddr);

            if (backendChannel == null || !backendChannel.isActive()) {
                // Create new backend connection
                backendChannel = createBackendChannel(ctx.channel(), clientAddr);
                clientToBackend.put(clientAddr, backendChannel);
                LOGGER.info("New connection: {} -> {}", clientAddr, backendAddress);
            }

            // Forward packet to backend
            backendChannel.writeAndFlush(new DatagramPacket(content, backendAddress));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("Client handler error", cause);
        }
    }

    /**
     * Creates a channel to communicate with the backend for a specific client
     */
    private Channel createBackendChannel(Channel clientChannel, InetSocketAddress clientAddr) throws Exception {
        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(new BackendHandler(clientChannel, clientAddr));

        Channel channel = bootstrap.bind(0).sync().channel();
        backendToClient.put((InetSocketAddress) channel.localAddress(), clientAddr);
        return channel;
    }

    /**
     * Handles packets from backend, forwards to client
     */
    private class BackendHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private final Channel clientChannel;
        private final InetSocketAddress clientAddr;

        public BackendHandler(Channel clientChannel, InetSocketAddress clientAddr) {
            this.clientChannel = clientChannel;
            this.clientAddr = clientAddr;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ByteBuf content = packet.content().retain();

            // Forward packet to client
            clientChannel.writeAndFlush(new DatagramPacket(content, clientAddr));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            LOGGER.info("Backend connection closed for client: {}", clientAddr);
            clientToBackend.remove(clientAddr);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("Backend handler error for client {}", clientAddr, cause);
            ctx.close();
        }
    }

    public static void main(String[] args) throws Exception {
        String bindHost = "0.0.0.0";
        int bindPort = 45585;
        String backendHost = "34.245.46.163";
        int backendPort = 5520;

        // Parse command line args
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bind-host":
                    bindHost = args[++i];
                    break;
                case "--bind-port":
                    bindPort = Integer.parseInt(args[++i]);
                    break;
                case "--backend-host":
                    backendHost = args[++i];
                    break;
                case "--backend-port":
                    backendPort = Integer.parseInt(args[++i]);
                    break;
            }
        }

        Layer4UdpProxy proxy = new Layer4UdpProxy(bindHost, bindPort, backendHost, backendPort);

        Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop));

        proxy.start();

        // Keep running
        Thread.currentThread().join();
    }
}

