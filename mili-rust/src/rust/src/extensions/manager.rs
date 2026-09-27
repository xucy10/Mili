//! 内置扩展 trait + 注册表 + 动态扩展加载器（骨架）。
//!
//! - 内置扩展：实现 [`MiliExtension`]，编译进主库随版本发布，经 [`ExtensionRegistry`] 管理生命周期
//! - 外部扩展：C ABI v1 动态库（[`crate::extensions::abi`]），经 [`DynamicExtensionLoader`]
//!   在 rustd 子进程内加载（崩溃隔离，JVM 内不做 dlopen）

use std::collections::BTreeMap;
use std::fmt;
use std::path::Path;

use super::abi::{self, DescriptorFn, MiliExtensionV1};
use super::safety::{catch_panic, PanicGuard};

/// 内置扩展接口：编译进主库的扩展实现此 trait，经注册表获得统一生命周期。
pub trait MiliExtension: Send {
    /// 扩展名（注册表主键，全局唯一）。
    fn name(&self) -> &str;
    /// 扩展版本。
    fn version(&self) -> &str;
    /// 初始化。返回 `Err` 则拒绝注册。
    fn on_init(&mut self) -> Result<(), String>;
    /// 每 tick 回调（服务端累计 tick 数）。
    fn on_tick(&mut self, tick: u64);
    /// 卸载/停机回调。
    fn on_shutdown(&mut self);
}

/// 单个已注册扩展及其防护状态。
struct RegisteredExtension {
    ext: Box<dyn MiliExtension>,
    panics: PanicGuard,
}

/// 内置扩展注册表：注册 = init + 插入；注销 = 移除 + shutdown。
///
/// 骨架阶段仅保证生命周期顺序与 panic 隔离；并发模型（是否上锁、
/// 由哪个线程 tick_all）在 Phase 1 结合 rustd 主循环定型。
pub struct ExtensionRegistry {
    inner: BTreeMap<String, RegisteredExtension>,
    /// 注册顺序（生命周期执行的确定性依据）。
    order: Vec<String>,
}

impl ExtensionRegistry {
    pub fn new() -> Self {
        ExtensionRegistry {
            inner: BTreeMap::new(),
            order: Vec::new(),
        }
    }

    /// 初始化并注册扩展。重名、或 `on_init` 失败时返回 `Err`。
    pub fn register(&mut self, mut ext: Box<dyn MiliExtension>) -> Result<(), RegisterError> {
        let name = ext.name().to_string();
        if self.inner.contains_key(&name) {
            return Err(RegisterError::Duplicate(name));
        }
        match catch_panic(|| ext.on_init()) {
            Ok(Ok(())) => {}
            Ok(Err(msg)) => return Err(RegisterError::InitFailed(name, msg)),
            Err(panic_msg) => return Err(RegisterError::InitPanicked(name, panic_msg)),
        }
        self.order.push(name.clone());
        self.inner.insert(
            name,
            RegisteredExtension {
                ext,
                panics: PanicGuard::default(),
            },
        );
        Ok(())
    }

    /// 注销扩展（调用 `on_shutdown`，panic 被吞掉并记入 stderr）。
    pub fn unregister(&mut self, name: &str) -> bool {
        let Some(mut reg) = self.remove_entry(name) else {
            return false;
        };
        if let Err(msg) = catch_panic(|| reg.ext.on_shutdown()) {
            eprintln!("[mili-rust] extension '{name}' panicked on shutdown: {msg}");
        }
        true
    }

    /// 按名查询（共享访问）。
    pub fn get(&self, name: &str) -> Option<&dyn MiliExtension> {
        self.inner.get(name).map(|r| &*r.ext)
    }

    /// 已注册扩展名（按注册顺序）。
    pub fn names(&self) -> &[String] {
        &self.order
    }

    /// 扩展数量。
    pub fn len(&self) -> usize {
        self.inner.len()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.is_empty()
    }

    /// 驱动所有扩展的 `on_tick`。
    ///
    /// 单个扩展 panic 不影响其他扩展；连续 panic 达到阈值后自动卸载。
    pub fn tick_all(&mut self, tick: u64) {
        let names: Vec<String> = self.order.clone();
        for name in names {
            let should_unload = match self.inner.get_mut(&name) {
                Some(reg) => {
                    let result = catch_panic(|| reg.ext.on_tick(tick));
                    match result {
                        Ok(()) => false,
                        Err(msg) => {
                            eprintln!(
                                "[mili-rust] extension '{name}' panicked on tick {tick}: {msg}"
                            );
                            reg.panics.record()
                        }
                    }
                }
                None => false,
            };
            if should_unload {
                eprintln!("[mili-rust] extension '{name}' exceeded panic threshold, unloading");
                self.unregister(&name);
            }
        }
    }

    /// 移除条目并同步 order。
    fn remove_entry(&mut self, name: &str) -> Option<RegisteredExtension> {
        self.inner.remove(name).map(|reg| {
            self.order.retain(|n| n != name);
            reg
        })
    }
}

impl Default for ExtensionRegistry {
    fn default() -> Self {
        ExtensionRegistry::new()
    }
}

/// 注册失败原因。
#[derive(Debug, PartialEq, Eq)]
pub enum RegisterError {
    Duplicate(String),
    InitFailed(String, String),
    InitPanicked(String, String),
}

impl fmt::Display for RegisterError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            RegisterError::Duplicate(name) => write!(f, "扩展重名: {name}"),
            RegisterError::InitFailed(name, msg) => write!(f, "扩展 '{name}' 初始化失败: {msg}"),
            RegisterError::InitPanicked(name, msg) => write!(f, "扩展 '{name}' 初始化 panic: {msg}"),
        }
    }
}

impl std::error::Error for RegisterError {}

/// 外部动态库扩展加载错误。
#[derive(Debug)]
pub enum LoadError {
    /// dlopen 失败。
    Open(libloading::Error),
    /// 缺少描述符导出符号。
    Symbol(libloading::Error),
    /// 描述符函数返回空指针。
    NullDescriptor,
    /// ABI 版本不匹配。
    AbiMismatch { expected: u32, got: u32 },
    /// 描述符字段非法（如 name 为空/非 UTF-8）。
    BadDescriptor(String),
}

impl fmt::Display for LoadError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LoadError::Open(e) => write!(f, "dlopen 失败: {e}"),
            LoadError::Symbol(e) => write!(f, "缺少描述符符号 '{}': {e}", abi::DESCRIPTOR_SYMBOL),
            LoadError::NullDescriptor => write!(f, "描述符函数返回空指针"),
            LoadError::AbiMismatch { expected, got } => {
                write!(f, "ABI 版本不匹配: 期望 {expected}, 扩展声明 {got}")
            }
            LoadError::BadDescriptor(msg) => write!(f, "描述符非法: {msg}"),
        }
    }
}

impl std::error::Error for LoadError {}

/// 外部动态库扩展加载器（骨架）。
///
/// 持有已 dlopen 的库以保证描述符指针在卸载前有效。
/// 完整加载生命周期（回调包装、注册进 registry、安全卸载顺序）在 Phase 2 落地。
pub struct DynamicExtensionLoader {
    /// 已加载库名→(Library, 描述符指针)，保持库存活；销毁顺序 = 加载逆序。
    libs: Vec<(String, libloading::Library)>,
}

impl DynamicExtensionLoader {
    pub fn new() -> Self {
        DynamicExtensionLoader { libs: Vec::new() }
    }

    /// dlopen → 查找描述符符号 → 调用 → ABI 版本与字段校验。
    ///
    /// 返回的指针在 loader 存活期间有效。
    ///
    /// # Safety
    /// 加载任意第三方动态库 inherently unsafe：路径必须可信，
    /// 描述符指向的内存由被加载库的静态存储期保证。
    pub unsafe fn open_and_verify(&mut self, path: &Path) -> Result<*const MiliExtensionV1, LoadError> {
        let lib = libloading::Library::new(path).map_err(LoadError::Open)?;
        let sym: libloading::Symbol<DescriptorFn> = lib
            .get::<DescriptorFn>(format!("{}\0", abi::DESCRIPTOR_SYMBOL).as_bytes())
            .map_err(LoadError::Symbol)?;
        let desc_ptr = sym();
        if desc_ptr.is_null() {
            return Err(LoadError::NullDescriptor);
        }
        let desc = &*desc_ptr;
        if desc.abi_version != abi::ABI_VERSION {
            return Err(LoadError::AbiMismatch {
                expected: abi::ABI_VERSION,
                got: desc.abi_version,
            });
        }
        let name = abi::read_c_str(desc.name).unwrap_or_default();
        if name.is_empty() {
            return Err(LoadError::BadDescriptor("name 为空".to_string()));
        }
        self.libs.push((name.to_string(), lib));
        Ok(desc_ptr)
    }

    /// 按名卸载扩展库（dlclose）。返回 false 表示未找到。
    ///
    /// # Safety
    /// 调用后描述符指针失效；调用方须确保不再解引用已卸载库的任何符号。
    pub unsafe fn unload(&mut self, name: &str) -> bool {
        let pos = self.libs.iter().position(|(n, _)| n == name);
        if let Some(idx) = pos {
            // 逆序 drop 保证依赖顺序（后加载的先卸载）
            let (_, lib) = self.libs.remove(idx);
            drop(lib); // libloading::Library drop 执行 dlclose
            true
        } else {
            false
        }
    }

    /// 已加载库名列表（按加载顺序）。
    pub fn loaded_names(&self) -> Vec<&str> {
        self.libs.iter().map(|(n, _)| n.as_str()).collect()
    }

    /// 已加载库数量（测试/观测用）。
    pub fn loaded_count(&self) -> usize {
        self.libs.len()
    }
}

impl Default for DynamicExtensionLoader {
    fn default() -> Self {
        DynamicExtensionLoader::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering as AtomicOrdering};

    /// 测试用内置扩展。
    struct FakeExt {
        name: &'static str,
        ticks: AtomicU64,
        /// 每 `every` 个 tick panic 一次（tick % every == 0 时触发）。
        panic_every: Option<u64>,
        shutdown_called: AtomicU64,
    }

    impl FakeExt {
        fn new(name: &'static str, panic_every: Option<u64>) -> Self {
            FakeExt {
                name,
                ticks: AtomicU64::new(0),
                panic_every,
                shutdown_called: AtomicU64::new(0),
            }
        }
    }

    impl MiliExtension for FakeExt {
        fn name(&self) -> &str {
            self.name
        }
        fn version(&self) -> &str {
            "0.1.0"
        }
        fn on_init(&mut self) -> Result<(), String> {
            Ok(())
        }
        fn on_tick(&mut self, tick: u64) {
            self.ticks.fetch_add(1, AtomicOrdering::Relaxed);
            if let Some(every) = self.panic_every {
                if tick % every == 0 {
                    panic!("planned panic on tick {tick}");
                }
            }
        }
        fn on_shutdown(&mut self) {
            self.shutdown_called.fetch_add(1, AtomicOrdering::Relaxed);
        }
    }

    #[test]
    fn registry_lifecycle() {
        let mut reg = ExtensionRegistry::new();
        reg.register(Box::new(FakeExt::new("a", None))).unwrap();
        reg.register(Box::new(FakeExt::new("b", None))).unwrap();

        // 重名拒绝
        assert_eq!(
            reg.register(Box::new(FakeExt::new("a", None))),
            Err(RegisterError::Duplicate("a".to_string()))
        );

        // 按注册顺序 tick
        reg.tick_all(1);
        reg.tick_all(2);
        assert_eq!(reg.len(), 2);
        assert_eq!(reg.names(), &["a".to_string(), "b".to_string()]);

        // 注销触发 shutdown
        assert!(reg.unregister("a"));
        assert!(!reg.unregister("a")); // 二次注销返回 false
        assert_eq!(reg.len(), 1);
        assert_eq!(reg.names(), &["b".to_string()]);
    }

    #[test]
    fn init_failure_rejects_registration() {
        struct BadInit;
        impl MiliExtension for BadInit {
            fn name(&self) -> &str {
                "bad"
            }
            fn version(&self) -> &str {
                "0"
            }
            fn on_init(&mut self) -> Result<(), String> {
                Err("nope".to_string())
            }
            fn on_tick(&mut self, _tick: u64) {}
            fn on_shutdown(&mut self) {}
        }
        let mut reg = ExtensionRegistry::new();
        assert_eq!(
            reg.register(Box::new(BadInit)),
            Err(RegisterError::InitFailed("bad".to_string(), "nope".to_string()))
        );
        assert!(reg.is_empty());
    }

    #[test]
    fn panic_is_isolated_and_threshold_unloads() {
        let mut reg = ExtensionRegistry::new();
        reg.register(Box::new(FakeExt::new("stable", None))).unwrap();
        // crasher：每 2 个 tick panic 一次（tick=6, 8, 10 ...），默认阈值 3
        reg.register(Box::new(FakeExt::new("crasher", Some(2)))).unwrap();

        // tick 5..=9：crasher 在 6、8 panic 两次（count=2，未达阈值 3，不卸载）
        for tick in 5..=9 {
            reg.tick_all(tick);
        }
        assert_eq!(reg.len(), 2);

        // tick 10：crasher 第三次 panic，达到阈值 → 自动卸载；stable 不受影响
        reg.tick_all(10);
        assert_eq!(reg.len(), 1);
        assert_eq!(reg.names(), &["stable".to_string()]);
        assert!(reg.get("stable").is_some());

        // 注销稳定扩展后注册表为空
        reg.tick_all(11);
        assert!(reg.unregister("stable"));
        assert!(reg.is_empty());
    }
}
