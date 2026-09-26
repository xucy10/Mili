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
 * Entity tick limiter.
 * <p>
 * fix.md §12: {@code tickLimiterShouldSkip} is on the entity tick hot path, so it must
 * not allocate. It previously returned a freshly built {@code EntityThrottlerReturn} for
 * every entity every tick; it now returns an {@code int} verdict from
 * {@link EntityTickDecision} ({@code TICK}/{@code SKIP}/{@code REMOVE}), which is
 * allocation-free.
 */
public class KaiijuEntityThrottler {
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
     * Decide what to do with this entity this tick.
     *
     * @return {@link EntityTickDecision#TICK}, {@link EntityTickDecision#SKIP} or
     *         {@link EntityTickDecision#REMOVE}; zero allocation (fix.md §12)
     */
    public int tickLimiterShouldSkip(Entity entity) {
        if (entity.isRemoved()) return EntityTickDecision.TICK;
        KaiijuEntityLimits.EntityLimit entityLimit = KaiijuEntityLimits.getEntityLimit(entity);

        if (entityLimit == null) {
            return EntityTickDecision.TICK;
        }

        TickInfo tickInfo = entityLimitTickInfoMap.computeIfAbsent(entityLimit, el -> {
            TickInfo newTickInfo = new TickInfo();
            newTickInfo.toTick = entityLimit.limit();
            return newTickInfo;
        });

        tickInfo.currentTick++;
        if (tickInfo.currentTick <= tickInfo.toRemove && entityLimit.removal() > 0) {
            return EntityTickDecision.REMOVE;
        }

        if (tickInfo.currentTick < tickInfo.continueFrom) {
            return EntityTickDecision.SKIP;
        }
        if (tickInfo.currentTick - tickInfo.continueFrom < tickInfo.toTick) {
            return EntityTickDecision.TICK;
        }
        return EntityTickDecision.SKIP;
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