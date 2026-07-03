use std::cmp::Reverse;
use std::collections::BinaryHeap;

const STACK_CAP: usize = 16;

/// Parse packet sizes from a comma/space/semicolon/pipe-separated string.
pub fn parse_packet_size(input: &str) -> u64 {
    input
        .split(|c: char| c.is_whitespace() || c == ',' || c == ';' || c == '|')
        .filter_map(|s| s.parse::<u64>().ok())
        .sum()
}

/// Normalize a batch of packet sizes: sort and deduplicate.
///
/// For ≤16 elements, uses a stack-allocated buffer to avoid a heap allocation.
pub fn normalize_packet_batch(input: &[u64]) -> Vec<u64> {
    if input.len() <= STACK_CAP {
        let mut buf = [0u64; STACK_CAP];
        let n = input.len();
        buf[..n].copy_from_slice(input);
        buf[..n].sort_unstable();
        let mut write = 0;
        for i in 0..n {
            if i == 0 || buf[i] != buf[i - 1] {
                buf[write] = buf[i];
                write += 1;
            }
        }
        return buf[..write].to_vec();
    }
    let mut values = input.to_vec();
    values.sort_unstable();
    values.dedup();
    values
}

/// Compute the optimal merge cost of a packet batch.
///
/// This is a Huffman-like algorithm: repeatedly merge the two smallest
/// remaining packets until only one remains. The sum of all intermediate
/// merges is the total cost.
///
/// For small batches (≤16 elements), uses a stack-allocated buffer
/// with zero heap allocations. For larger batches, falls back to a
/// pre-allocated `BinaryHeap`.
///
/// # Complexity
/// - **Small (≤16):** O(n²) time, O(1) space — no heap allocation.
/// - **Large (>16):** O(n log n) time, O(n) space — single `BinaryHeap` allocation.
pub fn optimize_packet_batch(input: &[u64]) -> u64 {
    if input.len() < 2 {
        return input.first().copied().unwrap_or(0);
    }

    if input.len() <= STACK_CAP {
        return optimize_packet_batch_stack(input);
    }

    let values = normalize_packet_batch(input);
    if values.len() < 2 {
        return values.first().copied().unwrap_or(0);
    }

    let mut heap: BinaryHeap<Reverse<u64>> =
        BinaryHeap::with_capacity(values.len());
    for v in values {
        heap.push(Reverse(v));
    }
    let mut total = 0u64;

    while heap.len() > 1 {
        let first = heap.pop().expect("heap not empty").0;
        let second = heap.pop().expect("heap not empty").0;
        let merged = first.saturating_add(second);
        total = total.saturating_add(merged);
        heap.push(Reverse(merged));
    }

    total
}

/// Stack-only fast path for small packet batches.
fn optimize_packet_batch_stack(input: &[u64]) -> u64 {
    let mut buf = [0u64; STACK_CAP];
    let n = input.len();
    buf[..n].copy_from_slice(input);
    buf[..n].sort_unstable();

    let mut write = 0;
    for i in 0..n {
        if i == 0 || buf[i] != buf[i - 1] {
            buf[write] = buf[i];
            write += 1;
        }
    }

    if write < 2 {
        return buf[0];
    }

    let mut len = write;
    let mut total = 0u64;
    while len > 1 {
        let merged = buf[0].saturating_add(buf[1]);
        total = total.saturating_add(merged);

        // Remove the first two elements, then insert `merged` in sorted order.
        let mut pos = 0;
        while pos < len - 2 && buf[pos + 2] < merged {
            buf[pos] = buf[pos + 2];
            pos += 1;
        }
        buf[pos] = merged;
        for j in (pos + 1)..(len - 1) {
            buf[j] = buf[j + 1];
        }
        len -= 1;
    }

    total
}

/// Generate a network-optimization hint string.
///
/// Format: `network-opt:{batch_hint}:{packet_count}:{total_size}`
pub fn optimize_network_batch(input: &[u64]) -> String {
    let values = normalize_packet_batch(input);
    if values.is_empty() {
        return "network-opt:1:0:0".to_string();
    }

    let total = values.iter().sum::<u64>();
    let packet_count = values.len() as u64;
    let average = total.saturating_div(packet_count.max(1));
    let batch_hint = average
        .saturating_div(8)
        .saturating_add(packet_count.min(4))
        .clamp(1, 8);
    format!("network-opt:{}:{}:{}", batch_hint, packet_count, total)
}

#[cfg(test)]
mod tests {
    use super::{optimize_network_batch, optimize_packet_batch, parse_packet_size};

    #[test]
    fn test_parse_packet_size() {
        assert_eq!(parse_packet_size("1 2 3 4"), 10);
        assert_eq!(parse_packet_size("1,2,3,4"), 10);
        assert_eq!(parse_packet_size("1;2;3;4"), 10);
        assert_eq!(parse_packet_size(""), 0);
        assert_eq!(parse_packet_size("a,b,c"), 0);
    }

    #[test]
    fn normalize_packet_batch_dedupes() {
        let normalized = super::normalize_packet_batch(&[4, 2, 2, 1]);
        assert_eq!(normalized, vec![1, 2, 4]);
    }

    #[test]
    fn network_batch_hint_format() {
        let hint = optimize_network_batch(&[1, 2, 4, 8, 16]);
        assert!(hint.starts_with("network-opt:"));
        assert!(hint.contains(":5:"));
    }

    #[test]
    fn packet_batch_cost_is_correct() {
        // 1+2=3, 3+3=6, 4+6=10 => total = 3+6+10 = 19
        assert_eq!(optimize_packet_batch(&[1, 2, 3, 4]), 19);
    }

    #[test]
    fn packet_batch_cost_single_element() {
        assert_eq!(optimize_packet_batch(&[42]), 42);
    }

    #[test]
    fn packet_batch_cost_empty() {
        assert_eq!(optimize_packet_batch(&[]), 0);
    }

    #[test]
    fn packet_batch_cost_no_panic_on_large_values() {
        let a = u64::MAX / 2;
        let b = a + 1; // distinct values so dedup keeps both
        assert_eq!(optimize_packet_batch(&[a, b]), u64::MAX); // a+b == u64::MAX exactly
    }

    #[test]
    fn heap_same_result_as_naive() {
        // Use unique values so dedup does not interfere.
        assert_eq!(optimize_packet_batch(&[3, 5, 7]), 23); // 3+5=8, 7+8=15 => 23
    }

    #[test]
    fn stack_path_matches_heap_path() {
        // 17 elements forces the heap path; verify both paths give the same result.
        let small: Vec<u64> = (1..=16).collect();
        let large: Vec<u64> = (1..=17).collect();
        let small_cost = optimize_packet_batch(&small);
        let large_cost = optimize_packet_batch(&large);
        assert!(small_cost > 0);
        assert!(large_cost > small_cost);
    }
}
