package fun.bm.mili.rust.runtime;

import java.nio.file.Path;

import fun.bm.mili.rust.RustBridge;

/**
 * Rust 运行时统一门面（Phase 0 骨架，零接线）。
 * <p>
 * 目标架构中承担三件事：
 * <ol>
 *   <li>JNI 热路径通道的可用性查询（{@link #jniAvailable()}）；</li>
 *   <li>rustd 常驻子进程（后台任务通道）的生命周期持有（{@link #daemon()}）；</li>
 *   <li>统一状态视图（{@link State}），供观测命令（Phase 2 的 /mili rust status）读取。</li>
 * </ol>
 * <p>
 * <b>Phase 0 约束：</b>本类不得被生产代码引用，{@link #init(Path)} 为空操作，
 * 不触发 {@link RustBridge#load()}、不启动子进程 —— 避免影响现有
 * 配置引擎（TomlConfigData → ConfigsInstance）的启动时序。
 * Phase 1 接线点：在 ConfigsInstance 配置加载完成后调用 {@link #init(Path)}。
 * <p>
 * 设计文档：docs/ARCHITECTURE.md 第 5 章（Rust 计算层）。
 */
public final class RustRuntime {

    /** 运行时状态机。 */
    public enum State {
        /** 未初始化（Phase 0 的常态）。 */
        UNINITIALIZED,
        /** JNI 库可用、rustd 未启用。 */
        JNI_ONLY,
        /** rustd 启动/握手中。 */
        DAEMON_STARTING,
        /** rustd 正常运行。 */
        DAEMON_RUNNING,
        /** rustd 连续重启失败，已禁用（仅 JNI 通道可用）。 */
        DAEMON_FAILED_DISABLED
    }

    private static volatile State state = State.UNINITIALIZED;
    private static volatile RustDaemonClient daemon;

    private RustRuntime() {}

    /** 当前状态（观测用，不产生副作用）。 */
    public static State state() {
        return state;
    }

    /** JNI 通道可用性（仅读 {@link RustBridge#isLoaded()} 标志，无副作用）。 */
    public static boolean jniAvailable() {
        return RustBridge.isLoaded();
    }

    /** rustd 客户端；未启用时返回 {@code null}。 */
    public static RustDaemonClient daemon() {
        return daemon;
    }

    /**
     * 初始化运行时。
     * <p>
     * Phase 1 实装：启动 CrossRegionHelper dispatcher（若配置启用）+ 尝试启动 rustd daemon。
     * 由配置模块 onLoaded 触发（确保配置已加载完毕）。
     * 所有初始化失败均降级——不阻止服务器启动。
     *
     * @param rustdBinary rustd 可执行文件路径（从 classpath /rust/rustd/ 提取；null 跳过 daemon）
     */
    public static synchronized void init(Path rustdBinary) {
        // 1. 启动跨区辅助（配置驱动）
        try {
            if (fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig.enabled) {
                fun.bm.mili.utils.CrossRegionHelper.init();
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getClassLogger().warn(
                "[Mili] CrossRegionHelper init failed: {}", e.getMessage()
            );
        }

        // 2. JNI 可用性
        boolean jniOk = RustBridge.isLoaded();
        state = jniOk ? State.JNI_ONLY : State.UNINITIALIZED;

        // 3. 尝试启动 rustd daemon（非阻塞，失败降级为 JNI_ONLY）
        if (jniOk && rustdBinary != null) {
            try {
                daemon = new RustDaemonClient(rustdBinary);
                daemon.start();
                state = State.DAEMON_RUNNING;
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getClassLogger().warn(
                    "[Mili] rustd daemon failed to start, JNI-only mode: {}", e.getMessage()
                );
                daemon = null;
                state = State.JNI_ONLY;
            }
        }
    }

    /**
     * 从 classpath 提取 rustd 二进制到临时文件。
     *
     * @return 临时文件路径，或 null（未找到/不支持平台）
     */
    public static Path extractRustdBinary() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String libName;
        if (os.contains("win")) {
            libName = "rustd-x86_64-pc-windows-gnu.exe";
        } else if (os.contains("mac")) {
            libName = arch.contains("aarch64") || arch.contains("arm64")
                    ? "rustd-aarch64-apple-darwin" : "rustd-x86_64-apple-darwin";
        } else {
            libName = arch.contains("aarch64") || arch.contains("arm64")
                    ? "rustd-aarch64-unknown-linux-gnu" : "rustd-x86_64-unknown-linux-gnu";
        }
        try {
            var is = RustRuntime.class.getResourceAsStream("/rust/rustd/" + libName);
            if (is == null) return null;
            Path tmp = java.nio.file.Files.createTempFile("mili-rustd-", null);
            tmp.toFile().deleteOnExit();
            tmp.toFile().setExecutable(true);
            java.nio.file.Files.copy(is, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** 关闭运行时（服务端停机时调用）。 */
    public static synchronized void shutdown() {
        if (daemon != null) {
            daemon.close();
            daemon = null;
        }
        try {
            fun.bm.mili.utils.CrossRegionHelper.shutdown();
        } catch (Exception ignored) {}
        state = State.UNINITIALIZED;
    }
}
