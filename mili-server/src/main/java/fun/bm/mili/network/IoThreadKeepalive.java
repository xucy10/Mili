package fun.bm.mili.network;

import fun.bm.mili.config.modules.optimizations.IoThreadKeepaliveConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * 在 Netty IO 线程处理 keepalive 回包，而不是把它排进玩家所属 region 的 tick 队列。
 *
 * <p>为什么需要它：Vanilla/Paper 的 tab 列表 ping 值不是网络 RTT，而是
 * {@code keepalive 回包被处理时的时间 - 发出时的时间}。在 Folia 下，回包会被
 * {@code PacketUtils.ensureRunningOnSameThread} 投递到玩家所属 region 的 tick 线程，
 * 于是这个差值里混进了“region 排队等待”。同一个服务器上，站在繁忙 region（主城、
 * 大量实体）里的玩家会被抬高几十毫秒，站在空闲 region 里的玩家几乎是纯 RTT ——
 * 这就是“人数一多，一部分人 ping 低、一部分人 ping 高”的来源。
 *
 * <p>做法：往每个玩家的 pipeline 里插入一个入站 handler，位于连接处理器之前，
 * 直接在 IO 线程上完成 keepalive 的时间戳结算，并终止该包的继续投递。
 * keepalive 不触碰任何世界状态，因此在 IO 线程处理是安全的；写操作集中在一个
 * event loop 线程（Netty 保证同一 channel 的入站事件串行），配合
 * {@code MultiThreadedQueue} 本身的并发安全性，无需额外加锁。
 */
public final class IoThreadKeepalive {

    public static final String HANDLER_NAME = "mili-io-keepalive";

    private static final Logger LOGGER = LoggerFactory.getLogger("Mili IO Keepalive");
    private static Field nettyChannelField;
    private static Field playerListenerField;
    private static MethodHandle getPacketListener;
    private static boolean initialised;
    private static boolean available;

    private IoThreadKeepalive() {
    }

    private static synchronized boolean init() {
        if (initialised) {
            return available;
        }
        initialised = true;
        try {
            nettyChannelField = findFieldByType(Connection.class, Channel.class);
            playerListenerField = findFieldByExactType(ServerPlayer.class, ServerGamePacketListenerImpl.class);
            if (nettyChannelField == null || playerListenerField == null) {
                LOGGER.error("IO 线程 keepalive 已禁用：无法定位 NMS 字段");
                available = false;
                return false;
            }
            Method method = Connection.class.getDeclaredMethod("getPacketListener");
            method.setAccessible(true);
            getPacketListener = MethodHandles.lookup().unreflect(method)
                    .asType(MethodType.methodType(Object.class, Object.class));
            available = true;
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("IO 线程 keepalive 初始化失败，功能保持禁用", throwable);
            available = false;
            return false;
        }
    }

    /**
     * 给单个玩家的 pipeline 安装 handler。幂等，重复调用安全。
     *
     * @return 该连接是否处于 IO 线程 keepalive 接管状态
     */
    public static boolean install(Player player) {
        if (!IoThreadKeepaliveConfig.enabled || !available()) {
            return false;
        }
        try {
            Object handle = player instanceof CraftPlayer craftPlayer ? craftPlayer.getHandle() : null;
            if (!(handle instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            Object listener = playerListenerField.get(serverPlayer);
            if (!(listener instanceof ServerGamePacketListenerImpl gameListener)) {
                return false;
            }
            return installFromListener(gameListener.connection);
        } catch (Throwable throwable) {
            // 单个玩家失败不应影响其它玩家，也不应刷屏
            LOGGER.debug("安装 IO 线程 keepalive 失败：{}", player.getName(), throwable);
            return false;
        }
    }

    private static boolean available() {
        if (!initialised) {
            init();
        }
        return available;
    }

    private static boolean installFromListener(Connection connection) throws Throwable {
        Channel channel = (Channel) nettyChannelField.get(connection);
        if (channel == null) {
            return false;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            return true;
        }
        String target = findConnectionHandlerName(pipeline, connection);
        if (target == null) {
            LOGGER.debug("未能定位连接处理器，跳过 {}", channel.remoteAddress());
            return false;
        }
        pipeline.addBefore(target, HANDLER_NAME, new KeepaliveHandler(connection));
        return true;
    }

    private static String findConnectionHandlerName(ChannelPipeline pipeline, Connection connection) {
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (entry.getValue() == connection) {
                return entry.getKey();
            }
        }
        // 兜底：按类型匹配，避免上游把连接换成包装后的实例
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (entry.getValue() instanceof Connection) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void uninstall(Player player) {
        if (!initialised || nettyChannelField == null || playerListenerField == null) {
            return;
        }
        try {
            Object handle = player instanceof CraftPlayer craftPlayer ? craftPlayer.getHandle() : null;
            if (!(handle instanceof ServerPlayer serverPlayer)) {
                return;
            }
            Object listener = playerListenerField.get(serverPlayer);
            if (!(listener instanceof ServerGamePacketListenerImpl gameListener)) {
                return;
            }
            Channel channel = (Channel) nettyChannelField.get(gameListener.connection);
            if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Throwable throwable) {
            LOGGER.debug("卸载 IO 线程 keepalive 失败：{}", player.getName(), throwable);
        }
    }

    /**
     * 对当前所有在线玩家生效（配置重载等热切换场景）。
     */
    public static void installAll() {
        if (!IoThreadKeepaliveConfig.enabled || !available()) {
            return;
        }
        int online = 0;
        int hooked = 0;
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                online++;
                if (install(player)) {
                    hooked++;
                }
            }
        } catch (Throwable throwable) {
            // 服务器尚未就绪（例如配置在 Bukkit 之前加载），忽略即可
            LOGGER.debug("批量安装 IO 线程 keepalive 失败", throwable);
            return;
        }
        LOGGER.info("IO 线程 keepalive 已接管 {}/{} 条连接", hooked, online);
    }

    public static void uninstallAll() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                uninstall(player);
            }
        } catch (Throwable throwable) {
            LOGGER.debug("批量卸载 IO 线程 keepalive 失败", throwable);
        }
    }

    private static Field findFieldByType(Class<?> owner, Class<?> type) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findFieldByExactType(Class<?> owner, Class<?> type) {
        Class<?> current = owner;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * 在 IO 线程结算 keepalive 的入站 handler。
     */
    private static final class KeepaliveHandler extends ChannelInboundHandlerAdapter {

        private final Connection connection;

        private KeepaliveHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ServerboundKeepAlivePacket packet) {
                try {
                    Object listener = getPacketListener.invoke(this.connection);
                    if (listener instanceof ServerCommonPacketListenerImpl common) {
                        // rx 时间戳在这里取 —— 仍在同一个 event loop 上，但不再等待 region 有空
                        common.handleKeepAlive(packet);
                        return;
                    }
                } catch (Throwable throwable) {
                    LOGGER.error("IO 线程 keepalive 结算失败，回退到 region 队列", throwable);
                }
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            removeSelf(ctx);
            ctx.fireChannelInactive();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            // 无外部资源需要释放
        }

        private void removeSelf(ChannelHandlerContext ctx) {
            try {
                ChannelPipeline pipeline = ctx.pipeline();
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            } catch (Throwable ignored) {
                // channel 正在关闭，忽略
            }
        }
    }
}
