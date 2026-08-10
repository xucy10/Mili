package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;

public class VillagerMakeLove extends Behavior<Villager> {
    private long birthTimestamp;

    public VillagerMakeLove() {
        super(
            ImmutableMap.of(
                MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
            ),
            350,
            350
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return this.isBreedingPossible(owner);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return gameTime <= this.birthTimestamp && this.isBreedingPossible(entity);
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        AgeableMob ageableMob = entity.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, ageableMob, 0.5F, 2);
        level.broadcastEntityEvent(ageableMob, EntityEvent.IN_LOVE_HEARTS);
        level.broadcastEntityEvent(entity, EntityEvent.IN_LOVE_HEARTS);
        int i = 275 + entity.getRandom().nextInt(50);
        this.birthTimestamp = gameTime + i;
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Villager villager = (Villager)owner.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        if (!(owner.distanceToSqr(villager) > 5.0)) {
            BehaviorUtils.lockGazeAndWalkToEachOther(owner, villager, 0.5F, 2);
            if (gameTime >= this.birthTimestamp) {
                owner.eatAndDigestFood();
                villager.eatAndDigestFood();
                this.tryToGiveBirth(level, owner, villager);
            } else if (owner.getRandom().nextInt(35) == 0) {
                level.broadcastEntityEvent(villager, EntityEvent.LOVE_HEARTS);
                level.broadcastEntityEvent(owner, EntityEvent.LOVE_HEARTS);
            }
        }
    }

    private void tryToGiveBirth(ServerLevel level, Villager parent, Villager partner) {
        Optional<BlockPos> optional = this.takeVacantBed(level, parent);
        if (optional.isEmpty()) {
            level.broadcastEntityEvent(partner, EntityEvent.VILLAGER_ANGRY);
            level.broadcastEntityEvent(parent, EntityEvent.VILLAGER_ANGRY);
        } else {
            Optional<Villager> optional1 = this.breed(level, parent, partner);
            if (optional1.isPresent()) {
                this.giveBedToChild(level, optional1.get(), optional.get());
            } else {
                level.getPoiManager().release(optional.get());
                level.debugSynchronizers().updatePoi(optional.get());
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        entity.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
    }

    private boolean isBreedingPossible(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        Optional<AgeableMob> optional = brain.getMemory(MemoryModuleType.BREED_TARGET).filter(entity -> entity.getType() == EntityType.VILLAGER);
        return !optional.isEmpty()
            && BehaviorUtils.targetIsValid(brain, MemoryModuleType.BREED_TARGET, EntityType.VILLAGER)
            && villager.canBreed()
            && optional.get().canBreed();
    }

    private Optional<BlockPos> takeVacantBed(ServerLevel level, Villager villager) {
        return level.getPoiManager()
            .take(holder -> holder.is(PoiTypes.HOME), (holder, blockPos) -> this.canReach(villager, blockPos, holder), villager.blockPosition(), 48);
    }

    private boolean canReach(Villager villager, BlockPos pos, Holder<PoiType> poiType) {
        Path path = villager.getNavigation().createPath(pos, poiType.value().validRange());
        return path != null && path.canReach();
    }

    private Optional<Villager> breed(ServerLevel level, Villager parent, Villager partner) {
        Villager breedOffspring = parent.getBreedOffspring(level, partner);
        if (breedOffspring == null) {
            return Optional.empty();
        } else {
            breedOffspring.setAge(-24000);
            // Paper - Move age setting down
            breedOffspring.snapTo(parent.getX(), parent.getY(), parent.getZ(), 0.0F, 0.0F);
            // CraftBukkit start - call EntityBreedEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(breedOffspring, parent, partner, null, null, 0).isCancelled()) {
                return Optional.empty();
            }
            parent.setAge(6000);
            partner.setAge(6000);
            level.addFreshEntityWithPassengers(breedOffspring, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING);
            // CraftBukkit end - call EntityBreedEvent
            level.broadcastEntityEvent(breedOffspring, EntityEvent.LOVE_HEARTS);
            return Optional.of(breedOffspring);
        }
    }

    private void giveBedToChild(ServerLevel level, Villager villager, BlockPos pos) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        villager.getBrain().setMemory(MemoryModuleType.HOME, globalPos);
    }
}
