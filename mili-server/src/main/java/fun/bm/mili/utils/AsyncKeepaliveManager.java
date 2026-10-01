package fun.bm.mili.utils;

import fun.bm.mili.config.modules.function.OldFeatureConfig;
import fun.bm.mili.scheduler.RecurringTask;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.Util;
import org.leavesmc.leaves.bot.ServerBotPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Ported from Leaves - Async keepalive
// Folia note: send() queues to Netty channel (thread-safe); disconnectAsync() is designed for async use;
// KeepAlive data structures are concurrent (ConcurrentLinkedQueue). Safe for Folia.
public final class AsyncKeepaliveManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mili Async Keepalive");
    private static final Map<Connection, ServerCommonPacketListenerImpl> ACTIVE_LISTENERS = new ConcurrentHashMap<>();

    static {
        // Mili start - 移交给线程治理层。原实现有双重隐患：一是完全没有关闭路径，
        // 线程随 JVM 一直泄漏；二是 scheduleAtFixedRate 无异常兜底，tickAll 抛一次
        // 即被 JDK 静默取消，心跳检测从此无声失效（表现为玩家超时不掉线）。
        if (OldFeatureConfig.asyncKeepalive) {
            RecurringTask.start("async-keepalive", 1_000L, AsyncKeepaliveManager::tickAll);
        }
        // Mili end
    }

    private AsyncKeepaliveManager() {
    }

    public static void register(ServerCommonPacketListenerImpl listener) {
        if (!OldFeatureConfig.asyncKeepalive) {
            return;
        }

        // 假人没有真实客户端，send() 为空操作导致其永远不会回复 keepalive 包，lastKeepAliveResponse 永不更新；
        // 注册进管理器会在超时后每秒对假人触发一次超时踢出链路（"bot was kicked due to keepalive timeout!" 刷屏）。
        // 且所有假人共享 BotConnection.INSTANCE 作为 map key，注册会互相覆盖并造成 listener 泄漏。
        // 因此将假人完全排除在异步 keepalive 管理之外。
        if (listener instanceof ServerBotPacketListenerImpl) {
            return;
        }

        ACTIVE_LISTENERS.put(listener.connection, listener);
    }

    public static void unregister(ServerCommonPacketListenerImpl listener) {
        ACTIVE_LISTENERS.remove(listener.connection, listener);
    }

    private static void tickAll() {
        long currentTimeNs = System.nanoTime();
        long currentTimeMs = Util.getMillis();

        for (ServerCommonPacketListenerImpl listener : ACTIVE_LISTENERS.values()) {
            try {
                listener.keepConnectionAliveAsync(currentTimeNs, currentTimeMs);
                if (!listener.connection.isConnected() || listener.processedDisconnect) {
                    ACTIVE_LISTENERS.remove(listener.connection, listener);
                }
            } catch (Throwable throwable) {
                ACTIVE_LISTENERS.remove(listener.connection, listener);
                LOGGER.error("Failed to run async keepalive for connection " + listener.connection.getRemoteAddress(), throwable);
            }
        }
    }
}
