/*
 * Mili Extension ABI v1（规范源）。
 *
 * Rust 侧契约：mili-rust/src/rust/src/extensions/abi.rs
 * 完整规范与加载流程：docs/ARCHITECTURE.md 附录 B
 *
 * 外部扩展以 C ABI 动态库形态提供，必须导出符号：
 *     mili_extension_descriptor
 * 其类型为「返回指向静态存储期描述符的指针的函数」。
 *
 * 构建约定（与宿主一致，见 mili-rust/build.gradle.kts）：
 *   - Linux x86_64: x86_64-unknown-linux-gnu, GLIBC <= 2.28
 *   - Linux aarch64: aarch64-unknown-linux-gnu
 *   - Windows x86_64: x86_64-pc-windows-gnu
 *   - macOS: x86_64-apple-darwin / aarch64-apple-darwin
 *
 * 生命周期与线程约定：
 *   - on_init / on_shutdown 由 rustd 主线程调用
 *   - on_tick 同样在主线程串行调用（骨架阶段；Phase 2 如引入多线程将扩展 ABI v2）
 *   - 回调内禁止 unwind（C 侧无法捕获；Rust 侧扩展由宿主 catch_unwind 包裹）
 *   - 回调内禁止调用 stdio（stdout 为协议帧独占）
 */

#ifndef MILI_EXTENSION_H
#define MILI_EXTENSION_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MILI_ABI_VERSION_V1 1u

/* on_init 返回码 */
#define MILI_INIT_OK 0

typedef struct mili_extension_v1 {
    uint32_t abi_version;            /* 必须为 MILI_ABI_VERSION_V1 */
    const char *name;                /* UTF-8 扩展名，静态存储期，NUL 结尾 */
    const char *version;             /* UTF-8 版本串，静态存储期，NUL 结尾 */
    int (*on_init)(void);            /* 必填；返回 MILI_INIT_OK 表示成功 */
    void (*on_tick)(uint64_t tick);  /* 可空（NULL 跳过） */
    void (*on_shutdown)(void);       /* 可空（NULL 跳过） */
} mili_extension_v1;

/*
 * 扩展动态库必须导出（C 链接，返回值不得为 NULL）：
 *
 *   const mili_extension_v1 *mili_extension_descriptor(void);
 *
 * 示例（C）：
 *
 *   static int my_init(void) { return MILI_INIT_OK; }
 *   static void my_tick(uint64_t t) { (void)t; }
 *
 *   static const mili_extension_v1 MY_DESC = {
 *       .abi_version = MILI_ABI_VERSION_V1,
 *       .name        = "my-extension",
 *       .version     = "0.1.0",
 *       .on_init     = my_init,
 *       .on_tick     = my_tick,
 *       .on_shutdown = NULL,
 *   };
 *
 *   const mili_extension_v1 *mili_extension_descriptor(void) {
 *       return &MY_DESC;
 *   }
 */

#ifdef __cplusplus
}
#endif

#endif /* MILI_EXTENSION_H */
