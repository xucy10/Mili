/// Chunk and region coordinate utilities for Minecraft Anvil format.
///
/// Folia's region-based scheduling maps directly to Anvil region files.
/// This module provides zero-allocation coordinate conversions and
/// region-file header math.

/// Convert chunk coordinates to region coordinates.
/// Java equivalent: `regionX = chunkX >> 5`
pub fn chunk_to_region(chunk_x: i32, chunk_z: i32) -> (i32, i32) {
    (chunk_x >> 5, chunk_z >> 5)
}

/// Convert chunk coordinates to local coordinates within a region (0..=31).
pub fn chunk_to_local(chunk_x: i32, chunk_z: i32) -> (i32, i32) {
    (chunk_x & 0x1F, chunk_z & 0x1F)
}

/// Compute the byte index in a region file's 4 KiB header for a given chunk.
/// Index = 4 * (local_x + local_z * 32)
pub fn chunk_index(chunk_x: i32, chunk_z: i32) -> usize {
    let (local_x, local_z) = chunk_to_local(chunk_x, chunk_z);
    4 * (local_x + local_z * 32) as usize
}

/// Decode a 4-byte header entry into (sector_offset, sector_count).
///
/// The first 3 bytes (big-endian) are the sector offset multiplied by 4096.
/// The last byte is the sector count.
pub fn decode_header_entry(entry: u32) -> (u32, u8) {
    let offset = (entry >> 8) & 0xFFFFFF;
    let count = (entry & 0xFF) as u8;
    (offset, count)
}

/// Encode a sector offset and count into a 4-byte header entry.
pub fn encode_header_entry(offset: u32, count: u8) -> u32 {
    ((offset & 0xFFFFFF) << 8) | (count as u32)
}

/// Pack a region coordinate pair into a single i64 key (useful for hash maps).
pub fn region_key(region_x: i32, region_z: i32) -> i64 {
    ((region_x as i64) << 32) | ((region_z as i64) & 0xFFFFFFFF)
}

/// Unpack a region key back into coordinates.
pub fn unpack_region_key(key: i64) -> (i32, i32) {
    ((key >> 32) as i32, key as i32)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn chunk_to_region_basic() {
        assert_eq!(chunk_to_region(0, 0), (0, 0));
        assert_eq!(chunk_to_region(31, 31), (0, 0));
        assert_eq!(chunk_to_region(32, 32), (1, 1));
    }

    #[test]
    fn chunk_to_region_negative() {
        assert_eq!(chunk_to_region(-1, -1), (-1, -1));
        assert_eq!(chunk_to_region(-32, -32), (-1, -1));
        assert_eq!(chunk_to_region(-33, -33), (-2, -2));
    }

    #[test]
    fn chunk_to_local_range() {
        let (lx, lz) = chunk_to_local(33, 33);
        assert_eq!((lx, lz), (1, 1));
    }

    #[test]
    fn chunk_index_increases() {
        let idx0 = chunk_index(0, 0);
        let idx1 = chunk_index(1, 0);
        let idx32 = chunk_index(0, 1);
        assert!(idx1 > idx0);
        assert!(idx32 > idx1);
        assert_eq!(idx0, 0);
        assert_eq!(idx1, 4);
    }

    #[test]
    fn header_entry_roundtrip() {
        let offset = 12345u32;
        let count = 42u8;
        let encoded = encode_header_entry(offset, count);
        let (decoded_offset, decoded_count) = decode_header_entry(encoded);
        assert_eq!(decoded_offset, offset);
        assert_eq!(decoded_count, count);
    }

    #[test]
    fn region_key_roundtrip() {
        let key = region_key(-100, 200);
        assert_eq!(unpack_region_key(key), (-100, 200));
    }
}
