//! 外部动态库扩展的 C ABI v1 契约（规范源）。
//!
//! 设计决策：手写最小 C ABI，而非 abi_stable/stabby —— 扩展面仅
//! 「1 个描述符函数 + 3 个回调指针」，手写成本可控且不扩张依赖树与
//! GLIBC 符号面（与构建流水线的 zigbuild + GLIBC 2.28 铁律相抵）。
//! 完整规范与示例：`docs/ARCHITECTURE.md` 附录 B、`mili-rust/include/mili_extension.h`。
//!
//! 加载流程（Phase 2 完整落地，当前为骨架）：
//! 1. `dlopen` 扩展动态库
//! 2. 查找导出符号 [`DESCRIPTOR_SYMBOL`]（类型为 [`DescriptorFn`]）
//! 3. 调用之，得到指向静态存储期描述符的指针
//! 4. 校验 `abi_version == [`ABI_VERSION`]`，不匹配即拒绝加载
//! 5. 所有回调经 [`crate::extensions::safety::catch_panic`] 包装后调用

use std::os::raw::{c_char, c_int};

/// 当前 ABI 版本。破坏性变更时递增；扩展按自身声明的版本协商。
pub const ABI_VERSION: u32 = 1;

/// 扩展库必须导出的描述符符号名。
pub const DESCRIPTOR_SYMBOL: &str = "mili_extension_descriptor";

/// `on_init` 成功返回码。
pub const INIT_OK: c_int = 0;

/// 描述符导出函数类型：返回指向静态存储期描述符的指针。
pub type DescriptorFn = unsafe extern "C" fn() -> *const MiliExtensionV1;

/// C ABI v1 扩展描述符。
///
/// 与 `mili_extension.h` 中的 `mili_extension_v1` 逐字段对应；
/// `Option<extern "C" fn>` 与可空 C 函数指针 ABI 兼容（Rust 语言保证）。
#[repr(C)]
pub struct MiliExtensionV1 {
    /// 必须等于 [`ABI_VERSION`]，加载器据此协商/拒绝。
    pub abi_version: u32,
    /// 扩展名（UTF-8，静态存储期，作为注册表主键）。
    pub name: *const c_char,
    /// 扩展版本（UTF-8，静态存储期）。
    pub version: *const c_char,
    /// 必填：初始化回调，返回 0（[`INIT_OK`]）表示成功。
    pub on_init: Option<unsafe extern "C" fn() -> c_int>,
    /// 可空：每 tick 回调，`tick` 为服务端累计 tick 数。
    pub on_tick: Option<unsafe extern "C" fn(u64)>,
    /// 可空：卸载/停机回调。
    pub on_shutdown: Option<unsafe extern "C" fn()>,
}

/// 读取描述符中的 C 字符串字段。
///
/// # Safety
/// `p` 必须指向合法的静态存储期 NUL 结尾 UTF-8 字符串（由扩展库保证）。
pub unsafe fn read_c_str(p: *const c_char) -> Option<&'static str> {
    if p.is_null() {
        return None;
    }
    std::ffi::CStr::from_ptr(p).to_str().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn abi_layout_matches_c_header() {
        // 若此断言失败，说明字段布局与 mili_extension.h 漂移，需双侧同步
        assert_eq!(ABI_VERSION, 1);
        assert_eq!(DESCRIPTOR_SYMBOL, "mili_extension_descriptor");
        assert_eq!(INIT_OK, 0);
        // Option<extern fn> 与 C 函数指针等尺寸
        assert_eq!(
            std::mem::size_of::<Option<unsafe extern "C" fn()>>(),
            std::mem::size_of::<*const ()>()
        );
    }
}
