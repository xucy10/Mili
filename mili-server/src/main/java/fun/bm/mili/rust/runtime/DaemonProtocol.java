package fun.bm.mili.rust.runtime;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * rustd 帧协议 v1 的 Java 侧实现。
 * <p>
 * 规范源（Rust）：{@code mili-rust/src/rust/src/proto.rs}；
 * 完整规范：docs/ARCHITECTURE.md 附录 A。
 * <p>
 * 帧格式：
 * <pre>
 * ┌──────────────┬────────┬─────────────┐
 * │ u32 LE 长度  │ u8 op  │ payload     │
 * └──────────────┴────────┴─────────────┘
 * </pre>
 * 长度 = 帧体长度 = 1 + payload.length（不含 4 字节前缀自身）。
 * <b>双侧字段/语义必须与 proto.rs 同步修改</b>（proto.rs 中有对应用断言）。
 */
public final class DaemonProtocol {

    /** 协议版本。不兼容变更时递增，握手阶段双侧校验。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单帧 payload 上限：16 MiB，防御畸形帧耗尽内存。 */
    public static final int MAX_PAYLOAD_LEN = 16 * 1024 * 1024;

    /** 帧头固定字节数（u32 LE 长度 + u8 op）。 */
    public static final int HEADER_LEN = 5;

    public static final byte OP_PING = 0x01;
    public static final byte OP_PONG = 0x02;
    public static final byte OP_EXEC = 0x10;
    public static final byte OP_RESULT = 0x11;
    public static final byte OP_SHUTDOWN = (byte) 0x7F;

    /** RESULT payload 的状态码：成功。 */
    public static final byte STATUS_OK = 0;
    /** RESULT payload 的状态码：失败。 */
    public static final byte STATUS_ERR = 1;

    private DaemonProtocol() {}

    /** 一帧：操作码 + 负载。 */
    public record Frame(byte op, byte[] payload) {
    }

    /**
     * PING 负载：u16 LE 协议版本（PONG 原样回显，构成应用层版本握手）。
     */
    public static byte[] pingPayload() {
        return new byte[] {
                (byte) (PROTOCOL_VERSION & 0xFF),
                (byte) ((PROTOCOL_VERSION >>> 8) & 0xFF)
        };
    }

    /** 编码并写出一帧（不 flush，由调用方批量控制）。 */
    public static void writeFrame(OutputStream out, byte op, byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD_LEN) {
            throw new IOException("payload too large: " + payload.length + " > " + MAX_PAYLOAD_LEN);
        }
        int bodyLen = 1 + payload.length;
        out.write(bodyLen & 0xFF);
        out.write((bodyLen >>> 8) & 0xFF);
        out.write((bodyLen >>> 16) & 0xFF);
        out.write((bodyLen >>> 24) & 0xFF);
        out.write(op);
        out.write(payload);
    }

    /** 从流中读取一帧；流提前结束抛 {@link EOFException}。 */
    public static Frame readFrame(InputStream in) throws IOException {
        byte[] header = readFully(in, HEADER_LEN);
        long bodyLen = (header[0] & 0xFFL)
                | ((header[1] & 0xFFL) << 8)
                | ((header[2] & 0xFFL) << 16)
                | ((header[3] & 0xFFL) << 24);
        if (bodyLen < 1) {
            throw new IOException("protocol error: zero-length frame body");
        }
        int payloadLen = (int) (bodyLen - 1);
        if (payloadLen > MAX_PAYLOAD_LEN) {
            throw new IOException("protocol error: payload too large: " + payloadLen);
        }
        byte op = header[4];
        byte[] payload = readFully(in, payloadLen);
        return new Frame(op, payload);
    }

    /** 解析 RESULT 帧负载：[1 字节状态码] + UTF-8 消息。返回 (ok, message)。 */
    public static RecordResult decodeResult(Frame frame) {
        if (frame.op() != OP_RESULT || frame.payload().length < 1) {
            return new RecordResult(false, "not a RESULT frame");
        }
        boolean ok = frame.payload()[0] == STATUS_OK;
        String message = new String(frame.payload(), 1, frame.payload().length - 1,
                java.nio.charset.StandardCharsets.UTF_8);
        return new RecordResult(ok, message);
    }

    /** RESULT 解码结果。 */
    public record RecordResult(boolean ok, String message) {
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new EOFException("stream ended mid-frame (read " + off + "/" + n + ")");
            }
            off += r;
        }
        return buf;
    }

    // ------------------------------------------------------------------
    // 供测试/观测使用的小工具
    // ------------------------------------------------------------------

    /** 判断是否为版本回显正确的 PONG 帧。 */
    public static boolean isValidPong(Frame frame) {
        return frame.op() == OP_PONG && Arrays.equals(frame.payload(), pingPayload());
    }
}
