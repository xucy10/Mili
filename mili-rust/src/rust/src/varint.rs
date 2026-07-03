/// Minecraft Protocol VarInt and VarLong encoding/decoding.
///
/// All encode functions return **stack-allocated arrays** with the actual byte
/// count, eliminating heap allocations entirely (VarInt ≤ 5 bytes, VarLong ≤ 10).
///
/// VarInt: 1-5 bytes, MSB indicates continuation.
/// VarLong: 1-10 bytes, same scheme for i64.

/// Encode an i32 as a VarInt (unsigned LEB128 of the bit pattern).
/// Returns a stack-allocated `[u8; 5]` and the actual number of bytes written.
///
/// Zero heap allocations.
pub fn encode_varint(value: i32) -> ([u8; 5], usize) {
    let mut buf = [0u8; 5];
    let mut v = value as u32;
    let mut i = 0;
    loop {
        if (v & !0x7F) == 0 {
            buf[i] = v as u8;
            i += 1;
            break;
        }
        buf[i] = ((v & 0x7F) | 0x80) as u8;
        i += 1;
        v >>= 7;
    }
    (buf, i)
}

/// Decode a VarInt from a byte slice.
/// Returns `(value, bytes_consumed)` or an error if the VarInt exceeds 5 bytes.
pub fn decode_varint(data: &[u8]) -> Result<(i32, usize), String> {
    let mut value = 0u32;
    for (i, &byte) in data.iter().enumerate().take(5) {
        value |= ((byte as u32) & 0x7F) << (i * 7);
        if (byte & 0x80) == 0 {
            return Ok((value as i32, i + 1));
        }
    }
    Err("varint-error:too-long".to_string())
}

/// Number of bytes required to encode a value as VarInt.
///
/// Pure arithmetic — no encoding, no allocation.
pub fn varint_size(value: i32) -> usize {
    let v = value as u32;
    if v & 0xFFFFFF80 == 0 {
        1
    } else if v & 0xFFFFC000 == 0 {
        2
    } else if v & 0xFFE00000 == 0 {
        3
    } else if v & 0xF0000000 == 0 {
        4
    } else {
        5
    }
}

/// Encode an i64 as a VarLong.
/// Returns a stack-allocated `[u8; 10]` and the actual number of bytes written.
///
/// Zero heap allocations.
pub fn encode_varlong(value: i64) -> ([u8; 10], usize) {
    let mut buf = [0u8; 10];
    let mut v = value as u64;
    let mut i = 0;
    loop {
        if (v & !0x7F) == 0 {
            buf[i] = v as u8;
            i += 1;
            break;
        }
        buf[i] = ((v & 0x7F) | 0x80) as u8;
        i += 1;
        v >>= 7;
    }
    (buf, i)
}

/// Decode a VarLong from a byte slice.
pub fn decode_varlong(data: &[u8]) -> Result<(i64, usize), String> {
    let mut value = 0u64;
    for (i, &byte) in data.iter().enumerate().take(10) {
        value |= ((byte as u64) & 0x7F) << (i * 7);
        if (byte & 0x80) == 0 {
            return Ok((value as i64, i + 1));
        }
    }
    Err("varlong-error:too-long".to_string())
}

/// Number of bytes required to encode a value as VarLong.
///
/// Pure arithmetic — no encoding, no allocation.
pub fn varlong_size(value: i64) -> usize {
    let v = value as u64;
    if v & 0xFFFFFFFFFFFFFF80 == 0 {
        1
    } else if v & 0xFFFFFFFFFFFFC000 == 0 {
        2
    } else if v & 0xFFFFFFFFFFE00000 == 0 {
        3
    } else if v & 0xFFFFFFFFF0000000 == 0 {
        4
    } else if v & 0xFFFFFFF800000000 == 0 {
        5
    } else if v & 0xFFFFFC0000000000 == 0 {
        6
    } else if v & 0xFFFE000000000000 == 0 {
        7
    } else if v & 0xFF00000000000000 == 0 {
        8
    } else if v & 0x8000000000000000 == 0 {
        9
    } else {
        10
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn varint_zero() {
        let (buf, len) = encode_varint(0);
        assert_eq!(&buf[..len], &[0]);
        assert_eq!(varint_size(0), 1);
    }

    #[test]
    fn varint_one_byte() {
        let (buf, len) = encode_varint(127);
        assert_eq!(&buf[..len], &[0x7F]);
    }

    #[test]
    fn varint_two_bytes() {
        let (buf, len) = encode_varint(128);
        assert_eq!(&buf[..len], &[0x80, 0x01]);
    }

    #[test]
    fn varint_max_i32() {
        let (_, len) = encode_varint(i32::MAX);
        assert_eq!(len, 5);
        assert_eq!(varint_size(i32::MAX), 5);
    }

    #[test]
    fn varint_roundtrip() {
        let test_values = [0, 1, 127, 128, 255, 256, 10000, i32::MAX, -1, i32::MIN];
        for &val in &test_values {
            let (buf, len) = encode_varint(val);
            let (decoded, size) = decode_varint(&buf[..len]).unwrap();
            assert_eq!(decoded, val, "roundtrip failed for {}", val);
            assert_eq!(size, len);
        }
    }

    #[test]
    fn varlong_roundtrip() {
        let test_values = [0i64, 1, 127, 128, 255, 256, 10000, i64::MAX, -1, i64::MIN];
        for &val in &test_values {
            let (buf, len) = encode_varlong(val);
            let (decoded, size) = decode_varlong(&buf[..len]).unwrap();
            assert_eq!(decoded, val, "roundtrip failed for {}", val);
            assert_eq!(size, len);
        }
    }

    #[test]
    fn decode_varint_rejects_too_long() {
        let data = [0xFF; 5];
        assert!(decode_varint(&data).is_err());
    }

    #[test]
    fn varint_size_matches_encode() {
        for v in [0, 1, 127, 128, 255, 256, 16383, 16384, 2097151, 2097152, 268435455, -1, i32::MIN] {
            let (_, len) = encode_varint(v);
            assert_eq!(varint_size(v), len, "size mismatch for {}", v);
        }
    }

    #[test]
    fn varlong_size_matches_encode() {
        for v in [0i64, 1, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, -1, i64::MIN] {
            let (_, len) = encode_varlong(v);
            assert_eq!(varlong_size(v), len, "size mismatch for {}", v);
        }
    }
}
