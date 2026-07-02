pub fn parse_packet_size(input: &str) -> u64 {
    input
        .split(|c: char| c.is_whitespace() || c == ',' || c == ';' || c == '|')
        .filter_map(|s| s.parse::<u64>().ok())
        .sum()
}

pub fn normalize_packet_batch(input: &[u64]) -> Vec<u64> {
    let mut values = input.to_vec();
    values.sort_unstable();
    values.dedup();
    values
}

pub fn optimize_packet_batch(input: &[u64]) -> u64 {
    let mut values = normalize_packet_batch(input);
    if values.len() < 2 {
        return values.first().copied().unwrap_or(0);
    }

    let mut total = 0u64;
    while values.len() > 1 {
        let first = values.remove(0);
        let second = values.remove(0);
        let merged = first.saturating_add(second);
        total = total.saturating_add(merged);
        let pos = values.partition_point(|x| *x < merged);
        values.insert(pos, merged);
    }

    total
}

pub fn optimize_network_batch(input: &[u64]) -> String {
    let values = normalize_packet_batch(input);
    if values.is_empty() {
        return "network-opt:1:0:0".to_string();
    }

    let total = values.iter().sum::<u64>();
    let packet_count = values.len() as u64;
    let average = total.saturating_div(packet_count.max(1));
    let batch_hint = average.saturating_div(8).saturating_add(packet_count.min(4)).clamp(1, 8);
    format!("network-opt:{}:{}:{}", batch_hint, packet_count, total)
}

#[cfg(test)]
mod tests {
    use super::{optimize_network_batch, optimize_packet_batch};

    #[test]
    fn network_batch_hint_is_bounded() {
        let hint = optimize_network_batch(&[1, 2, 4, 8, 16]);
        assert!(hint.starts_with("network-opt:"));
        assert!(hint.contains(":5:"));
    }

    #[test]
    fn packet_batch_cost_is_stable() {
        let cost = optimize_packet_batch(&[1, 2, 3, 4]);
        assert_eq!(cost, 10);
    }
}
