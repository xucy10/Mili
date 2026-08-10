package net.minecraft.world.level;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface CommonLevelAccessor extends EntityGetter, LevelReader, LevelSimulatedRW {
    @Override
    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        return LevelReader.super.getBlockEntity(pos, type);
    }

    @Override
    default List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        return EntityGetter.super.getEntityCollisions(entity, collisionBox);
    }

    @Override
    default boolean isUnobstructed(@Nullable Entity entity, VoxelShape shape) {
        return EntityGetter.super.isUnobstructed(entity, shape);
    }

    @Override
    default BlockPos getHeightmapPos(Heightmap.Types heightmapType, BlockPos pos) {
        return LevelReader.super.getHeightmapPos(heightmapType, pos);
    }
}
