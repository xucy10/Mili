//! Mili 扩展框架（骨架，Phase 0）。
//!
//! - [`abi`]：外部动态库扩展的 C ABI v1 契约（规范源，C 头文件见 `mili-rust/include/mili_extension.h`）
//! - [`safety`]：跨边界 panic 防护
//! - [`manager`]：内置扩展 trait + 注册表 + 动态加载器
//!
//! 生命周期与设计决策见 `docs/ARCHITECTURE.md` 第 6 章。

pub mod abi;
pub mod manager;
pub mod safety;
