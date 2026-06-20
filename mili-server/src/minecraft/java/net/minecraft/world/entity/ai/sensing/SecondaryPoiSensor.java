package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

public class SecondaryPoiSensor extends Sensor<Villager> {
    private static final int SCAN_RATE = 40;

    public SecondaryPoiSensor() {
        super(40);
    }

    @Override
    protected void doTick(ServerLevel level, Villager entity) {
        ResourceKey<Level> resourceKey = level.dimension();
        BlockPos blockPos = entity.blockPosition();
        List<GlobalPos> list = Lists.newArrayList();
        int i = 4;

        for (int i1 = -4; i1 <= 4; i1++) {
            for (int i2 = -2; i2 <= 2; i2++) {
                for (int i3 = -4; i3 <= 4; i3++) {
                    BlockPos blockPos1 = blockPos.offset(i1, i2, i3);
                    if (entity.getVillagerData().profession().value().secondaryPoi().contains(level.getBlockState(blockPos1).getBlock())) {
                        list.add(GlobalPos.of(resourceKey, blockPos1));
                    }
                }
            }
        }

        Brain<?> brain = entity.getBrain();
        if (!list.isEmpty()) {
            brain.setMemory(MemoryModuleType.SECONDARY_JOB_SITE, list);
        } else {
            brain.eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.SECONDARY_JOB_SITE);
    }
}
