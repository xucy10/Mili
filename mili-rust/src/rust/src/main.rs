mod protocol;
mod scheduler;

use std::env;

fn main() {
    let args: Vec<String> = env::args().collect();
    let command = args.get(1).map(|s| s.as_str()).unwrap_or("dedup");
    let payload = args.get(2).map(|s| s.as_str()).unwrap_or("");

    let result = match command {
        "dedup" => dedup(payload),
        "hash" => fnv1a_hash(payload),
        "merge-cost" => packet_merge_cost(payload),
        "packet-size" => packet_size(payload),
        "network-opt" => network_opt(payload),
        "task-uid" => task_uid(payload),
        "scheduler" => {
            let (job_count, work_units) = parse_scheduler_input(payload);
            scheduler::run_lightweight_tasks(job_count, work_units)
        }
        _ => format!("error:unknown-command:{}", command),
    };

    println!("rust-opt:{}", result);
}

fn dedup(input: &str) -> String {
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
    let sizes: Vec<u64> = input
        .split(|c| c == ',' || c == ';' || c == ' ' || c == '|')
        .filter_map(|chunk| chunk.trim().parse::<u64>().ok())
        .collect();

    format!("merge-cost:{}", protocol::optimize_packet_batch(&sizes))
}

fn packet_size(input: &str) -> String {
    format!("packet-size:{}", protocol::parse_packet_size(input))
}

fn network_opt(input: &str) -> String {
    let sizes: Vec<u64> = input
        .split(|c| c == ',' || c == ';' || c == ' ' || c == '|')
        .filter_map(|chunk| chunk.trim().parse::<u64>().ok())
        .collect();
    protocol::optimize_network_batch(&sizes)
}

fn task_uid(_: &str) -> String {
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    format!("task-uid:{}", nanos)
}

fn parse_scheduler_input(input: &str) -> (usize, usize) {
    let mut values = input
        .split(|c: char| c.is_whitespace() || c == ',' || c == ';' || c == '|')
        .filter_map(|token| token.parse::<usize>().ok());

    let job_count = values.next().unwrap_or(1);
    let work_units = values.next().unwrap_or(512);
    (job_count, work_units)
}
