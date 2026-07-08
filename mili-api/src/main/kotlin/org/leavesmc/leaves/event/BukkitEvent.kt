@file:JvmName("BukkitEventKt")

package org.leavesmc.leaves.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Base class for Kotlin Bukkit events with automatic HandlerList management.
 *
 * Kotlin data classes can extend this to eliminate the boilerplate
 * `HandlerList` + `getHandlerList` + `getHandlers` pattern.
 *
 * Usage:
 * ```kotlin
 * class BotCreateEvent(...) : BukkitEvent() {
 *     companion object {
 *         @JvmStatic val HANDLERS = HandlerList()
 *         @JvmStatic fun getHandlerList() = HANDLERS
 *     }
 *     override fun getHandlers() = HANDLERS
 * }
 * ```
 */
abstract class BukkitEvent : Event() {
    abstract override fun getHandlers(): HandlerList
}
