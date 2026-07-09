package fun.bm.mili.utils

import fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig
import io.papermc.paper.threadedregions.RegionizedWorldData
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cross-Region Helper — Kotlin rewrite with sealed event types.
 *
 * Replaces the Java version's `Object payload` pattern with typed sealed classes,
 * eliminating runtime type casts and making the dispatch logic type-safe.
 */
object CrossRegionHelper {

    private val eventCounter = AtomicLong(0)

    sealed class Event(
        val id: Long = eventCounter.incrementAndGet(),
        val sourceRegion: RegionizedWorldData,
        val targetRegion: RegionizedWorldData,
        val tickStamp: Long,
    ) {
        data class RedstoneSignal(
            val pos: BlockPos, val neighbor: BlockPos, val dir: Direction,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)

        data class EntityEnterRegion(
            val entityUUID: UUID,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)

        data class EntityLeaveRegion(
            val entityUUID: UUID,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)

        data class BlockNotify(
            val pos: BlockPos,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)

        data class EntityDamageSync(
            val sourceUUID: UUID, val targetUUID: UUID, val damageSource: DamageSource,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)

        data class Generic(
            val payload: Any,
            val src: RegionizedWorldData, val tgt: RegionizedWorldData, val tick: Long,
        ) : Event(sourceRegion = src, targetRegion = tgt, tickStamp = tick)
    }

    private val eventQueue = LinkedBlockingQueue<Event>()
    private val pendingByRegion = ConcurrentHashMap<RegionizedWorldData, ConcurrentLinkedQueue<Event>>()

    @Volatile private var running = false

    private val helperThread = Thread({
        running = true
        while (running) {
            try {
                val event = eventQueue.poll(CrossRegionHelperConfig.queuePollTimeoutMs, TimeUnit.MILLISECONDS) ?: continue
                val queue = pendingByRegion.computeIfAbsent(event.targetRegion) { ConcurrentLinkedQueue() }
                val max = CrossRegionHelperConfig.maxPendingEventsPerRegion
                while (queue.size >= max) { queue.poll() }
                queue.add(event)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (ex: Exception) {
                // log error
            }
        }
    }, "CrossRegion-Helper").apply {
        isDaemon = true
        start()
    }

    fun submit(event: Event) {
        if (!CrossRegionHelperConfig.enabled) return
        eventQueue.add(event)
    }

    fun submitRedstoneCrossRegion(sl: ServerLevel?, pos: BlockPos, neighbor: BlockPos, dir: Direction) {
        if (!CrossRegionHelperConfig.enabled || sl == null) return
        val s = sl.currentWorldData ?: return
        submit(Event.RedstoneSignal(pos, neighbor, dir, s, s, sl.gameTime))
    }

    fun submitDamageCrossRegion(src: LivingEntity?, tgt: LivingEntity?, ds: DamageSource?, tick: Long) {
        if (!CrossRegionHelperConfig.enabled || src == null || tgt == null || ds == null) return
        val s = src.level().currentWorldData ?: return
        val t = tgt.level().currentWorldData ?: return
        if (s != t) {
            submit(Event.EntityDamageSync(src.uuid, tgt.uuid, ds, s, t, tick))
        }
    }

    @JvmStatic
    fun consumePending(targetRegion: RegionizedWorldData): ConcurrentLinkedQueue<Event>? {
        if (!CrossRegionHelperConfig.enabled) return null
        return pendingByRegion.remove(targetRegion)
    }

    @JvmStatic
    fun onRegionTick(level: ServerLevel, data: RegionizedWorldData): ConcurrentLinkedQueue<Event>? {
        if (!CrossRegionHelperConfig.enabled || data == null) return null
        return consumePending(data)
    }

    @JvmStatic
    fun pendingCount(targetRegion: RegionizedWorldData): Int = pendingByRegion[targetRegion]?.size ?: 0

    @JvmStatic
    fun inboundQueueSize(): Int = eventQueue.size

    @JvmStatic
    fun onRegionUnload(data: RegionizedWorldData?) {
        if (data != null) pendingByRegion.remove(data)
    }

    @JvmStatic
    fun shutdown() {
        running = false
        helperThread.interrupt()
    }
}