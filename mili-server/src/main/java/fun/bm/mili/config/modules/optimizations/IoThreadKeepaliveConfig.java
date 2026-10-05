package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.network.IoThreadKeepalive;
import fun.bm.mili.network.IoThreadKeepaliveListener;
import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import me.earthme.luminol.enums.EnumConfigCategory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "io-thread-keepalive", comments = """
        在 Netty IO 线程上结算 keepalive 回包，而不是排进玩家所属 region 的 tick 队列。
        Folia 会把回包投递到 region tick 线程，导致繁忙 region 里的玩家 ping 被抬高几十毫秒，
        空闲 region 的玩家几乎只有纯网络延迟 —— 人多时的 ping 分化由此而来。
        开启后 tab 列表与 getPing() 读数只反映网络往返 + 服务端 IO，不再混进 region 排队。
        """)
public class IoThreadKeepaliveConfig implements IConfigModule {
    @ConfigInfo(name = "enabled", comments = "启用 IO 线程 keepalive 结算（关闭则回退到 Folia 原生的 region tick 处理）")
    public static boolean enabled = true;

    @DoNotLoad
    private static IoThreadKeepaliveListener listener = null;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exs) {
        if (enabled) {
            ensureListener();
        } else {
            IoThreadKeepalive.uninstallAll();
        }
    }

    /**
     * 幂等地确保监听器已注册，并对当前在线玩家补齐安装。
     * 配置加载可能早于 PluginManager 可用，因此 MiliOptimizations.init() 会在服务端就绪后重试一次。
     */
    public static void ensureListener() {
        if (listener == null) {
            listener = new IoThreadKeepaliveListener();
            listener.register();
        }
        // 配置热重载时同样需要覆盖已在线的玩家
        IoThreadKeepalive.installAll();
    }

    public static void releaseListener() {
        IoThreadKeepalive.uninstallAll();
        if (listener != null) {
            listener.unregister();
            listener = null;
        }
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        releaseListener();
    }
}
