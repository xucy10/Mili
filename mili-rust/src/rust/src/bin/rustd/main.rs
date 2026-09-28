//! rustd — Mili 常驻 Rust 守护进程（骨架，Phase 0）。
//!
//! 职责：承接重型/易崩后台任务（当前为演示命令），崩溃由 Java 侧
//! [`RustDaemonClient`]（Phase 1 接线）检测并以指数退避重启。
//!
//! 协议：stdin/stdout 帧协议 v1（`mili_optimizer::proto`，规范见
//! `docs/ARCHITECTURE.md` 附录 A）。**stdout 独占协议帧，日志一律走 stderr。**
//!
//! 退出码约定：0 = 正常（EOF/Shutdown 帧）；1 = IO 错误；2 = 参数/协议错误。
//!
//! 骨架阶段支持的 EXEC 命令：
//! - `echo <text>`：回显（联调用）
//! - `ext.list`：列出已注册内置扩展
//! - `ext.load <path>`：dlopen 外部动态库并完成 C ABI v1 校验（演示加载链路）

use std::env;
use std::io::{ErrorKind, Read, Write};
use std::path::PathBuf;
use std::process::ExitCode;

use mili_optimizer::extensions::abi;
use mili_optimizer::extensions::manager::{DynamicExtensionLoader, ExtensionRegistry};
use mili_optimizer::extensions::safety::catch_panic;
use mili_optimizer::proto::{self, DecodeError, Frame, Op};

const USAGE: &str = "用法: rustd [--protocol-version <n>]";

/// 解析 `--protocol-version` 参数；缺省视为最新版本。
fn parse_protocol_version(args: &[String]) -> Result<u16, String> {
    if args.is_empty() {
        return Ok(proto::PROTOCOL_VERSION);
    }
    if args[0] == "--protocol-version" {
        let v = args
            .get(1)
            .ok_or_else(|| "--protocol-version 缺少取值".to_string())?;
        return v
            .parse::<u16>()
            .map_err(|_| format!("--protocol-version 取值非法: {v}"));
    }
    Err(format!("未知参数: {}", args[0]))
}

fn main() -> ExitCode {
    let args: Vec<String> = env::args().skip(1).collect();
    let version = match parse_protocol_version(&args) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("[rustd] {e}");
            eprintln!("[rustd] {USAGE}");
            return ExitCode::from(2);
        }
    };
    if version != proto::PROTOCOL_VERSION {
        eprintln!(
            "[rustd] 协议版本不匹配: 期望 {}, 收到 {}",
            proto::PROTOCOL_VERSION,
            version
        );
        return ExitCode::from(2);
    }

    let mut registry = ExtensionRegistry::new();
    let mut loader = DynamicExtensionLoader::new();
    eprintln!(
        "[rustd] 启动 (protocol v{}, abi v{})",
        proto::PROTOCOL_VERSION,
        abi::ABI_VERSION
    );

    match serve(&mut registry, &mut loader) {
        Ok(()) => {
            eprintln!("[rustd] 正常退出");
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("[rustd] {e}");
            ExitCode::from(e.exit_code())
        }
    }
}

/// 事件循环结果。
enum ServeError {
    Io(String),
    Protocol(String),
}

impl ServeError {
    fn exit_code(&self) -> u8 {
        match self {
            ServeError::Io(_) => 1,
            ServeError::Protocol(_) => 2,
        }
    }
}

impl std::fmt::Display for ServeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ServeError::Io(msg) => write!(f, "IO 错误: {msg}"),
            ServeError::Protocol(msg) => write!(f, "协议错误: {msg}"),
        }
    }
}

/// 主循环：增量读 stdin → 解码全部完整帧 → 处理 → 回写。
fn serve(
    registry: &mut ExtensionRegistry,
    loader: &mut DynamicExtensionLoader,
) -> Result<(), ServeError> {
    let stdin = std::io::stdin();
    let mut reader = stdin.lock();
    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    let mut in_buf: Vec<u8> = Vec::with_capacity(8 * 1024);
    let mut out_buf: Vec<u8> = Vec::with_capacity(8 * 1024);
    let mut scratch = [0u8; 8192];

    loop {
        let n = match reader.read(&mut scratch) {
            Ok(0) => return Ok(()), // EOF：父进程关闭管道，优雅退出
            Ok(n) => n,
            Err(e) if e.kind() == ErrorKind::Interrupted => continue,
            Err(e) => return Err(ServeError::Io(e.to_string())),
        };
        in_buf.extend_from_slice(&scratch[..n]);

        // 处理缓冲区内所有完整帧
        loop {
            let (frame, consumed) = match proto::decode_frame(&in_buf) {
                Ok(ok) => ok,
                Err(DecodeError::Incomplete) => break,
                Err(e) => return Err(ServeError::Protocol(e.to_string())),
            };
            in_buf.drain(..consumed);

            match handle_frame(frame, registry, loader) {
                Flow::Reply(reply) => {
                    proto::encode_frame(&reply, &mut out_buf);
                }
                Flow::Stop => return Ok(()),
            }
        }

        if !out_buf.is_empty() {
            if let Err(e) = out.write_all(&out_buf).and_then(|_| out.flush()) {
                return Err(ServeError::Io(e.to_string()));
            }
            out_buf.clear();
        }
    }
}

/// 单帧处理结果。
enum Flow {
    /// 回写一帧。
    Reply(Frame),
    /// 处理 Shutdown，退出主循环。
    Stop,
}

fn handle_frame(
    frame: Frame,
    registry: &mut ExtensionRegistry,
    loader: &mut DynamicExtensionLoader,
) -> Flow {
    match frame.op {
        Op::Ping => {
            // 应用层版本握手：payload = u16 LE 协议版本，原样回显
            if frame.payload.len() == 2 {
                Flow::Reply(Frame::new(Op::Pong, frame.payload))
            } else {
                Flow::Reply(result_frame(false, "ping payload 必须为 2 字节版本号"))
            }
        }
        Op::Exec => {
            // 处理命令全程 panic 防护：转错误帧而非进程退出
            match catch_panic(|| handle_exec(&frame.payload, registry, loader)) {
                Ok(reply) => Flow::Reply(reply),
                Err(msg) => Flow::Reply(result_frame(false, &format!("内部错误: {msg}"))),
            }
        }
        Op::Shutdown => Flow::Stop,
        other_op => Flow::Reply(result_frame(
            false,
            &format!("未预期的操作码: {:?}", other_op),
        )),
    }
}

/// 处理 EXEC 命令。payload 为 UTF-8 命令串。
fn handle_exec(
    payload: &[u8],
    registry: &mut ExtensionRegistry,
    loader: &mut DynamicExtensionLoader,
) -> Frame {
    let Ok(text) = std::str::from_utf8(payload) else {
        return result_frame(false, "exec payload 必须为 UTF-8");
    };
    let text = text.trim();
    let mut parts = text.splitn(2, char::is_whitespace);
    let head = parts.next().unwrap_or("");
    let rest = parts.next().unwrap_or("").trim();

    match head {
        "echo" => result_frame(true, rest),
        "ext.load" => {
            if rest.is_empty() {
                return result_frame(false, "用法: ext.load <动态库路径>");
            }
            let path = PathBuf::from(rest);
            // # Safety: 路径来自受信父进程（Java 侧校验 extensions/ 目录），骨架阶段开放
            unsafe {
                match loader.open_and_verify(&path) {
                    Ok(desc) => {
                        let name = abi::read_c_str((*desc).name).unwrap_or("<unnamed>");
                        let ver = abi::read_c_str((*desc).version).unwrap_or("?");
                        result_frame(true, &format!("已加载 {name} {ver}（骨架阶段未注册回调）"))
                    }
                    Err(e) => result_frame(false, &format!("加载失败: {e}")),
                }
            }
        }
        "ext.unload" => {
            if rest.is_empty() {
                return result_frame(false, "用法: ext.unload <扩展名>");
            }
            // # Safety: 卸载后调用方不应再使用该扩展的描述符指针
            let success = unsafe { loader.unload(rest) };
            if success {
                result_frame(true, &format!("已卸载 {rest}"))
            } else {
                result_frame(false, &format!("未找到扩展: {rest}"))
            }
        }
        "ext.list" => {
            let names = loader.loaded_names();
            result_frame(true, &names.join(","))
        }
        other => result_frame(
            false,
            &format!("未知命令: {other}（骨架阶段支持 echo/ext.list/ext.load）"),
        ),
    }
}

/// 构造 RESULT 帧：`[1 字节状态码(0=ok,1=err)] + UTF-8 消息`。
fn result_frame(ok: bool, msg: &str) -> Frame {
    let mut payload = Vec::with_capacity(1 + msg.len());
    payload.push(if ok { 0 } else { 1 });
    payload.extend_from_slice(msg.as_bytes());
    Frame::new(Op::Result, payload)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_protocol_version() {
        // 注意：测试函数改名以避免遮蔽外层 parse_protocol_version
        assert_eq!(
            parse_protocol_version(&[]),
            Ok(proto::PROTOCOL_VERSION)
        );
        assert_eq!(
            parse_protocol_version(&["--protocol-version".to_string(), "1".to_string()]),
            Ok(1)
        );
        assert!(parse_protocol_version(&["--protocol-version".to_string()]).is_err());
        assert!(parse_protocol_version(&["--protocol-version".to_string(), "x".to_string()]).is_err());
        assert!(parse_protocol_version(&["--bogus".to_string()]).is_err());
    }

    #[test]
    fn result_frame_layout() {
        let f = result_frame(true, "ok");
        assert_eq!(f.op, Op::Result);
        assert_eq!(f.payload, vec![0, b'o', b'k']);
        let f = result_frame(false, "err");
        assert_eq!(f.payload[0], 1);
    }

    #[test]
    fn ping_pong_echo() {
        let mut registry = ExtensionRegistry::new();
        let mut loader = DynamicExtensionLoader::new();
        let ping = proto::ping_frame();
        match handle_frame(ping.clone(), &mut registry, &mut loader) {
            Flow::Reply(reply) => {
                assert_eq!(reply.op, Op::Pong);
                assert_eq!(reply.payload, ping.payload);
            }
            _ => panic!("expected pong reply"),
        }
        // 非 2 字节 payload 拒绝
        match handle_frame(Frame::new(Op::Ping, vec![1, 2, 3]), &mut registry, &mut loader) {
            Flow::Reply(reply) => assert_eq!(reply.payload[0], 1),
            _ => panic!("expected error result"),
        }
    }

    #[test]
    fn shutdown_stops_loop() {
        let mut registry = ExtensionRegistry::new();
        let mut loader = DynamicExtensionLoader::new();
        assert!(matches!(
            handle_frame(Frame::new(Op::Shutdown, vec![]), &mut registry, &mut loader),
            Flow::Stop
        ));
    }
}
