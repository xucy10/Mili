package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TemptingSensor extends Sensor<PathfinderMob> {
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private final BiPredicate<PathfinderMob, ItemStack> temptations;

    public TemptingSensor(Predicate<ItemStack> temptations) {
        this((pathfinderMob, itemStack) -> temptations.test(itemStack));
    }

    public static TemptingSensor forAnimal() {
        return new TemptingSensor((pathfinderMob, itemStack) -> pathfinderMob instanceof Animal animal && animal.isFood(itemStack));
    }

    private TemptingSensor(BiPredicate<PathfinderMob, ItemStack> temptations) {
        this.temptations = temptations;
    }

    @Override
    protected void doTick(ServerLevel level, PathfinderMob entity) {
        Brain<?> brain = entity.getBrain();
        TargetingConditions targetingConditions = TEMPT_TARGETING.copy().range((float)entity.getAttributeValue(Attributes.TEMPT_RANGE));
        List<Player> list = level.getLocalPlayers() // Folia - region threading
            .stream()
            .filter(EntitySelector.NO_SPECTATORS)
            .filter(serverPlayer -> targetingConditions.test(level, entity, serverPlayer))
            .filter(serverPlayer -> this.playerHoldingTemptation(entity, serverPlayer))
            .filter(serverPlayer -> !entity.hasPassenger(serverPlayer))
            .sorted(Comparator.comparingDouble(entity::distanceToSqr))
            .collect(Collectors.toList());
        if (!list.isEmpty()) {
            Player player = list.get(0);
            // CraftBukkit start
            org.bukkit.event.entity.EntityTargetLivingEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(
                entity, player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TEMPT
            );
            if (event.isCancelled()) {
                return;
            }
            if (event.getTarget() instanceof org.bukkit.craftbukkit.entity.CraftHumanEntity target) {
                brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, target.getHandle());
            } else {
                brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            }
            // CraftBukkit end
        } else {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }
    }

    private boolean playerHoldingTemptation(PathfinderMob mob, Player player) {
        return this.isTemptation(mob, player.getMainHandItem()) || this.isTemptation(mob, player.getOffhandItem());
    }

    private boolean isTemptation(PathfinderMob mob, ItemStack stack) {
        return this.temptations.test(mob, stack);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
    }
}
