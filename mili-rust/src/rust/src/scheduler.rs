use std::sync::mpsc;
use std::thread;

pub fn run_lightweight_tasks(job_count: usize, work_units: usize) -> String {
    let (tx, rx) = mpsc::channel();
    let mut handles = Vec::new();

    for worker_id in 0..job_count.min(4) {
        let tx = tx.clone();
        handles.push(thread::spawn(move || {
            let mut total = 0u64;
            for value in 0..work_units {
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

    format!("scheduler:{}:{}:{}", job_count, work_units, sum)
}
