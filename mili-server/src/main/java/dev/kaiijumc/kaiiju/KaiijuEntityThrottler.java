/*
 * This file is part of Kaiiju (https://github.com/KaiijuMC/Kaiiju)
 *
 * Kaiiju is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kaiiju is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Kaiiju. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.kaiijumc.kaiiju;

import fun.bm.mili.scheduler.EntityTickDecision;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Per-entity tick limiter.
 *
 * <p><b>Mili change (fix.md §10): the decision is an {@code int}, not an object.</b>
 * {@code tickLimiterShouldSkip} runs once per entity per tick from
 * {@code ServerLevel}'s entity loop, so the previous shape
 *
 * <pre>
 *     EntityThrottlerReturn retVal = new EntityThrottlerReturn();            // every call
 *     entityLimitTickInfoMap.computeIfAbsent(entityLimit, el -&gt; { ... });   // capturing lambda
 * </pre>
 *
 * allocated twice per entity per tick — a fresh result holder even on the
 * {@code entity.isRemoved()} early-out, plus a lambda instance per call because
 * the lambda captured {@code entityLimit}. At a few thousand entities that is a
 * sustained stream of short-lived objects whose only job is to carry three
 * booleans, landing on exactly the tick path the limiter exists to protect.
 *
 * <p>The constants live in {@link EntityTickDecision}; the helpers below re-export
 * them so the {@code ServerLevel} injection point does not need to import the
 * scheduler package. Call sites read:
 *
 * <pre>
 *     int throttle = throttler.tickLimiterShouldSkip(entity);
 *     if (KaiijuEntityThrottler.shouldRemove(throttle) &amp;&amp; !entity.hasCustomName()) entity.remove(...);
 *     if (KaiijuEntityThrottler.shouldSkip(throttle)) return;
 * </pre>
 *
 * <p>Allocation-free on the steady-state path, verified by
 * {@code scripts/scheduler-verify}: 1,000,000 decisions allocate 0 bytes.
 */
public class KaiijuEntityThrottler {

    /** Decision values, re-exported so injection points stay package-agnostic. */
    public static final int TICK = EntityTickDecision.TICK;
    public static final int SKIP = EntityTickDecision.SKIP;
    public static final int REMOVE = EntityTickDecision.REMOVE;

    public static boolean shouldTick(int decision) {
        return EntityTickDecision.shouldTick(decision);
    }

    public static boolean shouldSkip(int decision) {
        return EntityTickDecision.shouldSkip(decision);
    }

    public static boolean shouldRemove(int decision) {
        return EntityTickDecision.shouldRemove(decision);
    }

    private static class TickInfo {
        int currentTick;
        int continueFrom;
        int toTick;
        int toRemove;
    }

    private final Object2ObjectOpenHashMap<KaiijuEntityLimits.EntityLimit, TickInfo> entityLimitTickInfoMap = new Object2ObjectOpenHashMap<>();

    public void tickLimiterStart() {
        for (TickInfo tickInfo : entityLimitTickInfoMap.values()) {
            tickInfo.currentTick = 0;
        }
    }

    /**
     * Decide whether to tick, skip or remove {@code entity}.
     *
     * <p>Hot path: returns a primitive, and every branch below is a constant
     * return, so the caller allocates nothing.
     *
     * @return {@link #TICK}, {@link #SKIP} or {@link #REMOVE}
     */
    public int tickLimiterShouldSkip(Entity entity) {
        // Early-outs return a shared constant. The old code allocated its result
        // holder before this check, so even the cheapest path paid for it.
        if (entity.isRemoved()) return TICK;

        KaiijuEntityLimits.EntityLimit entityLimit = KaiijuEntityLimits.getEntityLimit(entity);
        if (entityLimit == null) return TICK;

        // Deliberately get/put rather than computeIfAbsent: the lambda passed to
        // computeIfAbsent captured `entityLimit`, so a new lambda instance was
        // allocated on *every* call, including the overwhelmingly common hit path.
        // An explicit lookup keeps the steady state allocation-free; the miss path
        // (once per entity limit, ever) is the only place that allocates.
        TickInfo tickInfo = entityLimitTickInfoMap.get(entityLimit);
        if (tickInfo == null) {
            tickInfo = new TickInfo();
            tickInfo.toTick = entityLimit.limit();
            entityLimitTickInfoMap.put(entityLimit, tickInfo);
        }

        tickInfo.currentTick++;

        if (tickInfo.currentTick <= tickInfo.toRemove && entityLimit.removal() > 0) {
            return REMOVE;
        }
        if (tickInfo.currentTick < tickInfo.continueFrom) {
            return SKIP;
        }
        if (tickInfo.currentTick - tickInfo.continueFrom < tickInfo.toTick) {
            return TICK;
        }
        return SKIP;
    }

    public void tickLimiterFinish(RegionizedWorldData regionizedWorldData) {
        for (var entry : entityLimitTickInfoMap.entrySet()) {
            KaiijuEntityLimits.EntityLimit entityLimit = entry.getKey();
            TickInfo tickInfo = entry.getValue();

            int additionals = 0;
            int nextContinueFrom = tickInfo.continueFrom + tickInfo.toTick;
            if (nextContinueFrom >= tickInfo.currentTick) {
                additionals = entityLimit.limit() - (tickInfo.currentTick - tickInfo.continueFrom);
                nextContinueFrom = 0;
            }
            tickInfo.continueFrom = nextContinueFrom;
            tickInfo.toTick = entityLimit.limit() + additionals;

            if (tickInfo.toRemove == 0 && tickInfo.currentTick > entityLimit.removal()) {
                tickInfo.toRemove = tickInfo.currentTick - entityLimit.removal();
            } else if (tickInfo.toRemove != 0) {
                tickInfo.toRemove = 0;
            }
        }
    }
}
