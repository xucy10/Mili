package net.minecraft.server.jsonrpc;

import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.security.AuthenticationHandler;
import net.minecraft.server.jsonrpc.websocket.JsonToWebSocketEncoder;
import net.minecraft.server.jsonrpc.websocket.WebSocketToJsonCodec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ManagementServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HostAndPort hostAndPort;
    final AuthenticationHandler authenticationHandler;
    private @Nullable Channel serverChannel;
    private final NioEventLoopGroup nioEventLoopGroup;
    private final Set<Connection> connections = Sets.newIdentityHashSet();

    public ManagementServer(HostAndPort hostAndPort, AuthenticationHandler authenticationHandler) {
        this.hostAndPort = hostAndPort;
        this.authenticationHandler = authenticationHandler;
        this.nioEventLoopGroup = new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Management server IO #%d").setDaemon(true).build());
    }

    public ManagementServer(HostAndPort hostAndPort, AuthenticationHandler authenticationHandler, NioEventLoopGroup nioEventLoopGroup) {
        this.hostAndPort = hostAndPort;
        this.authenticationHandler = authenticationHandler;
        this.nioEventLoopGroup = nioEventLoopGroup;
    }

    public void onConnected(Connection connection) {
        synchronized (this.connections) {
            this.connections.add(connection);
        }
    }

    public void onDisconnected(Connection connection) {
        synchronized (this.connections) {
            this.connections.remove(connection);
        }
    }

    public void startWithoutTls(MinecraftApi api) {
        this.start(api, null);
    }

    public void startWithTls(MinecraftApi api, SslContext context) {
        this.start(api, context);
    }

    private void start(final MinecraftApi api, final @Nullable SslContext context) {
        final JsonRpcLogger jsonRpcLogger = new JsonRpcLogger();
        ChannelFuture channelFuture = new ServerBootstrap()
            .handler(new LoggingHandler(LogLevel.DEBUG))
            .channel(NioServerSocketChannel.class)
            .childHandler(
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        try {
                            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException var3) {
                        }

                        ChannelPipeline channelPipeline = channel.pipeline();
                        if (context != null) {
                            channelPipeline.addLast(context.newHandler(channel.alloc()));
                        }

                        channelPipeline.addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(65536))
                            .addLast(ManagementServer.this.authenticationHandler)
                            .addLast(new WebSocketServerProtocolHandler("/"))
                            .addLast(new WebSocketToJsonCodec())
                            .addLast(new JsonToWebSocketEncoder())
                            .addLast(new Connection(channel, ManagementServer.this, api, jsonRpcLogger));
                    }
                }
            )
            .group(this.nioEventLoopGroup)
            .localAddress(this.hostAndPort.getHost(), this.hostAndPort.getPort())
            .bind();
        this.serverChannel = channelFuture.channel();
        channelFuture.syncUninterruptibly();
        LOGGER.info("Json-RPC Management connection listening on {}:{}", this.hostAndPort.getHost(), this.getPort());
    }

    public void stop(boolean shutdownThreads) throws InterruptedException {
        if (this.serverChannel != null) {
            this.serverChannel.close().sync();
            this.serverChannel = null;
        }

        this.connections.clear();
        if (shutdownThreads) {
            this.nioEventLoopGroup.shutdownGracefully().sync();
        }
    }

    public void tick() {
        this.forEachConnection(Connection::tick);
    }

    public int getPort() {
        return this.serverChannel != null ? ((InetSocketAddress)this.serverChannel.localAddress()).getPort() : this.hostAndPort.getPort();
    }

    void forEachConnection(Consumer<Connection> action) {
        synchronized (this.connections) {
            this.connections.forEach(action);
        }
    }
}
