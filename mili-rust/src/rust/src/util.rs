use std::fmt::Write;

/// General-purpose utilities: bitmaps, fast math, and non-cryptographic hashing.

/// A compact bitset backed by a `Vec<u64>`.
///
/// Suitable for tracking up to 4096 bits (64×64) with a single 512-byte allocation,
/// common in Minecraft for chunk-section masks.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Bitmap {
    bits: Vec<u64>,
}

impl Bitmap {
    /// Create a new bitmap with capacity for `size` bits, all zero.
    pub fn with_capacity(size: usize) -> Self {
        let len = (size + 63) / 64;
        Bitmap {
            bits: vec![0u64; len],
        }
    }

    /// Create from a hex string (each u64 is 16 hex chars, big-endian byte order).
    ///
    /// Accepts optional `0x` prefix and whitespace is ignored.
    /// Zero intermediate `String` allocations — parses directly from byte slice.
    pub fn from_hex(input: &str) -> Result<Self, String> {
        let bytes = parse_hex_bytes(input)?;
        let mut bits = Vec::with_capacity(bytes.len() / 8 + 1);
        for chunk in bytes.chunks(8) {
            let mut buf = [0u8; 8];
            buf[..chunk.len()].copy_from_slice(chunk);
            bits.push(u64::from_be_bytes(buf));
        }
        Ok(Bitmap { bits })
    }

    /// Convert to a hex string (uppercase, 16 chars per u64, big-endian).
    ///
    /// Uses `write!` into a pre-allocated `String` buffer — no per-word temporary
    /// `String` allocations.
    pub fn to_hex(&self) -> String {
        let mut result = String::with_capacity(self.bits.len() * 16 + 2);
        result.push_str("0x");
        for &word in &self.bits {
            write!(result, "{:016X}", word).unwrap();
        }
        result
    }

    /// Set bit at `index` to 1.
    pub fn set(&mut self, index: usize) {
        let word = index / 64;
        let bit = index % 64;
        if let Some(entry) = self.bits.get_mut(word) {
            *entry |= 1u64 << bit;
        }
    }

    /// Clear bit at `index` to 0.
    pub fn clear(&mut self, index: usize) {
        let word = index / 64;
        let bit = index % 64;
        if let Some(entry) = self.bits.get_mut(word) {
            *entry &= !(1u64 << bit);
        }
    }

    /// Get bit at `index`.
    pub fn get(&self, index: usize) -> bool {
        let word = index / 64;
        let bit = index % 64;
        self.bits
            .get(word)
            .map(|entry| (entry >> bit) & 1 != 0)
            .unwrap_or(false)
    }

    /// Count set bits (population count).
    pub fn count(&self) -> u32 {
        self.bits.iter().map(|w| w.count_ones()).sum()
    }

    /// Number of bits that can be stored.
    pub fn capacity(&self) -> usize {
        self.bits.len() * 64
    }
}

/// Parse a hex string into raw bytes.
///
/// Skips whitespace, accepts optional `0x`/`0X` prefix, and produces a `Vec<u8>`
/// with zero intermediate `String` allocations.
pub fn parse_hex_bytes(input: &str) -> Result<Vec<u8>, String> {
    let mut bytes = Vec::with_capacity(input.len() / 2);
    let raw = input.as_bytes();
    let mut i = 0;

    // Skip optional 0x/0X prefix
    if raw.len() >= 2 && raw[0] == b'0' && (raw[1] == b'x' || raw[1] == b'X') {
        i = 2;
    }

    let mut hi = None;
    while i < raw.len() {
        let b = raw[i];
        if b.is_ascii_whitespace() {
            i += 1;
            continue;
        }
        let nibble = hex_val(b).ok_or_else(|| format!("bitmap-error:invalid-hex:0x{:02X}", b))?;
        if let Some(h) = hi {
            bytes.push((h << 4) | nibble);
            hi = None;
        } else {
            hi = Some(nibble);
        }
        i += 1;
    }

    if hi.is_some() {
        return Err("bitmap-error:odd-hex".to_string());
    }
    Ok(bytes)
}

pub fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bitmap_set_and_get() {
        let mut bm = Bitmap::with_capacity(128);
        assert!(!bm.get(5));
        bm.set(5);
        assert!(bm.get(5));
        bm.clear(5);
        assert!(!bm.get(5));
    }

    #[test]
    fn bitmap_count() {
        let mut bm = Bitmap::with_capacity(128);
        bm.set(0);
        bm.set(63);
        bm.set(64);
        assert_eq!(bm.count(), 3);
    }

    #[test]
    fn bitmap_hex_roundtrip() {
        let mut bm = Bitmap::with_capacity(128);
        bm.set(5);
        bm.set(70);
        let hex = bm.to_hex();
        let decoded = Bitmap::from_hex(&hex).unwrap();
        assert_eq!(bm, decoded);
    }

    #[test]
    fn parse_hex_skips_whitespace_and_prefix() {
        let bytes = parse_hex_bytes("0x01 02 03 04 ").unwrap();
        assert_eq!(bytes, vec![0x01, 0x02, 0x03, 0x04]);
    }

    #[test]
    fn parse_hex_rejects_odd() {
        assert!(parse_hex_bytes("abc").is_err());
    }
}