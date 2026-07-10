pub mod chunk;
pub mod entity_cull;
pub mod frustum;
pub mod jni_bridge;
pub mod lighting;
pub mod mesh;
pub mod nbt;
pub mod occlusion;
pub mod protocol;
pub mod scheduler;
pub mod util;
pub mod varint;

/// Parse a string of whitespace/comma/semicolon/pipe-separated numbers into `Vec<u64>`.
/// Ignores non-numeric tokens silently.
pub fn parse_number_list(input: &str) -> Vec<u64> {
    input
        .split(|c: char| c.is_whitespace() || c == ',' || c == ';' || c == '|')
        .filter_map(|s| s.trim().parse::<u64>().ok())
        .collect()
}

/// Parse scheduler input into `(job_count, work_units)`.
/// Falls back to `(1, 512)` if the input is malformed or empty.
pub fn parse_scheduler_input(input: &str) -> (usize, usize) {
    let mut values = input
        .split(|c: char| c.is_whitespace() || c == ',' || c == ';' || c == '|')
        .filter_map(|token| token.parse::<usize>().ok());

    let job_count = values.next().unwrap_or(1);
    let work_units = values.next().unwrap_or(512);
    (job_count, work_units)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_number_list_various_separators() {
        assert_eq!(parse_number_list("1 2 3 4"), vec![1, 2, 3, 4]);
        assert_eq!(parse_number_list("1,2,3,4"), vec![1, 2, 3, 4]);
        assert_eq!(parse_number_list("1;2|3;4"), vec![1, 2, 3, 4]);
    }

    #[test]
    fn parse_number_list_ignores_garbage() {
        assert_eq!(parse_number_list("a,1,b,2"), vec![1, 2]);
    }

    #[test]
    fn parse_scheduler_input_defaults() {
        assert_eq!(parse_scheduler_input(""), (1, 512));
        assert_eq!(parse_scheduler_input("4"), (4, 512));
        assert_eq!(parse_scheduler_input("4 1024"), (4, 1024));
    }
}