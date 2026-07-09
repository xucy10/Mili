package `fun`.bm.mili.config.modules

import com.electronwill.nightconfig.core.file.CommentedFileConfig

/**
 * Kotlin-friendly config module base interface.
 *
 * Config classes should use the pattern:
 * ```kotlin
 * class MyConfig : ConfigModule {
 *     companion object {
 *         @JvmField var enabled = false
 *     }
 *     override fun onLoaded(c: CommentedFileConfig) {}
 *     override fun onUnloaded(c: CommentedFileConfig) {}
 * }
 * ```
 *
 * `@JvmField` on companion object properties exposes them as static fields
 * on the Java side (e.g., `MyConfig.enabled` works from Java).
 */
interface ConfigModule {
    fun onLoaded(configInstance: CommentedFileConfig)
    fun onUnloaded(configInstance: CommentedFileConfig)
}
