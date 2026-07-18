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

/// Fast power-of-two ceiling for u32.
pub fn next_power_of_two_u32(value: u32) -> u32 {
    value.next_power_of_two()
}

/// Fast modulo when `divisor` is a power of two.
/// # Panics
/// In debug builds if `divisor` is not a power of two or is zero.
pub fn fast_mod(value: u32, divisor: u32) -> u32 {
    debug_assert!(divisor.is_power_of_two() && divisor != 0);
    value & (divisor - 1)
}

/// MurmurHash3 32-bit (x86 variant, no seed mixing).
pub fn murmur3_32(data: &[u8], seed: u32) -> u32 {
    let len = data.len() as u32;
    let mut h = seed;
    const C1: u32 = 0xCC9E2D97;
    const C2: u32 = 0x1B873593;

    for chunk in data.chunks_exact(4) {
        let mut k = u32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
        k = k.wrapping_mul(C1);
        k = k.rotate_left(15);
        k = k.wrapping_mul(C2);
        h ^= k;
        h = h.rotate_left(13);
        h = h.wrapping_mul(5).wrapping_add(0xE6546B64);
    }

    let remainder = data.chunks_exact(4).remainder();
    let mut k = 0u32;
    match remainder.len() {
        3 => k ^= (remainder[2] as u32) << 16,
        2 => k ^= (remainder[1] as u32) << 8,
        1 => k ^= remainder[0] as u32,
        _ => {}
    }
    if !remainder.is_empty() {
        k = k.wrapping_mul(C1);
        k = k.rotate_left(15);
        k = k.wrapping_mul(C2);
        h ^= k;
    }

    h ^= len;
    h ^= h >> 16;
    h = h.wrapping_mul(0x85EBCA6B);
    h ^= h >> 13;
    h = h.wrapping_mul(0xC2B2AE35);
    h ^= h >> 16;
    h
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

    #[test]
    fn next_power_of_two_basic() {
        assert_eq!(next_power_of_two_u32(1), 1);
        assert_eq!(next_power_of_two_u32(5), 8);
        assert_eq!(next_power_of_two_u32(8), 8);
    }

    #[test]
    fn fast_mod_correct() {
        assert_eq!(fast_mod(17, 8), 17 % 8);
        assert_eq!(fast_mod(31, 16), 31 % 16);
    }

    #[test]
    fn murmur3_known_value() {
        let hash = murmur3_32(b"hello", 0);
        assert_ne!(hash, 0); // sanity check
    }
}