use std::convert::TryInto;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum NbtTagType {
    End = 0,
    Byte = 1,
    Short = 2,
    Int = 3,
    Long = 4,
    Float = 5,
    Double = 6,
    ByteArray = 7,
    String = 8,
    List = 9,
    Compound = 10,
    IntArray = 11,
    LongArray = 12,
}

impl NbtTagType {
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            0 => Some(NbtTagType::End),
            1 => Some(NbtTagType::Byte),
            2 => Some(NbtTagType::Short),
            3 => Some(NbtTagType::Int),
            4 => Some(NbtTagType::Long),
            5 => Some(NbtTagType::Float),
            6 => Some(NbtTagType::Double),
            7 => Some(NbtTagType::ByteArray),
            8 => Some(NbtTagType::String),
            9 => Some(NbtTagType::List),
            10 => Some(NbtTagType::Compound),
            11 => Some(NbtTagType::IntArray),
            12 => Some(NbtTagType::LongArray),
            _ => None,
        }
    }
}

/// Parse a hex string into raw bytes. Accepts optional `0x` prefix and whitespace.
///
/// Zero intermediate `String` allocations — parses directly from byte slice.
pub fn parse_hex(input: &str) -> Result<Vec<u8>, String> {
    let raw = input.as_bytes();
    if raw.is_empty() {
        return Ok(Vec::new());
    }

    let mut i = 0;
    // Skip optional 0x/0X prefix
    if raw.len() >= 2 && raw[0] == b'0' && (raw[1] == b'x' || raw[1] == b'X') {
        i = 2;
    }

    let mut bytes = Vec::with_capacity(raw.len() / 2);
    let mut hi = None;
    while i < raw.len() {
        let b = raw[i];
        if b.is_ascii_whitespace() {
            i += 1;
            continue;
        }
        let nibble = hex_nibble(b).ok_or_else(|| format!("nbt-error:invalid-hex:0x{:02X}", b))?;
        if let Some(h) = hi {
            bytes.push((h << 4) | nibble);
            hi = None;
        } else {
            hi = Some(nibble);
        }
        i += 1;
    }

    if hi.is_some() {
        return Err("nbt-error:odd-hex-length".to_string());
    }
    Ok(bytes)
}

fn hex_nibble(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

/// Lightweight NBT stream scanner.
///
/// Walks through NBT bytes without fully materializing the tree.
/// Returns `(total_bytes_consumed, max_depth, tag_count)`.
///
/// - `total_bytes_consumed`: number of bytes parsed
/// - `max_depth`: deepest nesting level (root compound with a byte inside is depth 3)
/// - `tag_count`: total number of tags encountered (including End tags)
///
/// Does NOT validate the full structure beyond basic bounds checks.
pub fn scan_nbt(data: &[u8]) -> Result<(usize, usize, usize), String> {
    if data.is_empty() {
        return Ok((0, 0, 0));
    }
    let mut cursor = Cursor::new(data);
    let depth = scan_tag(&mut cursor)?;
    Ok((cursor.pos, depth.max_depth, depth.tag_count))
}

struct Cursor<'a> {
    data: &'a [u8],
    pos: usize,
}

struct DepthResult {
    max_depth: usize,
    tag_count: usize,
}

impl<'a> Cursor<'a> {
    fn new(data: &'a [u8]) -> Self {
        Cursor { data, pos: 0 }
    }

    fn remaining(&self) -> usize {
        self.data.len().saturating_sub(self.pos)
    }

    fn read_u8(&mut self) -> Result<u8, String> {
        self.data
            .get(self.pos)
            .copied()
            .ok_or_else(|| format!("nbt-error:unexpected-eof:u8:{}", self.pos))
            .map(|b| {
                self.pos += 1;
                b
            })
    }

    fn read_be_u16(&mut self) -> Result<u16, String> {
        if self.remaining() < 2 {
            return Err(format!("nbt-error:unexpected-eof:u16:{}", self.pos));
        }
        let val = u16::from_be_bytes(self.data[self.pos..self.pos + 2].try_into().unwrap());
        self.pos += 2;
        Ok(val)
    }

    fn read_be_i32(&mut self) -> Result<i32, String> {
        if self.remaining() < 4 {
            return Err(format!("nbt-error:unexpected-eof:i32:{}", self.pos));
        }
        let val = i32::from_be_bytes(self.data[self.pos..self.pos + 4].try_into().unwrap());
        self.pos += 4;
        Ok(val)
    }

    fn skip_bytes(&mut self, n: usize) -> Result<(), String> {
        if self.remaining() < n {
            return Err(format!("nbt-error:unexpected-eof:skip:{}:{}", n, self.pos));
        }
        self.pos += n;
        Ok(())
    }
}

fn scan_tag(cursor: &mut Cursor) -> Result<DepthResult, String> {
    let tag_type = cursor.read_u8()?;
    let tag = NbtTagType::from_u8(tag_type)
        .ok_or_else(|| format!("nbt-error:unknown-tag-type:{}", tag_type))?;

    if tag == NbtTagType::End {
        return Ok(DepthResult {
            max_depth: 0,
            tag_count: 1,
        });
    }

    let name_len = cursor.read_be_u16()? as usize;
    cursor.skip_bytes(name_len)?;

    let payload = scan_payload(cursor, tag)?;
    Ok(DepthResult {
        max_depth: payload.max_depth + 1,
        tag_count: payload.tag_count + 1,
    })
}

fn scan_payload(cursor: &mut Cursor, tag: NbtTagType) -> Result<DepthResult, String> {
    match tag {
        NbtTagType::End => Ok(DepthResult {
            max_depth: 0,
            tag_count: 0,
        }),
        NbtTagType::Byte => {
            cursor.skip_bytes(1)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::Short => {
            cursor.skip_bytes(2)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::Int => {
            cursor.skip_bytes(4)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::Long => {
            cursor.skip_bytes(8)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::Float => {
            cursor.skip_bytes(4)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::Double => {
            cursor.skip_bytes(8)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::ByteArray => {
            let len = cursor.read_be_i32()? as usize;
            cursor.skip_bytes(len)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::String => {
            let len = cursor.read_be_u16()? as usize;
            cursor.skip_bytes(len)?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::List => {
            let elem_type = cursor.read_u8()?;
            let len = cursor.read_be_i32()? as usize;
            if len == 0 {
                return Ok(DepthResult {
                    max_depth: 0,
                    tag_count: 0,
                });
            }
            let elem_tag = NbtTagType::from_u8(elem_type).ok_or_else(|| {
                format!("nbt-error:unknown-list-elem-type:{}", elem_type)
            })?;
            let mut max_depth = 0usize;
            let mut total_tags = 0usize;
            for _ in 0..len {
                let depth = scan_payload(cursor, elem_tag)?;
                max_depth = max_depth.max(depth.max_depth);
                total_tags += depth.tag_count;
            }
            Ok(DepthResult {
                max_depth: max_depth + 1,
                tag_count: total_tags,
            })
        }
        NbtTagType::Compound => {
            let mut max_depth = 0usize;
            let mut total_tags = 0usize;
            loop {
                if cursor.remaining() == 0 {
                    return Err("nbt-error:unexpected-eof:compound".to_string());
                }
                let next_type = cursor.read_u8()?;
                if next_type == 0 {
                    total_tags += 1;
                    break;
                }
                cursor.pos -= 1;
                let depth = scan_tag(cursor)?;
                max_depth = max_depth.max(depth.max_depth);
                total_tags += depth.tag_count;
            }
            Ok(DepthResult {
                max_depth: max_depth + 1,
                tag_count: total_tags,
            })
        }
        NbtTagType::IntArray => {
            let len = cursor.read_be_i32()? as usize;
            cursor.skip_bytes(len.saturating_mul(4))?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
        NbtTagType::LongArray => {
            let len = cursor.read_be_i32()? as usize;
            cursor.skip_bytes(len.saturating_mul(8))?;
            Ok(DepthResult {
                max_depth: 0,
                tag_count: 0,
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn minimal_compound() -> Vec<u8> {
        vec![0x0A, 0x00, 0x00, 0x00]
    }

    #[test]
    fn scan_empty_compound() {
        let result = scan_nbt(&minimal_compound()).unwrap();
        assert_eq!(result.0, 4); // total bytes
        assert_eq!(result.1, 2); // depth: compound + end
        assert_eq!(result.2, 2); // tags: compound + end
    }

    #[test]
    fn scan_rejects_odd_hex() {
        assert!(parse_hex("abc").is_err());
    }

    #[test]
    fn scan_ignores_whitespace_in_hex() {
        let result = parse_hex("0A 00 00 00").unwrap();
        assert_eq!(result, minimal_compound());
    }

    #[test]
    fn scan_empty_input() {
        let result = scan_nbt(&[]).unwrap();
        assert_eq!(result, (0, 0, 0));
    }
}
