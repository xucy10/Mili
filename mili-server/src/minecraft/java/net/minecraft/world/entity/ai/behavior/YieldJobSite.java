package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;

public class YieldJobSite {
    public static BehaviorControl<Villager> create(float speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.present(MemoryModuleType.POTENTIAL_JOB_SITE),
                    instance.absent(MemoryModuleType.JOB_SITE),
                    instance.present(MemoryModuleType.NEAREST_LIVING_ENTITIES),
                    instance.registered(MemoryModuleType.WALK_TARGET),
                    instance.registered(MemoryModuleType.LOOK_TARGET)
                )
                .apply(
                    instance,
                    (potentialJobSite, jobSite, nearestLivingEntities, walkTarget, lookTarget) -> (level, villager, gameTime) -> {
                        if (villager.isBaby()) {
                            return false;
                        } else if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
                            return false;
                        } else {
                            // Folia start - region threading
                            GlobalPos globalPos = instance.get(potentialJobSite);
                            BlockPos blockPos = globalPos.pos();
                            if (globalPos.dimension() != level.dimension() || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level, blockPos)) {
                                return true;
                            }
                            // Folia end - region threading
                            Optional<Holder<PoiType>> type = level.getPoiManager().getType(blockPos);
                            if (type.isEmpty()) {
                                return true;
                            } else {
                                instance.<List<LivingEntity>>get(nearestLivingEntities)
                                    .stream()
                                    .filter(nearEntity -> nearEntity instanceof Villager && nearEntity != villager)
                                    .map(nearEntity -> (Villager)nearEntity)
                                    .filter(LivingEntity::isAlive)
                                    .filter(nearVillager -> nearbyWantsJobsite(type.get(), nearVillager, blockPos))
                                    .findFirst()
                                    .ifPresent(nearVillager -> {
                                        walkTarget.erase();
                                        lookTarget.erase();
                                        potentialJobSite.erase();
                                        if (nearVillager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
                                            BehaviorUtils.setWalkAndLookTargetMemories(nearVillager, blockPos, speedModifier, 1);
                                            nearVillager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), blockPos));
                                            level.debugSynchronizers().updatePoi(blockPos);
                                        }
                                    });
                                return true;
                            }
                        }
                    }
                )
        );
    }

    private static boolean nearbyWantsJobsite(Holder<PoiType> poi, Villager villager, BlockPos pos) {
        boolean isPresent = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent();
        if (isPresent) {
            return false;
        } else {
            Optional<GlobalPos> memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            Holder<VillagerProfession> holder = villager.getVillagerData().profession();
            if (holder.value().heldJobSite().test(poi)) {
                return memory.isEmpty() ? canReachPos(villager, pos, poi.value()) : memory.get().pos().equals(pos);
            } else {
                return false;
            }
        }
    }

    private static boolean canReachPos(PathfinderMob mob, BlockPos pos, PoiType poiType) {
        Path path = mob.getNavigation().createPath(pos, poiType.validRange());
        return path != null && path.canReach();
    }
}
