package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;

public class HoglinSpecificSensor extends Sensor<Hoglin> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_REPELLENT,
            MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN,
            MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS,
            MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT,
            MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT
        );
    }

    @Override
    protected void doTick(ServerLevel level, Hoglin entity) {
        Brain<?> brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_REPELLENT, this.findNearestRepellent(level, entity));
        Optional<Piglin> optional = Optional.empty();
        int i = 0;
        List<Hoglin> list = Lists.newArrayList();
        NearestVisibleLivingEntities nearestVisibleLivingEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
            .orElse(NearestVisibleLivingEntities.empty());

        for (LivingEntity livingEntity : nearestVisibleLivingEntities.findAll(
            entity1 -> !entity1.isBaby() && (entity1 instanceof Piglin || entity1 instanceof Hoglin)
        )) {
            if (livingEntity instanceof Piglin piglin) {
                i++;
                if (optional.isEmpty()) {
                    optional = Optional.of(piglin);
                }
            }

            if (livingEntity instanceof Hoglin hoglin) {
                list.add(hoglin);
            }
        }

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, optional);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS, list);
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, i);
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, list.size());
    }

    private Optional<BlockPos> findNearestRepellent(ServerLevel level, Hoglin hoglin) {
        return BlockPos.findClosestMatch(hoglin.blockPosition(), 8, 4, pos -> level.getBlockState(pos).is(BlockTags.HOGLIN_REPELLENTS));
    }
}
