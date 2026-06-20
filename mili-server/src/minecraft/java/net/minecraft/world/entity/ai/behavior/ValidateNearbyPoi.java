package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ValidateNearbyPoi {
    private static final int MAX_DISTANCE = 16;

    public static BehaviorControl<LivingEntity> create(Predicate<Holder<PoiType>> poiValidator, MemoryModuleType<GlobalPos> poiPosMemory) {
        return BehaviorBuilder.create(instance -> instance.group(instance.present(poiPosMemory)).apply(instance, poiPos -> (level, entity, gameTime) -> {
            GlobalPos globalPos = instance.get(poiPos);
            BlockPos blockPos = globalPos.pos();
            if (level.dimension() == globalPos.dimension() && blockPos.closerToCenterThan(entity.position(), 16.0)) {
                ServerLevel level1 = level.getServer().getLevel(globalPos.dimension());
                if (level1 == null || !level1.getPoiManager().exists(blockPos, poiValidator)) {
                    poiPos.erase();
                } else if (bedIsOccupied(level1, blockPos, entity)) {
                    poiPos.erase();
                    if (!bedIsOccupiedByVillager(level1, blockPos)) {
                        level.getPoiManager().release(blockPos);
                        level.debugSynchronizers().updatePoi(blockPos);
                    }
                }

                return true;
            } else {
                return false;
            }
        }));
    }

    private static boolean bedIsOccupied(ServerLevel level, BlockPos pos, LivingEntity entity) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && blockState.getValue(BedBlock.OCCUPIED) && !entity.isSleeping();
    }

    private static boolean bedIsOccupiedByVillager(ServerLevel level, BlockPos pos) {
        List<Villager> entitiesOfClass = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        return !entitiesOfClass.isEmpty();
    }
}
