//! 跨边界 panic 防护。
//!
//! 规则：`extern "C"` 回调内部 unwind 属未定义行为，因此所有进入
//! 第三方代码（内置扩展的 trait 实现、动态库回调）的调用都必须经过
//! [`catch_panic`]。panic 累计超过 [`PanicGuard`] 阈值后应卸载对应扩展。

use std::panic::{self, AssertUnwindSafe};
use std::sync::atomic::{AtomicU64, Ordering};

/// 执行 `f` 并把 panic 转为 `Err(描述字符串)`。
///
/// 使用 `AssertUnwindSafe`：扩展回调普遍持有内部可变状态，
/// 由扩展自身保证状态一致性；Mili 侧只保证进程存活。
pub fn catch_panic<R>(f: impl FnOnce() -> R) -> Result<R, String> {
    panic::catch_unwind(AssertUnwindSafe(f)).map_err(|payload| {
        payload
            .downcast_ref::<&str>()
            .map(|s| (*s).to_string())
            .or_else(|| payload.downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "<non-string panic payload>".to_string())
    })
}

/// panic 计数与卸载阈值。
#[derive(Debug)]
pub struct PanicGuard {
    counter: AtomicU64,
    threshold: u64,
}

impl PanicGuard {
    pub fn new(threshold: u64) -> Self {
        PanicGuard {
            counter: AtomicU64::new(0),
            threshold: threshold.max(1),
        }
    }

    /// 记录一次 panic。返回 `true` 表示已达卸载阈值。
    pub fn record(&self) -> bool {
        let n = self.counter.fetch_add(1, Ordering::Relaxed) + 1;
        n >= self.threshold
    }

    /// 累计 panic 次数。
    pub fn count(&self) -> u64 {
        self.counter.load(Ordering::Relaxed)
    }

    /// 阈值。
    pub fn threshold(&self) -> u64 {
        self.threshold
    }
}

impl Default for PanicGuard {
    fn default() -> Self {
        // 默认容忍 3 次 panic 后卸载
        PanicGuard::new(3)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catch_panic_converts_payload() {
        assert_eq!(catch_panic(|| 1).unwrap(), 1);
        // 格式化 panic! 产生 String 载荷
        assert_eq!(catch_panic::<()>(|| panic!("boom {}", 42)).unwrap_err(), "boom 42");
        // 非 String/&str 载荷（如 panic_any(42)）落到兜底分支
        assert_eq!(
            catch_panic::<()>(|| std::panic::panic_any(42i32)).unwrap_err(),
            "<non-string panic payload>"
        );
    }

    #[test]
    fn panic_guard_threshold() {
        let guard = PanicGuard::new(3);
        assert!(!guard.record());
        assert!(!guard.record());
        assert!(guard.record());
        assert_eq!(guard.count(), 3);
    }

    #[test]
    fn panic_guard_minimum_threshold_is_one() {
        let guard = PanicGuard::new(0);
        assert!(guard.record());
    }
}
