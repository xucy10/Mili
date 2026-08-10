package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.pathfinder.Path;

public class NearestBedSensor extends Sensor<Mob> {
    private static final int CACHE_TIMEOUT = 40;
    private static final int BATCH_SIZE = 5;
    private static final int RATE = 20;
    private final Long2LongMap batchCache = new Long2LongOpenHashMap();
    private int triedCount;
    private long lastUpdate;

    public NearestBedSensor() {
        super(20);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_BED);
    }

    @Override
    protected void doTick(ServerLevel level, Mob entity) {
        if (entity.isBaby()) {
            this.triedCount = 0;
            this.lastUpdate = level.getGameTime() + level.getRandom().nextInt(20);
            PoiManager poiManager = level.getPoiManager();
            Predicate<BlockPos> predicate = pos -> {
                long packedBlockPos = pos.asLong();
                if (this.batchCache.containsKey(packedBlockPos)) {
                    return false;
                } else if (++this.triedCount >= 5) {
                    return false;
                } else {
                    this.batchCache.put(packedBlockPos, this.lastUpdate + 40L);
                    return true;
                }
            };
            // Paper start - optimise POI access
            java.util.List<Pair<Holder<PoiType>, BlockPos>> poiposes = new java.util.ArrayList<>();
            // don't ask me why it's unbounded. ask mojang.
            io.papermc.paper.util.PoiAccess.findAnyPoiPositions(poiManager, type -> type.is(PoiTypes.HOME), predicate, entity.blockPosition(), 48, PoiManager.Occupancy.ANY, false, Integer.MAX_VALUE, poiposes);
            Path path = AcquirePoi.findPathToPois(entity, new java.util.HashSet<>(poiposes));
            // Paper end - optimise POI access
            if (path != null && path.canReach()) {
                BlockPos target = path.getTarget();
                Optional<Holder<PoiType>> type = poiManager.getType(target);
                if (type.isPresent()) {
                    entity.getBrain().setMemory(MemoryModuleType.NEAREST_BED, target);
                }
            } else if (this.triedCount < 5) {
                this.batchCache.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < this.lastUpdate);
            }
        }
    }
}
