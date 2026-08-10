package net.minecraft.server.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.ThreadFactory;
import org.jspecify.annotations.Nullable;

public abstract class EventLoopGroupHolder {
    private static final EventLoopGroupHolder NIO = new EventLoopGroupHolder("NIO", NioSocketChannel.class, NioServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return NioIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder EPOLL = new EventLoopGroupHolder("Epoll", EpollSocketChannel.class, EpollServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return EpollIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder KQUEUE = new EventLoopGroupHolder("Kqueue", KQueueSocketChannel.class, KQueueServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return KQueueIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder LOCAL = new EventLoopGroupHolder("Local", LocalChannel.class, LocalServerChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return LocalIoHandler.newFactory();
        }
    };
    // Paper start - Unix domain socket support
    private static final EventLoopGroupHolder EPOLL_UNIX_DOMAIN = new EventLoopGroupHolder("Unix Domain Socket", io.netty.channel.epoll.EpollDomainSocketChannel.class, io.netty.channel.epoll.EpollServerDomainSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return EpollIoHandler.newFactory();
        }
    };
    // Paper end - Unix domain socket support
    private final String type;
    private final Class<? extends Channel> channelCls;
    private final Class<? extends ServerChannel> serverChannelCls;
    private volatile @Nullable EventLoopGroup group;

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - use variant with address param
    public static EventLoopGroupHolder remote(boolean tryNativeTransport) {
        if (tryNativeTransport) {
            if (KQueue.isAvailable()) {
                return KQUEUE;
            }

            if (Epoll.isAvailable()) {
                return EPOLL;
            }
        }

        return NIO;
    }

    // Paper start - Unix domain socket support
    public static EventLoopGroupHolder remote(java.net.SocketAddress address, boolean tryNativeTransport) {
        if (tryNativeTransport) {
            if (KQueue.isAvailable()) {
                return KQUEUE;
            }

            if (Epoll.isAvailable()) {
                if (address instanceof io.netty.channel.unix.DomainSocketAddress) {
                    return EPOLL_UNIX_DOMAIN;
                } else {
                    return EPOLL;
                }
            }
        }
        return NIO;
    }
    // Paper end - Unix domain socket support

    public static EventLoopGroupHolder local() {
        return LOCAL;
    }

    EventLoopGroupHolder(String type, Class<? extends Channel> channelCls, Class<? extends ServerChannel> serverChannelCls) {
        this.type = type;
        this.channelCls = channelCls;
        this.serverChannelCls = serverChannelCls;
    }

    private ThreadFactory createThreadFactory() {
        return new ThreadFactoryBuilder().setNameFormat("Netty " + this.type + " IO #%d").setDaemon(true).build();
    }

    protected abstract IoHandlerFactory ioHandlerFactory();

    private EventLoopGroup createEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(this.createThreadFactory(), this.ioHandlerFactory());
    }

    public EventLoopGroup eventLoopGroup() {
        EventLoopGroup eventLoopGroup = this.group;
        if (eventLoopGroup == null) {
            synchronized (this) {
                eventLoopGroup = this.group;
                if (eventLoopGroup == null) {
                    eventLoopGroup = this.createEventLoopGroup();
                    this.group = eventLoopGroup;
                }
            }
        }

        return eventLoopGroup;
    }

    public Class<? extends Channel> channelCls() {
        return this.channelCls;
    }

    public Class<? extends ServerChannel> serverChannelCls() {
        return this.serverChannelCls;
    }
}
