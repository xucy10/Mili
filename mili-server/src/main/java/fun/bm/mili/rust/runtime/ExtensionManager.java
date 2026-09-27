package fun.bm.mili.rust.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 外部动态库扩展管理门面（Phase 0 骨架）。
 * <p>
 * 加载路径：扩展动态库（C ABI v1，见 {@code mili-rust/include/mili_extension.h}）
 * <b>经 rustd 子进程加载</b>（{@code ext.load} 命令），而非 JVM 内 dlopen ——
 * 崩溃隔离是扩展框架的硬需求，JVM 进程内只保留纯数值热路径 JNI。
 * <p>
 * <b>Phase 0：</b>本类仅提供扫描与加载的最小门面，无生产调用者；
 * Phase 2 落地：abi_version 协商报告、扩展健康上报、unload 命令
 * （当前帧协议 v1 尚无 unload 操作码）、{@code /mili rust status} 观测命令。
 */
public final class ExtensionManager {

    /** 支持的动态库扩展名（按平台命名约定，与 stageRustBinary 的命名风格一致）。 */
    private static final String[] LIB_EXTENSIONS = {".dll", ".so", ".dylib"};

    private final RustDaemonClient daemon;
    private final Path extensionsDir;

    public ExtensionManager(RustDaemonClient daemon, Path extensionsDir) {
        this.daemon = daemon;
        this.extensionsDir = extensionsDir;
    }

    /** 扫描扩展目录下的动态库文件（不校验内容，仅按扩展名过滤）。 */
    public List<Path> scan() throws IOException {
        if (!Files.isDirectory(extensionsDir)) {
            return List.of();
        }
        List<Path> libs = new ArrayList<>();
        try (Stream<Path> files = Files.list(extensionsDir)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isLibraryFile)
                    .sorted()
                    .forEach(libs::add);
        }
        return libs;
    }

    /**
     * 经 rustd 加载扩展（{@code ext.load <绝对路径>}）。
     * <p>
     * rustd 侧完成 dlopen → 描述符符号查找 → ABI 版本与字段校验
     * （见 extensions::manager::DynamicExtensionLoader）。
     *
     * @return rustd 返回的 (ok, message)
     */
    public DaemonProtocol.RecordResult load(Path library) throws IOException {
        Path absolute = library.toAbsolutePath();
        if (!Files.isRegularFile(absolute)) {
            return new DaemonProtocol.RecordResult(false, "not a file: " + absolute);
        }
        byte[] cmd = ("ext.load " + absolute).getBytes(StandardCharsets.UTF_8);
        return daemon.exec(cmd);
    }

    /**
     * 经 rustd 卸载扩展（{@code ext.unload <name>}）。
     */
    public DaemonProtocol.RecordResult unload(String name) throws IOException {
        byte[] cmd = ("ext.unload " + name).getBytes(StandardCharsets.UTF_8);
        return daemon.exec(cmd);
    }

    /** 列出已加载扩展（{@code ext.list}）。 */
    public DaemonProtocol.RecordResult list() throws IOException {
        return daemon.exec("ext.list".getBytes(StandardCharsets.UTF_8));
    }

    private boolean isLibraryFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (String ext : LIB_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
