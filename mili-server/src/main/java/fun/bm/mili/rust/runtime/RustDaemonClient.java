package fun.bm.mili.rust.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * rustd 常驻子进程客户端（Phase 0 骨架，零调用者）。
 * <p>
 * 职责：启动 rustd → 应用层版本握手 → 提供 EXEC 请求-响应；
 * 心跳判死 + 指数退避重启；连续失败达上限后进入禁用态（降级铁律：
 * rustd 不可用只影响后台任务通道，服务器本体继续运行）。
 * <p>
 * 协议见 {@link DaemonProtocol}；重启/心跳参数与 docs/ARCHITECTURE.md 5.3 节一致。
 * <p>
 * <b>Phase 0 约束：</b>本类无任何静态初始化副作用、无静态调用者，
 * 不会被生产代码引用；Phase 1 由 {@link RustRuntime#init} 接线。
 */
public final class RustDaemonClient implements Closeable {

    /** 心跳间隔（ms）。 */
    static final int HEARTBEAT_INTERVAL_MS = 30_000;
    /** 连续未收到 PONG 的次数上限，判死后进入重启流程。 */
    static final int HEARTBEAT_MAX_MISS = 3;
    /** 重启退避基数（ms）：1s、2s、4s…… */
    static final long RESTART_BACKOFF_BASE_MS = 1_000;
    /** 重启退避上限（ms）。 */
    static final long RESTART_BACKOFF_MAX_MS = 60_000;
    /** 连续重启失败上限，达到后转禁用。 */
    static final int RESTART_MAX_CONSECUTIVE = 5;

    private final Path binary;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Process process;
    private OutputStream stdin;
    private InputStream stdout;
    private Thread heartbeatThread;
    private int consecutiveFailures;

    public RustDaemonClient(Path binary) {
        this.binary = binary;
    }

    /**
     * 启动 rustd 并完成版本握手（PING → PONG 版本回显）。
     *
     * @throws IOException 进程启动失败或握手失败
     */
    public synchronized void start() throws IOException {
        ensureNotClosed();
        destroyProcess(); // 幂等：清理可能残留的旧进程
        ProcessBuilder pb = new ProcessBuilder(
                binary.toAbsolutePath().toString(),
                "--protocol-version", Integer.toString(DaemonProtocol.PROTOCOL_VERSION));
        // stderr 直接继承到服务端日志；stdout 为协议帧独占（规范约定）
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        process = p;
        stdin = new BufferedOutputStream(p.getOutputStream());
        stdout = new BufferedInputStream(p.getInputStream());

        DaemonProtocol.writeFrame(stdin, DaemonProtocol.OP_PING, DaemonProtocol.pingPayload());
        DaemonProtocol.Frame reply = DaemonProtocol.readFrame(stdout);
        if (!DaemonProtocol.isValidPong(reply)) {
            destroyProcess();
            throw new IOException("rustd handshake failed: unexpected reply op=" + reply.op());
        }
        consecutiveFailures = 0;
        startHeartbeat();
    }

    /**
     * 同步 EXEC 请求-响应（骨架阶段：阻塞式，供低频后台任务使用）。
     * <p>
     * Phase 1 扩展：请求队列 + 超时（execTimeout），并区分命令优先级。
     *
     * @param commandPayload UTF-8 命令串（如 {@code "ext.list"}）
     * @return RESULT 帧的 (ok, message)
     */
    public synchronized DaemonProtocol.RecordResult exec(byte[] commandPayload) throws IOException {
        ensureNotClosed();
        requireAlive();
        DaemonProtocol.writeFrame(stdin, DaemonProtocol.OP_EXEC, commandPayload);
        DaemonProtocol.Frame reply = DaemonProtocol.readFrame(stdout);
        return DaemonProtocol.decodeResult(reply);
    }

    /** 是否处于可用状态（进程存活且已握手）。 */
    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    /** 连续重启失败次数（观测用）。 */
    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * 优雅关闭：尽力发送 SHUTDOWN 帧，随后 destroy 兜底，并停止心跳线程。
     */
    @Override
    public synchronized void close() {
        closed.set(true);
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
        if (stdin != null) {
            try {
                DaemonProtocol.writeFrame(stdin, DaemonProtocol.OP_SHUTDOWN, new byte[0]);
                stdin.flush();
            } catch (IOException ignored) {
                // 进程可能已死，走 destroy 兜底
            }
        }
        destroyProcess();
    }

    // ------------------------------------------------------------------
    // 内部实现（骨架阶段单线程假设；Phase 1 接入调度后加锁细化）
    // ------------------------------------------------------------------

    private void startHeartbeat() {
        heartbeatThread = new Thread(this::heartbeatLoop, "mili-rustd-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void heartbeatLoop() {
        int miss = 0;
        try {
            while (!closed.get()) {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                if (pingOnce()) {
                    miss = 0;
                } else if (++miss >= HEARTBEAT_MAX_MISS) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!closed.get()) {
            onProcessDead("heartbeat miss x" + miss);
        }
    }

    /** 单次 PING 探测；任何异常按失败计。 */
    private boolean pingOnce() {
        synchronized (this) {
            if (closed.get() || process == null || !process.isAlive()) {
                return false;
            }
            try {
                DaemonProtocol.writeFrame(stdin, DaemonProtocol.OP_PING, DaemonProtocol.pingPayload());
                return DaemonProtocol.isValidPong(DaemonProtocol.readFrame(stdout));
            } catch (IOException e) {
                return false;
            }
        }
    }

    /** 判死入口：指数退避重启；连续失败达上限后放弃（降级为仅 JNI）。 */
    private void onProcessDead(String reason) {
        synchronized (this) {
            if (closed.get()) {
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures > RESTART_MAX_CONSECUTIVE) {
                // 降级铁律：禁用 rustd 通道，服务器继续运行（ARCHITECTURE 5.3）
                destroyProcess();
                return;
            }
        }
        long backoff = Math.min(
                RESTART_BACKOFF_BASE_MS << Math.min(consecutiveFailures - 1, 16),
                RESTART_BACKOFF_MAX_MS);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (closed.get()) {
            return;
        }
        try {
            start();
        } catch (IOException e) {
            onProcessDead("restart failed: " + e.getMessage());
        }
    }

    private void requireAlive() throws IOException {
        if (process == null || !process.isAlive()) {
            throw new IOException("rustd is not running (restart pending or disabled)");
        }
    }

    private void destroyProcess() {
        if (process != null) {
            process.destroy(); // 优雅；进程不退由 destroyForcibly 兜底
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process = null;
            stdin = null;
            stdout = null;
        }
    }

    private void ensureNotClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("client closed");
        }
    }

    // 保留 Arrays 引用，供 Phase 1 的 exec 超时比较逻辑使用
    @SuppressWarnings("unused")
    private static boolean samePayload(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
