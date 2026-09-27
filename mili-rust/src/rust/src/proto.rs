//! Mili 守护进程帧协议 v1（规范源）。
//!
//! Java 侧镜像实现：`mili-server/src/main/java/fun/bm/mili/rust/runtime/DaemonProtocol.java`。
//! 完整规范：`docs/ARCHITECTURE.md` 附录 A。
//!
//! 传输层为 stdio：stdout 独占协议帧，任何日志一律写 stderr。
//!
//! 帧格式：
//!
//! ```text
//! ┌──────────────┬────────┬─────────────┐
//! │ u32 LE 长度  │ u8 op  │ payload     │
//! └──────────────┴────────┴─────────────┘
//! ```
//!
//! - 长度字段 = 帧体长度 = `1 + payload.len()`（不含 4 字节前缀自身），合法范围 `1..=1+MAX_PAYLOAD_LEN`。
//! - `PING` 的 payload 固定为 2 字节（u16 LE 协议版本），`PONG` 原样回显，构成应用层版本握手。
//! - `EXEC` 的 payload 为 UTF-8 命令串；`RESULT` 的 payload 为 `[1 字节状态码(0=ok,1=err)] + UTF-8 消息`。

use std::fmt;

/// 协议版本。不兼容变更时递增，握手阶段双侧校验。
pub const PROTOCOL_VERSION: u16 = 1;

/// 单帧 payload 上限：16 MiB，防御畸形帧耗尽内存。
pub const MAX_PAYLOAD_LEN: u32 = 16 * 1024 * 1024;

/// 帧头固定字节数（u32 LE 长度 + u8 op）。
pub const HEADER_LEN: usize = 5;

pub const OP_PING: u8 = 0x01;
pub const OP_PONG: u8 = 0x02;
pub const OP_EXEC: u8 = 0x10;
pub const OP_RESULT: u8 = 0x11;
pub const OP_SHUTDOWN: u8 = 0x7F;

/// 帧操作码。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Op {
    Ping,
    Pong,
    Exec,
    Result,
    Shutdown,
}

impl Op {
    pub fn as_u8(self) -> u8 {
        match self {
            Op::Ping => OP_PING,
            Op::Pong => OP_PONG,
            Op::Exec => OP_EXEC,
            Op::Result => OP_RESULT,
            Op::Shutdown => OP_SHUTDOWN,
        }
    }

    pub fn from_u8(v: u8) -> Option<Op> {
        match v {
            OP_PING => Some(Op::Ping),
            OP_PONG => Some(Op::Pong),
            OP_EXEC => Some(Op::Exec),
            OP_RESULT => Some(Op::Result),
            OP_SHUTDOWN => Some(Op::Shutdown),
            _ => None,
        }
    }
}

/// 一帧：操作码 + 负载。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Frame {
    pub op: Op,
    pub payload: Vec<u8>,
}

impl Frame {
    pub fn new(op: Op, payload: Vec<u8>) -> Self {
        Frame { op, payload }
    }
}

/// 解码错误。
///
/// `Incomplete` 表示数据尚不完整、需要继续读入（非致命）；
/// 其余变体均为协议级错误，调用方应终止连接。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DecodeError {
    /// 需要更多数据。
    Incomplete,
    /// payload 超过 [`MAX_PAYLOAD_LEN`]。
    TooLong { len: u32 },
    /// 未知操作码。
    InvalidOp { code: u8 },
}

impl fmt::Display for DecodeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            DecodeError::Incomplete => write!(f, "帧不完整"),
            DecodeError::TooLong { len } => {
                write!(f, "帧负载超限: {len} > {MAX_PAYLOAD_LEN}")
            }
            DecodeError::InvalidOp { code } => write!(f, "未知操作码: 0x{code:02X}"),
        }
    }
}

impl std::error::Error for DecodeError {}

/// 将一帧编码追加到 `out`。
pub fn encode_frame(frame: &Frame, out: &mut Vec<u8>) {
    let body_len = 1u32.checked_add(frame.payload.len() as u32).expect("帧体长度溢出");
    out.extend_from_slice(&body_len.to_le_bytes());
    out.push(frame.op.as_u8());
    out.extend_from_slice(&frame.payload);
}

/// 从缓冲区头部解码一帧。
///
/// 成功时返回 `(帧, 消费的总字节数)`；缓冲区可能包含后续帧，调用方应循环解码。
pub fn decode_frame(buf: &[u8]) -> Result<(Frame, usize), DecodeError> {
    if buf.len() < HEADER_LEN {
        return Err(DecodeError::Incomplete);
    }
    let body_len = u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]]);
    if body_len < 1 {
        // 长度必须至少包含 op 字节
        return Err(DecodeError::InvalidOp { code: buf[4] });
    }
    if body_len > 1 + MAX_PAYLOAD_LEN {
        return Err(DecodeError::TooLong { len: body_len });
    }
    let op_byte = buf[4];
    let op = Op::from_u8(op_byte).ok_or(DecodeError::InvalidOp { code: op_byte })?;
    let total = HEADER_LEN + (body_len as usize - 1);
    if buf.len() < total {
        return Err(DecodeError::Incomplete);
    }
    let payload = buf[HEADER_LEN..total].to_vec();
    Ok((Frame { op, payload }, total))
}

/// 构造 `PING` 帧（payload = u16 LE 协议版本）。
pub fn ping_frame() -> Frame {
    Frame::new(Op::Ping, PROTOCOL_VERSION.to_le_bytes().to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip() {
        for payload in [
            vec![],
            b"hello".to_vec(),
            vec![0u8; MAX_PAYLOAD_LEN as usize],
        ] {
            let frame = Frame::new(Op::Exec, payload.clone());
            let mut buf = Vec::new();
            encode_frame(&frame, &mut buf);
            let (decoded, consumed) = decode_frame(&buf).unwrap();
            assert_eq!(consumed, buf.len());
            assert_eq!(decoded, frame);
            assert_eq!(decoded.payload.len(), payload.len());
        }
    }

    #[test]
    fn incomplete_then_more() {
        let frame = Frame::new(Op::Result, b"abc".to_vec());
        let mut buf = Vec::new();
        encode_frame(&frame, &mut buf);
        assert_eq!(decode_frame(&buf[..3]), Err(DecodeError::Incomplete));
        assert_eq!(decode_frame(&buf[..5]), Err(DecodeError::Incomplete));
        assert_eq!(decode_frame(&buf[..6]), Err(DecodeError::Incomplete));
        let (decoded, consumed) = decode_frame(&buf).unwrap();
        assert_eq!(decoded, frame);
        assert_eq!(consumed, buf.len());
    }

    #[test]
    fn empty_buffer_is_incomplete() {
        assert_eq!(decode_frame(&[]), Err(DecodeError::Incomplete));
    }

    #[test]
    fn too_long_rejected() {
        let mut buf = Vec::new();
        let huge = 1 + MAX_PAYLOAD_LEN + 1;
        buf.extend_from_slice(&huge.to_le_bytes());
        buf.push(OP_EXEC);
        assert_eq!(
            decode_frame(&buf),
            Err(DecodeError::TooLong { len: huge })
        );
    }

    #[test]
    fn invalid_op_rejected() {
        let mut buf = Vec::new();
        buf.extend_from_slice(&1u32.to_le_bytes());
        buf.push(0xAB);
        assert_eq!(decode_frame(&buf), Err(DecodeError::InvalidOp { code: 0xAB }));
    }

    #[test]
    fn op_codes_match_java_side() {
        // 与 DaemonProtocol.java 的常量保持一致，改动需双侧同步
        assert_eq!(OP_PING, 0x01);
        assert_eq!(OP_PONG, 0x02);
        assert_eq!(OP_EXEC, 0x10);
        assert_eq!(OP_RESULT, 0x11);
        assert_eq!(OP_SHUTDOWN, 0x7F);
    }

    #[test]
    fn ping_version_round_trip() {
        let ping = ping_frame();
        assert_eq!(ping.op, Op::Ping);
        assert_eq!(ping.payload, PROTOCOL_VERSION.to_le_bytes());
        let mut buf = Vec::new();
        encode_frame(&ping, &mut buf);
        let (decoded, _) = decode_frame(&buf).unwrap();
        assert_eq!(decoded, ping);
    }
}
