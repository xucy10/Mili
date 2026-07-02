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
