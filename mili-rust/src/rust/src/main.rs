use mili_optimizer::{
    chunk, nbt, parse_number_list, parse_scheduler_input, protocol, scheduler, util, varint,
};

const HEX_DIGITS: [u8; 16] = *b"0123456789ABCDEF";

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let command = args.get(1).map(|s| s.as_str()).unwrap_or("dedup");
    let payload = args.get(2).map(|s| s.as_str()).unwrap_or("");

    let result = match command {
        // Legacy commands
        "dedup" => dedup(payload),
        "hash" => fnv1a_hash(payload),
        "merge-cost" => packet_merge_cost(payload),
        "packet-size" => packet_size(payload),
        "network-opt" => network_opt(payload),
        "task-uid" => task_uid(),
        "scheduler" => {
            let (job_count, work_units) = parse_scheduler_input(payload);
            scheduler::run_lightweight_tasks(job_count, work_units)
        }

        // Chunk / Region commands
        "chunk-to-region" => chunk_to_region(payload),
        "chunk-local" => chunk_local(payload),
        "chunk-index" => chunk_index_cmd(payload),
        "region-key" => region_key_cmd(payload),
        "header-decode" => header_decode(payload),
        "header-encode" => header_encode(payload),

        // NBT commands
        "nbt-scan" => nbt_scan(payload),

        // VarInt commands
        "varint-encode" => varint_encode(payload),
        "varint-decode" => varint_decode(payload),
        "varint-size" => varint_size_cmd(payload),
        "varlong-encode" => varlong_encode(payload),
        "varlong-decode" => varlong_decode(payload),
        "varlong-size" => varlong_size_cmd(payload),

        // Bitmap / Utility commands
        "bitmap-set" => bitmap_set(payload),
        "bitmap-get" => bitmap_get(payload),
        "bitmap-count" => bitmap_count(payload),
        "murmur3" => murmur3_cmd(payload),

        _ => {
            let mut s = String::with_capacity(32);
            s.push_str("error:unknown-command:");
            s.push_str(command);
            s
        }
    };

    println!("rust-opt:{}", result);
}

fn chunk_to_region(input: &str) -> String {
    let nums = parse_number_list(input);
    let x = nums.get(0).copied().unwrap_or(0) as i32;
    let z = nums.get(1).copied().unwrap_or(0) as i32;
    let (rx, rz) = chunk::chunk_to_region(x, z);
    format!("chunk-to-region:{}:{}:{}:{}", x, z, rx, rz)
}

fn chunk_local(input: &str) -> String {
    let nums = parse_number_list(input);
    let x = nums.get(0).copied().unwrap_or(0) as i32;
    let z = nums.get(1).copied().unwrap_or(0) as i32;
    let (lx, lz) = chunk::chunk_to_local(x, z);
    format!("chunk-local:{}:{}:{}:{}", x, z, lx, lz)
}

fn chunk_index_cmd(input: &str) -> String {
    let nums = parse_number_list(input);
    let x = nums.get(0).copied().unwrap_or(0) as i32;
    let z = nums.get(1).copied().unwrap_or(0) as i32;
    let idx = chunk::chunk_index(x, z);
    format!("chunk-index:{}:{}:{}", x, z, idx)
}

fn region_key_cmd(input: &str) -> String {
    let nums = parse_number_list(input);
    let rx = nums.get(0).copied().unwrap_or(0) as i32;
    let rz = nums.get(1).copied().unwrap_or(0) as i32;
    let key = chunk::region_key(rx, rz);
    format!("region-key:{}:{}:{}", rx, rz, key)
}

fn header_decode(input: &str) -> String {
    let num = input.trim().parse::<u32>().unwrap_or(0);
    let (offset, count) = chunk::decode_header_entry(num);
    format!("header-decode:{}:{}:{}", num, offset, count)
}

fn header_encode(input: &str) -> String {
    let nums = parse_number_list(input);
    let offset = nums.get(0).copied().unwrap_or(0) as u32;
    let count = nums.get(1).copied().unwrap_or(0) as u8;
    let encoded = chunk::encode_header_entry(offset, count);
    format!("header-encode:{}:{}:{}", offset, count, encoded)
}

fn nbt_scan(input: &str) -> String {
    match nbt::parse_hex(input).and_then(|bytes| nbt::scan_nbt(&bytes)) {
        Ok((total, depth, count)) => format!("nbt-scan:{}:{}:{}", total, depth, count),
        Err(e) => e,
    }
}

fn varint_encode(input: &str) -> String {
    let num = input.trim().parse::<i32>().unwrap_or(0);
    let (bytes, len) = varint::encode_varint(num);
    let mut hex = String::with_capacity(len * 2);
    for i in 0..len {
        let b = bytes[i];
        hex.push(HEX_DIGITS[(b >> 4) as usize] as char);
        hex.push(HEX_DIGITS[(b & 0x0F) as usize] as char);
    }
    format!("varint-encode:{}:{}", num, hex)
}

fn varint_decode(input: &str) -> String {
    match nbt::parse_hex(input).and_then(|bytes| varint::decode_varint(&bytes)) {
        Ok((value, size)) => format!("varint-decode:{}:{}", value, size),
        Err(e) => e,
    }
}

fn varint_size_cmd(input: &str) -> String {
    let num = input.trim().parse::<i32>().unwrap_or(0);
    let size = varint::varint_size(num);
    format!("varint-size:{}:{}", num, size)
}

fn varlong_encode(input: &str) -> String {
    let num = input.trim().parse::<i64>().unwrap_or(0);
    let (bytes, len) = varint::encode_varlong(num);
    let mut hex = String::with_capacity(len * 2);
    for i in 0..len {
        let b = bytes[i];
        hex.push(HEX_DIGITS[(b >> 4) as usize] as char);
        hex.push(HEX_DIGITS[(b & 0x0F) as usize] as char);
    }
    format!("varlong-encode:{}:{}", num, hex)
}

fn varlong_decode(input: &str) -> String {
    match nbt::parse_hex(input).and_then(|bytes| varint::decode_varlong(&bytes)) {
        Ok((value, size)) => format!("varlong-decode:{}:{}", value, size),
        Err(e) => e,
    }
}

fn varlong_size_cmd(input: &str) -> String {
    let num = input.trim().parse::<i64>().unwrap_or(0);
    let size = varint::varlong_size(num);
    format!("varlong-size:{}:{}", num, size)
}

fn bitmap_set(input: &str) -> String {
    let mut parts = input.splitn(2, ',');
    let hex = parts.next().unwrap_or("").trim();
    let index = parts.next().unwrap_or("0").trim().parse::<usize>().unwrap_or(0);
    match util::Bitmap::from_hex(hex) {
        Ok(mut bm) => {
            bm.set(index);
            format!("bitmap-set:{}", bm.to_hex())
        }
        Err(e) => e,
    }
}

fn bitmap_get(input: &str) -> String {
    let mut parts = input.splitn(2, ',');
    let hex = parts.next().unwrap_or("").trim();
    let index = parts.next().unwrap_or("0").trim().parse::<usize>().unwrap_or(0);
    match util::Bitmap::from_hex(hex) {
        Ok(bm) => {
            let bit = if bm.get(index) { 1 } else { 0 };
            format!("bitmap-get:{}:{}", index, bit)
        }
        Err(e) => e,
    }
}

fn bitmap_count(input: &str) -> String {
    match util::Bitmap::from_hex(input.trim()) {
        Ok(bm) => format!("bitmap-count:{}", bm.count()),
        Err(e) => e,
    }
}

fn murmur3_cmd(input: &str) -> String {
    let mut parts = input.splitn(2, ',');
    let data = parts.next().unwrap_or("").as_bytes();
    let seed = parts.next().unwrap_or("0").trim().parse::<u32>().unwrap_or(0);
    let hash = util::murmur3_32(data, seed);
    format!("murmur3:{:08X}", hash)
}

/// Deduplicate and sort characters in a string.
///
/// For ASCII-only input, uses a 128-bit inline mask to avoid a `Vec<char>` heap allocation.
fn dedup(input: &str) -> String {
    if input.is_ascii() {
        let mut mask = 0u128;
        let mut result = String::with_capacity(input.len().min(128));
        for b in input.bytes() {
            let bit = 1u128 << b;
            if mask & bit == 0 {
                mask |= bit;
            }
        }
        for b in 0..128u8 {
            if mask & (1u128 << b) != 0 {
                result.push(b as char);
            }
        }
        return result;
    }

    let mut chars: Vec<char> = input.chars().collect();
    chars.sort_unstable();
    chars.dedup();
    chars.into_iter().collect()
}

fn fnv1a_hash(input: &str) -> String {
    const OFFSET_BASIS: u64 = 0xcbf29ce484222325;
    const PRIME: u64 = 0x100000001b3;
    let hash = input.as_bytes().iter().fold(OFFSET_BASIS, |acc, &byte| {
        let acc = acc ^ u64::from(byte);
        acc.wrapping_mul(PRIME)
    });
    format!("hash:{:016x}", hash)
}

fn packet_merge_cost(input: &str) -> String {
    let sizes = parse_number_list(input);
    format!("merge-cost:{}", protocol::optimize_packet_batch(&sizes))
}

fn packet_size(input: &str) -> String {
    format!("packet-size:{}", protocol::parse_packet_size(input))
}

fn network_opt(input: &str) -> String {
    let sizes = parse_number_list(input);
    protocol::optimize_network_batch(&sizes)
}

fn task_uid() -> String {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    format!("task-uid:{}", nanos)
}
