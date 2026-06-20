package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public interface SpawnPlacementType {
    boolean isSpawnPositionOk(LevelReader level, BlockPos pos, @Nullable EntityType<?> entityType);

    default BlockPos adjustSpawnPosition(LevelReader level, BlockPos pos) {
        return pos;
    }
}
