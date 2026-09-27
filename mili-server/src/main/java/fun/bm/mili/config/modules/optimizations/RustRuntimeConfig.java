package fun.bm.mili.config.modules.optimizations;

import fun.bm.mili.MiliOptimizations;
import fun.bm.mili.command.MiliRustStatusCommand;
import fun.bm.mili.command.MiliTogglesCommand;
import fun.bm.mili.rust.runtime.RustRuntime;
import fun.bm.mili.rust.TomlConfigData;
import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.config.flags.DoNotLoad;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Rust 运行时与 Mili 优化系统初始化接线模块。
 * <p>
 * onLoaded 在配置加载完毕后被调用（可能跑在 common pool 线程），
 * 此处只做无世界接触的初始化：RustRuntime（JNI/daemon）、CrossRegionHelper、命令注册。
 * MiliOptimizations.init(Plugin) 需要 Bukkit Plugin 实例，推迟到 server 启动后由补丁接线。
 */
@ConfigClassInfo(category = me.earthme.luminol.enums.EnumConfigCategory.OPTIMIZATION, name = "rust_runtime")
public class RustRuntimeConfig implements IConfigModule {

    @ConfigInfo(name = "enabled", comments = "启用 Rust 运行时（JNI 热路径 + rustd 后台进程）")
    public static boolean enabled = true;

    @ConfigInfo(name = "daemon_enabled", comments = "启用 rustd 常驻子进程（后台任务通道）")
    public static boolean daemonEnabled = true;

    @DoNotLoad
    private static MiliRustStatusCommand rustStatusCommand = null;
    @DoNotLoad
    private static MiliTogglesCommand togglesCommand = null;

    @Override
    public void onLoaded(TomlConfigData configInstance, @Nullable Set<Exception> exceptions) {
        if (!enabled) return;

        // 初始化 Rust 运行时（JNI + daemon + CrossRegionHelper）
        java.nio.file.Path rustdBinary = daemonEnabled ? RustRuntime.extractRustdBinary() : null;
        RustRuntime.init(rustdBinary);

        // 注册观测命令
        if (rustStatusCommand == null) {
            rustStatusCommand = new MiliRustStatusCommand();
        }
        rustStatusCommand.register();
        if (togglesCommand == null) {
            togglesCommand = new MiliTogglesCommand();
        }
        togglesCommand.register();
    }

    @Override
    public void onUnloaded(TomlConfigData configInstance) {
        RustRuntime.shutdown();
        if (rustStatusCommand != null) {
            rustStatusCommand.unregister();
        }
        if (togglesCommand != null) {
            togglesCommand.unregister();
        }
    }
}
