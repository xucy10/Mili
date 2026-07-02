use std::sync::mpsc;
use std::thread;

pub fn run_lightweight_tasks(job_count: usize, work_units: usize) -> String {
    let worker_count = job_count.max(1).min(4);
    let work_units = work_units.max(1);
    let batch_size = plan_batch_size(worker_count, work_units);

    let (tx, rx) = mpsc::channel();
    let mut handles = Vec::with_capacity(worker_count);

    for worker_id in 0..worker_count {
        let tx = tx.clone();
        let chunk_size = ((work_units + worker_count - 1) / worker_count).max(1);
        let start = worker_id * chunk_size;
        let end = (start + chunk_size).min(work_units);

        if start >= end {
            continue;
        }

        handles.push(thread::spawn(move || {
            let mut total = 0u64;
            for value in start..end {
                let mut x = (worker_id + 1) as u64;
                x = x.wrapping_mul(31).wrapping_add(value as u64);
                x = x.rotate_left((worker_id % 8) as u32);
                total = total.wrapping_add(x);
            }
            let _ = tx.send(total);
        }));
    }

    drop(tx);

    let mut sum = 0u64;
    for received in rx {
        sum = sum.wrapping_add(received);
    }

    for handle in handles {
        let _ = handle.join();
    }

    format!("scheduler:{}:{}:{}:{}", batch_size, worker_count, work_units, sum)
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
        assert_eq!(1, plan_batch_size(1, 32));
        assert_eq!(2, plan_batch_size(4, 160));
        assert_eq!(4, plan_batch_size(4, 1024));
    }

    #[test]
    fn run_lightweight_tasks_returns_scheduler_payload() {
        let payload = run_lightweight_tasks(4, 64);
        assert!(payload.starts_with("scheduler:"));
        assert!(payload.contains(":4:"));
    }
}
