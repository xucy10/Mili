use rayon::prelude::*;

/// Run lightweight computation tasks in parallel using Rayon's work-stealing scheduler.
///
/// Replaces the original `std::thread` + `mpsc::channel` implementation with
/// Rayon parallel iterators, which:
/// - Adapt to the available thread pool automatically
/// - Avoid manual channel management and `join()` bookkeeping
/// - Compose better with other Rayon parallel operations
///
/// For small workloads (< 256 work units), uses a sequential fast path to avoid
/// rayon task-queue overhead and implicit heap allocations.
///
/// # Arguments
/// * `job_count` - Desired number of worker threads (clamped to 1..=4)
/// * `work_units` - Total amount of work to distribute (clamped to >= 1)
///
/// # Returns
/// A string in the format `scheduler:{batch_size}:{worker_count}:{work_units}:{checksum}`
pub fn run_lightweight_tasks(job_count: usize, work_units: usize) -> String {
    let worker_count = job_count.max(1).min(4);
    let work_units = work_units.max(1);
    let batch_size = plan_batch_size(worker_count, work_units);

    // Sequential fast path for small workloads: avoids rayon task-queue overhead.
    let sum: u64 = if work_units < 256 {
        (0..worker_count)
            .map(|worker_id| compute_chunk(worker_id, work_units, worker_count))
            .sum()
    } else {
        (0..worker_count)
            .into_par_iter()
            .map(|worker_id| compute_chunk(worker_id, work_units, worker_count))
            .sum()
    };

    format!("scheduler:{}:{}:{}:{}", batch_size, worker_count, work_units, sum)
}

fn compute_chunk(worker_id: usize, work_units: usize, worker_count: usize) -> u64 {
    let chunk_size = ((work_units + worker_count - 1) / worker_count).max(1);
    let start = worker_id * chunk_size;
    let end = (start + chunk_size).min(work_units);

    if start >= end {
        return 0u64;
    }

    let mut total = 0u64;
    for value in start..end {
        let mut x = (worker_id + 1) as u64;
        x = x.wrapping_mul(31).wrapping_add(value as u64);
        x = x.rotate_left((worker_id % 8) as u32);
        total = total.wrapping_add(x);
    }
    total
}

fn plan_batch_size(worker_count: usize, work_units: usize) -> usize {
    let work_units = work_units.max(1);
    let worker_count = worker_count.max(1).min(4);

    if work_units <= 64 {
        1
    } else if work_units <= 256 {
        worker_count.min(2)
    } else {
        worker_count.min(4)
    }
}

#[cfg(test)]
mod tests {
    use super::{plan_batch_size, run_lightweight_tasks};

    #[test]
    fn plan_batch_size_scales_with_load() {
        assert_eq!(plan_batch_size(1, 32), 1);
        assert_eq!(plan_batch_size(4, 160), 2);
        assert_eq!(plan_batch_size(4, 1024), 4);
    }

    #[test]
    fn run_lightweight_tasks_returns_valid_payload() {
        let payload = run_lightweight_tasks(4, 64);
        assert!(payload.starts_with("scheduler:"));
        assert!(payload.contains(":4:"));
    }

    #[test]
    fn deterministic_result_for_same_input() {
        let a = run_lightweight_tasks(2, 100);
        let b = run_lightweight_tasks(2, 100);
        assert_eq!(a, b);
    }

    #[test]
    fn single_worker_edge_case() {
        let payload = run_lightweight_tasks(0, 10);
        assert!(payload.starts_with("scheduler:"));
        assert!(payload.contains(":1:"));
    }

    #[test]
    fn zero_work_units_defaults() {
        let payload = run_lightweight_tasks(2, 0);
        assert!(payload.contains(":1:")); // work_units clamped to 1
    }
}
