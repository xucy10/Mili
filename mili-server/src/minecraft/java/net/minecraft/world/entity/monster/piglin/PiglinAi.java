package net.minecraft.world.entity.monster.piglin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BackUpIfTooClose;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.CopyMemoryWithExpiry;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import net.minecraft.world.entity.ai.behavior.DismountOrSkipMounting;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.GoToTargetLocation;
import net.minecraft.world.entity.ai.behavior.GoToWantedItem;
import net.minecraft.world.entity.ai.behavior.InteractWith;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.Mount;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.SpearApproach;
import net.minecraft.world.entity.ai.behavior.SpearAttack;
import net.minecraft.world.entity.ai.behavior.SpearRetreat;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StartCelebratingIfTargetDead;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.StopBeingAngryIfTargetDead;
import net.minecraft.world.entity.ai.behavior.TriggerGate;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class PiglinAi {
    public static final int REPELLENT_DETECTION_RANGE_HORIZONTAL = 8;
    public static final int REPELLENT_DETECTION_RANGE_VERTICAL = 4;
    public static final Item BARTERING_ITEM = Items.GOLD_INGOT;
    private static final int PLAYER_ANGER_RANGE = 16;
    private static final int ANGER_DURATION = 600;
    private static final int ADMIRE_DURATION = 119;
    private static final int MAX_DISTANCE_TO_WALK_TO_ITEM = 9;
    private static final int MAX_TIME_TO_WALK_TO_ITEM = 200;
    private static final int HOW_LONG_TIME_TO_DISABLE_ADMIRE_WALKING_IF_CANT_REACH_ITEM = 200;
    private static final int CELEBRATION_TIME = 300;
    protected static final UniformInt TIME_BETWEEN_HUNTS = TimeUtil.rangeOfSeconds(30, 120);
    private static final int BABY_FLEE_DURATION_AFTER_GETTING_HIT = 100;
    private static final int HIT_BY_PLAYER_MEMORY_TIMEOUT = 400;
    private static final int MAX_WALK_DISTANCE_TO_START_RIDING = 8;
    private static final UniformInt RIDE_START_INTERVAL = TimeUtil.rangeOfSeconds(10, 40);
    private static final UniformInt RIDE_DURATION = TimeUtil.rangeOfSeconds(10, 30);
    private static final UniformInt RETREAT_DURATION = TimeUtil.rangeOfSeconds(5, 20);
    private static final int MELEE_ATTACK_COOLDOWN = 20;
    private static final int EAT_COOLDOWN = 200;
    private static final int DESIRED_DISTANCE_FROM_ENTITY_WHEN_AVOIDING = 12;
    private static final int MAX_LOOK_DIST = 8;
    private static final int MAX_LOOK_DIST_FOR_PLAYER_HOLDING_LOVED_ITEM = 14;
    private static final int INTERACTION_RANGE = 8;
    private static final int MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW = 5;
    private static final float SPEED_WHEN_STRAFING_BACK_FROM_TARGET = 0.75F;
    private static final int DESIRED_DISTANCE_FROM_ZOMBIFIED = 6;
    private static final UniformInt AVOID_ZOMBIFIED_DURATION = TimeUtil.rangeOfSeconds(5, 7);
    private static final UniformInt BABY_AVOID_NEMESIS_DURATION = TimeUtil.rangeOfSeconds(5, 7);
    private static final float PROBABILITY_OF_CELEBRATION_DANCE = 0.1F;
    private static final float SPEED_MULTIPLIER_WHEN_AVOIDING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_RETREATING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_MOUNTING = 0.8F;
    private static final float SPEED_MULTIPLIER_WHEN_GOING_TO_WANTED_ITEM = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_GOING_TO_CELEBRATE_LOCATION = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_DANCING = 0.6F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.6F;

    protected static Brain<?> makeBrain(Piglin piglin, Brain<Piglin> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initAdmireItemActivity(brain);
        initFightActivity(piglin, brain);
        initCelebrateActivity(brain);
        initRetreatActivity(brain);
        initRideHoglinActivity(brain);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    protected static void initMemories(Piglin piglin, RandomSource random) {
        int i = TIME_BETWEEN_HUNTS.sample(random);
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, i);
    }

    private static void initCoreActivity(Brain<Piglin> brain) {
        brain.addActivity(
            Activity.CORE,
            0,
            ImmutableList.of(
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink(),
                InteractWithDoor.create(),
                babyAvoidNemesis(),
                avoidZombified(),
                StopHoldingItemIfNoLongerAdmiring.create(),
                StartAdmiringItemIfSeen.create(119),
                StartCelebratingIfTargetDead.create(300, PiglinAi::wantsToDance),
                StopBeingAngryIfTargetDead.create()
            )
        );
    }

    private static void initIdleActivity(Brain<Piglin> brain) {
        brain.addActivity(
            Activity.IDLE,
            10,
            ImmutableList.of(
                SetEntityLookTarget.create(PiglinAi::isPlayerHoldingLovedItem, 14.0F),
                StartAttacking.<Piglin>create((level, piglin) -> piglin.isAdult(), PiglinAi::findNearestValidAttackTarget),
                BehaviorBuilder.triggerIf(Piglin::canHunt, StartHuntingHoglin.create()),
                avoidRepellent(),
                babySometimesRideBabyHoglin(),
                createIdleLookBehaviors(),
                createIdleMovementBehaviors(),
                SetLookAndInteract.create(EntityType.PLAYER, 4)
            )
        );
    }

    private static void initFightActivity(Piglin piglin, Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.FIGHT,
            10,
            ImmutableList.<BehaviorControl<? super Piglin>>of(
                StopAttackingIfTargetInvalid.create((level, entity) -> !isNearestValidAttackTarget(level, piglin, entity)),
                BehaviorBuilder.triggerIf(PiglinAi::hasCrossbow, BackUpIfTooClose.create(5, 0.75F)),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                new SpearApproach(1.0, 10.0F),
                new SpearAttack(1.0, 1.0, 10.0F, 2.0F),
                new SpearRetreat(1.0),
                MeleeAttack.create(20),
                new CrossbowAttack(),
                RememberIfHoglinWasKilled.create(),
                EraseMemoryIf.create(PiglinAi::isNearZombified, MemoryModuleType.ATTACK_TARGET)
            ),
            MemoryModuleType.ATTACK_TARGET
        );
    }

    private static void initCelebrateActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.CELEBRATE,
            10,
            ImmutableList.of(
                avoidRepellent(),
                SetEntityLookTarget.create(PiglinAi::isPlayerHoldingLovedItem, 14.0F),
                StartAttacking.<Piglin>create((level, piglin) -> piglin.isAdult(), PiglinAi::findNearestValidAttackTarget),
                BehaviorBuilder.triggerIf(level -> !level.isDancing(), GoToTargetLocation.create(MemoryModuleType.CELEBRATE_LOCATION, 2, 1.0F)),
                BehaviorBuilder.triggerIf(Piglin::isDancing, GoToTargetLocation.create(MemoryModuleType.CELEBRATE_LOCATION, 4, 0.6F)),
                new RunOne<>(
                    ImmutableList.of(
                        Pair.of(SetEntityLookTarget.create(EntityType.PIGLIN, 8.0F), 1),
                        Pair.of(RandomStroll.stroll(0.6F, 2, 1), 1),
                        Pair.of(new DoNothing(10, 20), 1)
                    )
                )
            ),
            MemoryModuleType.CELEBRATE_LOCATION
        );
    }

    private static void initAdmireItemActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.ADMIRE_ITEM,
            10,
            ImmutableList.of(
                GoToWantedItem.create(PiglinAi::isNotHoldingLovedItemInOffHand, 1.0F, true, 9),
                StopAdmiringIfItemTooFarAway.create(9),
                StopAdmiringIfTiredOfTryingToReachItem.create(200, 200)
            ),
            MemoryModuleType.ADMIRING_ITEM
        );
    }

    private static void initRetreatActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.AVOID,
            10,
            ImmutableList.of(
                SetWalkTargetAwayFrom.entity(MemoryModuleType.AVOID_TARGET, 1.0F, 12, true),
                createIdleLookBehaviors(),
                createIdleMovementBehaviors(),
                EraseMemoryIf.create(PiglinAi::wantsToStopFleeing, MemoryModuleType.AVOID_TARGET)
            ),
            MemoryModuleType.AVOID_TARGET
        );
    }

    private static void initRideHoglinActivity(Brain<Piglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(
            Activity.RIDE,
            10,
            ImmutableList.of(
                Mount.create(0.8F),
                SetEntityLookTarget.create(PiglinAi::isPlayerHoldingLovedItem, 8.0F),
                BehaviorBuilder.sequence(
                    BehaviorBuilder.triggerIf(Entity::isPassenger),
                    TriggerGate.triggerOneShuffled(
                        ImmutableList.<Pair<? extends Trigger<? super LivingEntity>, Integer>>builder()
                            .addAll(createLookBehaviors())
                            .add(Pair.of(BehaviorBuilder.triggerIf(piglin -> true), 1))
                            .build()
                    )
                ),
                DismountOrSkipMounting.create(8, PiglinAi::wantsToStopRiding)
            ),
            MemoryModuleType.RIDE_TARGET
        );
    }

    private static ImmutableList<Pair<OneShot<LivingEntity>, Integer>> createLookBehaviors() {
        return ImmutableList.of(
            Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 1),
            Pair.of(SetEntityLookTarget.create(EntityType.PIGLIN, 8.0F), 1),
            Pair.of(SetEntityLookTarget.create(8.0F), 1)
        );
    }

    private static RunOne<LivingEntity> createIdleLookBehaviors() {
        return new RunOne<>(
            ImmutableList.<Pair<? extends BehaviorControl<? super LivingEntity>, Integer>>builder()
                .addAll(createLookBehaviors())
                .add(Pair.of(new DoNothing(30, 60), 1))
                .build()
        );
    }

    private static RunOne<Piglin> createIdleMovementBehaviors() {
        return new RunOne<>(
            ImmutableList.of(
                Pair.of(RandomStroll.stroll(0.6F), 2),
                Pair.of(InteractWith.of(EntityType.PIGLIN, 8, MemoryModuleType.INTERACTION_TARGET, 0.6F, 2), 2),
                Pair.of(BehaviorBuilder.triggerIf(PiglinAi::doesntSeeAnyPlayerHoldingLovedItem, SetWalkTargetFromLookTarget.create(0.6F, 3)), 2),
                Pair.of(new DoNothing(30, 60), 1)
            )
        );
    }

    private static BehaviorControl<PathfinderMob> avoidRepellent() {
        return SetWalkTargetAwayFrom.pos(MemoryModuleType.NEAREST_REPELLENT, 1.0F, 8, false);
    }

    private static BehaviorControl<Piglin> babyAvoidNemesis() {
        return CopyMemoryWithExpiry.create(Piglin::isBaby, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.AVOID_TARGET, BABY_AVOID_NEMESIS_DURATION);
    }

    private static BehaviorControl<Piglin> avoidZombified() {
        return CopyMemoryWithExpiry.create(
            PiglinAi::isNearZombified, MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, MemoryModuleType.AVOID_TARGET, AVOID_ZOMBIFIED_DURATION
        );
    }

    protected static void updateActivity(Piglin piglin) {
        Brain<Piglin> brain = piglin.getBrain();
        Activity activity = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(
            ImmutableList.of(Activity.ADMIRE_ITEM, Activity.FIGHT, Activity.AVOID, Activity.CELEBRATE, Activity.RIDE, Activity.IDLE)
        );
        Activity activity1 = brain.getActiveNonCoreActivity().orElse(null);
        if (activity != activity1) {
            getSoundForCurrentActivity(piglin).ifPresent(piglin::makeSound);
        }

        piglin.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
        if (!brain.hasMemoryValue(MemoryModuleType.RIDE_TARGET) && isBabyRidingBaby(piglin)) {
            piglin.stopRiding();
        }

        if (!brain.hasMemoryValue(MemoryModuleType.CELEBRATE_LOCATION)) {
            brain.eraseMemory(MemoryModuleType.DANCING);
        }

        piglin.setDancing(brain.hasMemoryValue(MemoryModuleType.DANCING));
    }

    private static boolean isBabyRidingBaby(Piglin passenger) {
        if (!passenger.isBaby()) {
            return false;
        } else {
            Entity vehicle = passenger.getVehicle();
            return vehicle instanceof Piglin && ((Piglin)vehicle).isBaby() || vehicle instanceof Hoglin && ((Hoglin)vehicle).isBaby();
        }
    }

    protected static void pickUpItem(ServerLevel level, Piglin piglin, ItemEntity itemEntity) {
        stopWalking(piglin);
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(piglin, itemEntity, itemEntity.getItem().is(Items.GOLD_NUGGET) ? 0 : itemEntity.getItem().getCount() - 1).isCancelled()) return; // Paper
        piglin.onItemPickup(itemEntity); // Paper - moved from Piglin#pickUpItem - call prior to item entity modification
        ItemStack item;
        if (itemEntity.getItem().is(Items.GOLD_NUGGET)) {
            piglin.take(itemEntity, itemEntity.getItem().getCount()); // Paper - diff on change for above event
            item = itemEntity.getItem();
            itemEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        } else {
            piglin.take(itemEntity, 1); // Paper - diff on change for above event
            item = removeOneItemFromItemEntity(itemEntity);
        }

        if (isLovedItem(item, piglin)) { // CraftBukkit - Changes to allow for custom payment in bartering
            piglin.getBrain().eraseMemory(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM);
            holdInOffhand(level, piglin, item);
            admireGoldItem(piglin);
        } else if (isFood(item) && !hasEatenRecently(piglin)) {
            eat(piglin);
        } else {
            boolean flag = !piglin.equipItemIfPossible(level, item, null).equals(ItemStack.EMPTY); // CraftBukkit // Paper - pass null item entity to prevent duplicate pickup item event call - called above.
            if (!flag) {
                putInInventory(piglin, item);
            }
        }
    }

    private static void holdInOffhand(ServerLevel level, Piglin piglin, ItemStack stack) {
        if (isHoldingItemInOffHand(piglin)) {
            piglin.forceDrops = true; // Paper - Add missing forceDrop toggles
            piglin.spawnAtLocation(level, piglin.getItemInHand(InteractionHand.OFF_HAND));
            piglin.forceDrops = false; // Paper - Add missing forceDrop toggles
        }

        piglin.holdInOffHand(stack);
    }

    private static ItemStack removeOneItemFromItemEntity(ItemEntity itemEntity) {
        ItemStack item = itemEntity.getItem();
        ItemStack itemStack = item.split(1);
        if (item.isEmpty()) {
            itemEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        } else {
            itemEntity.setItem(item);
        }

        return itemStack;
    }

    protected static void stopHoldingOffHandItem(ServerLevel level, Piglin piglin, boolean barter) {
        ItemStack itemInHand = piglin.getItemInHand(InteractionHand.OFF_HAND);
        piglin.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (piglin.isAdult()) {
            boolean isBarterCurrency = isBarterCurrency(itemInHand, piglin); // CraftBukkit - Changes to allow custom payment for bartering
            if (barter && isBarterCurrency) {
                // CraftBukkit start
                org.bukkit.event.entity.PiglinBarterEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPiglinBarterEvent(piglin, getBarterResponseItems(piglin), itemInHand);
                if (!event.isCancelled()) {
                    throwItems(piglin, event.getOutcome().stream().map(org.bukkit.craftbukkit.inventory.CraftItemStack::asNMSCopy).collect(java.util.stream.Collectors.toList()));
                }
                // CraftBukkit end
            } else if (!isBarterCurrency) {
                boolean flag = !piglin.equipItemIfPossible(level, itemInHand).isEmpty();
                if (!flag) {
                    putInInventory(piglin, itemInHand);
                }
            }
        } else {
            boolean isBarterCurrency = !piglin.equipItemIfPossible(level, itemInHand).isEmpty();
            if (!isBarterCurrency) {
                ItemStack mainHandItem = piglin.getMainHandItem();
                if (isLovedItem(mainHandItem, piglin)) { // CraftBukkit - Changes to allow for custom payment in bartering
                    putInInventory(piglin, mainHandItem);
                } else {
                    throwItems(piglin, Collections.singletonList(mainHandItem));
                }

                piglin.holdInMainHand(itemInHand);
            }
        }
    }

    protected static void cancelAdmiring(ServerLevel level, Piglin piglin) {
        if (isAdmiringItem(piglin) && !piglin.getOffhandItem().isEmpty()) {
            piglin.forceDrops = true; // Paper - Add missing forceDrop toggles
            piglin.spawnAtLocation(level, piglin.getOffhandItem());
            piglin.forceDrops = false; // Paper - Add missing forceDrop toggles
            piglin.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static void putInInventory(Piglin piglin, ItemStack stack) {
        ItemStack itemStack = piglin.addToInventory(stack);
        throwItemsTowardRandomPos(piglin, Collections.singletonList(itemStack));
    }

    private static void throwItems(Piglin pilgin, List<ItemStack> stacks) {
        Optional<Player> memory = pilgin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        if (memory.isPresent()) {
            throwItemsTowardPlayer(pilgin, memory.get(), stacks);
        } else {
            throwItemsTowardRandomPos(pilgin, stacks);
        }
    }

    private static void throwItemsTowardRandomPos(Piglin piglin, List<ItemStack> stacks) {
        throwItemsTowardPos(piglin, stacks, getRandomNearbyPos(piglin));
    }

    private static void throwItemsTowardPlayer(Piglin piglin, Player player, List<ItemStack> stacks) {
        throwItemsTowardPos(piglin, stacks, player.position());
    }

    private static void throwItemsTowardPos(Piglin piglin, List<ItemStack> stacks, Vec3 pos) {
        if (!stacks.isEmpty()) {
            piglin.swing(InteractionHand.OFF_HAND);

            for (ItemStack itemStack : stacks) {
                BehaviorUtils.throwItem(piglin, itemStack, pos.add(0.0, 1.0, 0.0));
            }
        }
    }

    private static List<ItemStack> getBarterResponseItems(Piglin piglin) {
        LootTable lootTable = piglin.level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        List<ItemStack> randomItems = lootTable.getRandomItems(
            new LootParams.Builder((ServerLevel)piglin.level()).withParameter(LootContextParams.THIS_ENTITY, piglin).create(LootContextParamSets.PIGLIN_BARTER)
        );
        return randomItems;
    }

    private static boolean wantsToDance(LivingEntity piglin, LivingEntity target) {
        return target.getType() == EntityType.HOGLIN && RandomSource.create(piglin.level().getGameTime()).nextFloat() < 0.1F;
    }

    protected static boolean wantsToPickup(Piglin piglin, ItemStack stack) {
        if (piglin.isBaby() && stack.is(ItemTags.IGNORED_BY_PIGLIN_BABIES)) {
            return false;
        } else if (stack.is(ItemTags.PIGLIN_REPELLENTS)) {
            return false;
        } else if (isAdmiringDisabled(piglin) && piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return false;
        } else if (isBarterCurrency(stack, piglin)) { // CraftBukkit
            return isNotHoldingLovedItemInOffHand(piglin);
        } else {
            boolean canAddToInventory = piglin.canAddToInventory(stack);
            if (stack.is(Items.GOLD_NUGGET)) {
                return canAddToInventory;
            } else if (isFood(stack)) {
                return !hasEatenRecently(piglin) && canAddToInventory;
            } else {
                return !isLovedItem(stack, piglin) ? piglin.canReplaceCurrentItem(stack) : isNotHoldingLovedItemInOffHand(piglin) && canAddToInventory; // Paper - upstream missed isLovedItem check
            }
        }
    }

    // CraftBukkit start - Added method to allow checking for custom payment items
    protected static boolean isLovedItem(ItemStack item, Piglin piglin) {
        return PiglinAi.isLovedItem(item) || (piglin.interestItems.contains(item.getItem()) || piglin.allowedBarterItems.contains(item.getItem()));
    }
    // CraftBukkit end
    protected static boolean isLovedItem(ItemStack item) {
        return item.is(ItemTags.PIGLIN_LOVED);
    }

    private static boolean wantsToStopRiding(Piglin piglin, Entity vehicle) {
        return vehicle instanceof Mob mob
            && (!mob.isBaby() || !mob.isAlive() || wasHurtRecently(piglin) || wasHurtRecently(mob) || mob instanceof Piglin && mob.getVehicle() == null);
    }

    private static boolean isNearestValidAttackTarget(ServerLevel level, Piglin piglin, LivingEntity target) {
        return findNearestValidAttackTarget(level, piglin).filter(livingEntity -> livingEntity == target).isPresent();
    }

    private static boolean isNearZombified(Piglin piglin) {
        Brain<Piglin> brain = piglin.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED)) {
            LivingEntity livingEntity = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED).get();
            return piglin.closerThan(livingEntity, 6.0);
        } else {
            return false;
        }
    }

    private static Optional<? extends LivingEntity> findNearestValidAttackTarget(ServerLevel level, Piglin piglin) {
        Brain<Piglin> brain = piglin.getBrain();
        if (isNearZombified(piglin)) {
            return Optional.empty();
        } else {
            Optional<LivingEntity> livingEntityFromUuidMemory = BehaviorUtils.getLivingEntityFromUUIDMemory(piglin, MemoryModuleType.ANGRY_AT);
            if (livingEntityFromUuidMemory.isPresent() && Sensor.isEntityAttackableIgnoringLineOfSight(level, piglin, livingEntityFromUuidMemory.get())) {
                return livingEntityFromUuidMemory;
            } else {
                if (brain.hasMemoryValue(MemoryModuleType.UNIVERSAL_ANGER)) {
                    Optional<Player> memory = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
                    if (memory.isPresent()) {
                        return memory;
                    }
                }

                Optional<Mob> memory = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
                if (memory.isPresent()) {
                    return memory;
                } else {
                    Optional<Player> memory1 = brain.getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD);
                    return memory1.isPresent() && Sensor.isEntityAttackable(level, piglin, memory1.get()) ? memory1 : Optional.empty();
                }
            }
        }
    }

    public static void angerNearbyPiglins(ServerLevel level, Player player, boolean requireLineOfSight) {
        if (!player.level().paperConfig().entities.behavior.piglinsGuardChests) return; // Paper - Config option for Piglins guarding chests
        List<Piglin> entitiesOfClass = player.level().getEntitiesOfClass(Piglin.class, player.getBoundingBox().inflate(16.0));
        entitiesOfClass.stream().filter(PiglinAi::isIdle).filter(piglin -> !requireLineOfSight || BehaviorUtils.canSee(piglin, player)).forEach(piglin -> {
            if (level.getGameRules().get(GameRules.UNIVERSAL_ANGER)) {
                setAngerTargetToNearestTargetablePlayerIfFound(level, piglin, player);
            } else {
                setAngerTarget(level, piglin, player);
            }
        });
    }

    public static InteractionResult mobInteract(ServerLevel level, Piglin piglin, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (canAdmire(piglin, itemInHand)) {
            ItemStack itemStack = itemInHand.consumeAndReturn(1, player);
            holdInOffhand(level, piglin, itemStack);
            admireGoldItem(piglin);
            stopWalking(piglin);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    protected static boolean canAdmire(Piglin piglin, ItemStack stack) {
        return !isAdmiringDisabled(piglin) && !isAdmiringItem(piglin) && piglin.isAdult() && isBarterCurrency(stack, piglin); // CraftBukkit
    }

    protected static void wasHurtBy(ServerLevel level, Piglin piglin, LivingEntity entity) {
        if (!(entity instanceof Piglin)) {
            if (isHoldingItemInOffHand(piglin)) {
                stopHoldingOffHandItem(level, piglin, false);
            }

            Brain<Piglin> brain = piglin.getBrain();
            brain.eraseMemory(MemoryModuleType.CELEBRATE_LOCATION);
            brain.eraseMemory(MemoryModuleType.DANCING);
            brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
            if (entity instanceof Player) {
                brain.setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true, 400L);
            }

            getAvoidTarget(piglin).ifPresent(avoidTarget -> {
                if (avoidTarget.getType() != entity.getType()) {
                    brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
                }
            });
            if (piglin.isBaby()) {
                brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, entity, 100L);
                if (Sensor.isEntityAttackableIgnoringLineOfSight(level, piglin, entity)) {
                    broadcastAngerTarget(level, piglin, entity);
                }
            } else if (entity.getType() == EntityType.HOGLIN && hoglinsOutnumberPiglins(piglin)) {
                setAvoidTargetAndDontHuntForAWhile(piglin, entity);
                broadcastRetreat(piglin, entity);
            } else {
                maybeRetaliate(level, piglin, entity);
            }
        }
    }

    protected static void maybeRetaliate(ServerLevel level, AbstractPiglin piglin, LivingEntity entity) {
        if (!piglin.getBrain().isActive(Activity.AVOID)) {
            if (Sensor.isEntityAttackableIgnoringLineOfSight(level, piglin, entity)) {
                if (!BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(piglin, entity, 4.0)) {
                    if (entity.getType() == EntityType.PLAYER && level.getGameRules().get(GameRules.UNIVERSAL_ANGER)) {
                        setAngerTargetToNearestTargetablePlayerIfFound(level, piglin, entity);
                        broadcastUniversalAnger(level, piglin);
                    } else {
                        setAngerTarget(level, piglin, entity);
                        broadcastAngerTarget(level, piglin, entity);
                    }
                }
            }
        }
    }

    public static Optional<SoundEvent> getSoundForCurrentActivity(Piglin piglin) {
        return piglin.getBrain().getActiveNonCoreActivity().map(activity -> getSoundForActivity(piglin, activity));
    }

    private static SoundEvent getSoundForActivity(Piglin piglin, Activity activity) {
        if (activity == Activity.FIGHT) {
            return SoundEvents.PIGLIN_ANGRY;
        } else if (piglin.isConverting()) {
            return SoundEvents.PIGLIN_RETREAT;
        } else if (activity == Activity.AVOID && isNearAvoidTarget(piglin)) {
            return SoundEvents.PIGLIN_RETREAT;
        } else if (activity == Activity.ADMIRE_ITEM) {
            return SoundEvents.PIGLIN_ADMIRING_ITEM;
        } else if (activity == Activity.CELEBRATE) {
            return SoundEvents.PIGLIN_CELEBRATE;
        } else if (seesPlayerHoldingLovedItem(piglin)) {
            return SoundEvents.PIGLIN_JEALOUS;
        } else {
            return isNearRepellent(piglin) ? SoundEvents.PIGLIN_RETREAT : SoundEvents.PIGLIN_AMBIENT;
        }
    }

    private static boolean isNearAvoidTarget(Piglin piglin) {
        Brain<Piglin> brain = piglin.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET) && brain.getMemory(MemoryModuleType.AVOID_TARGET).get().closerThan(piglin, 12.0);
    }

    protected static List<AbstractPiglin> getVisibleAdultPiglins(Piglin piglin) {
        return piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS).orElse(ImmutableList.of());
    }

    private static List<AbstractPiglin> getAdultPiglins(AbstractPiglin piglin) {
        return piglin.getBrain().getMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS).orElse(ImmutableList.of());
    }

    public static boolean isWearingSafeArmor(LivingEntity entity) {
        for (EquipmentSlot equipmentSlot : EquipmentSlotGroup.ARMOR) {
            if (entity.getItemBySlot(equipmentSlot).is(ItemTags.PIGLIN_SAFE_ARMOR)) {
                return true;
            }
        }

        return false;
    }

    private static void stopWalking(Piglin piglin) {
        piglin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        piglin.getNavigation().stop();
    }

    private static BehaviorControl<LivingEntity> babySometimesRideBabyHoglin() {
        SetEntityLookTargetSometimes.Ticker ticker = new SetEntityLookTargetSometimes.Ticker(RIDE_START_INTERVAL);
        return CopyMemoryWithExpiry.create(
            entity -> entity.isBaby() && ticker.tickDownAndCheck(entity.level().random),
            MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN,
            MemoryModuleType.RIDE_TARGET,
            RIDE_DURATION
        );
    }

    protected static void broadcastAngerTarget(ServerLevel level, AbstractPiglin piglin, LivingEntity angerTarget) {
        getAdultPiglins(piglin).forEach(abstractPiglin -> {
            if (angerTarget.getType() != EntityType.HOGLIN || abstractPiglin.canHunt() && ((Hoglin)angerTarget).canBeHunted()) {
                setAngerTargetIfCloserThanCurrent(level, abstractPiglin, angerTarget);
            }
        });
    }

    protected static void broadcastUniversalAnger(ServerLevel level, AbstractPiglin piglin) {
        getAdultPiglins(piglin)
            .forEach(abstractPiglin -> getNearestVisibleTargetablePlayer(abstractPiglin).ifPresent(player -> setAngerTarget(level, abstractPiglin, player)));
    }

    protected static void setAngerTarget(ServerLevel level, AbstractPiglin piglin, LivingEntity angerTarget) {
        if (Sensor.isEntityAttackableIgnoringLineOfSight(level, piglin, angerTarget)) {
            piglin.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, angerTarget.getUUID(), 600L);
            if (angerTarget.getType() == EntityType.HOGLIN && piglin.canHunt()) {
                dontKillAnyMoreHoglinsForAWhile(piglin);
            }

            if (angerTarget.getType() == EntityType.PLAYER && level.getGameRules().get(GameRules.UNIVERSAL_ANGER)) {
                piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.UNIVERSAL_ANGER, true, 600L);
            }
        }
    }

    private static void setAngerTargetToNearestTargetablePlayerIfFound(ServerLevel level, AbstractPiglin piglin, LivingEntity entity) {
        Optional<Player> nearestVisibleTargetablePlayer = getNearestVisibleTargetablePlayer(piglin);
        if (nearestVisibleTargetablePlayer.isPresent()) {
            setAngerTarget(level, piglin, nearestVisibleTargetablePlayer.get());
        } else {
            setAngerTarget(level, piglin, entity);
        }
    }

    private static void setAngerTargetIfCloserThanCurrent(ServerLevel level, AbstractPiglin piglin, LivingEntity angerTarget) {
        Optional<LivingEntity> angerTarget1 = getAngerTarget(piglin);
        LivingEntity nearestTarget = BehaviorUtils.getNearestTarget(piglin, angerTarget1, angerTarget);
        if (!angerTarget1.isPresent() || angerTarget1.get() != nearestTarget) {
            setAngerTarget(level, piglin, nearestTarget);
        }
    }

    private static Optional<LivingEntity> getAngerTarget(AbstractPiglin piglin) {
        return BehaviorUtils.getLivingEntityFromUUIDMemory(piglin, MemoryModuleType.ANGRY_AT);
    }

    public static Optional<LivingEntity> getAvoidTarget(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET) ? piglin.getBrain().getMemory(MemoryModuleType.AVOID_TARGET) : Optional.empty();
    }

    public static Optional<Player> getNearestVisibleTargetablePlayer(AbstractPiglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)
            ? piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)
            : Optional.empty();
    }

    private static void broadcastRetreat(Piglin piglin, LivingEntity target) {
        getVisibleAdultPiglins(piglin)
            .stream()
            .filter(abstractPiglin -> abstractPiglin instanceof Piglin)
            .forEach(abstractPiglin -> retreatFromNearestTarget((Piglin)abstractPiglin, target));
    }

    private static void retreatFromNearestTarget(Piglin piglin, LivingEntity target) {
        Brain<Piglin> brain = piglin.getBrain();
        LivingEntity livingEntity = BehaviorUtils.getNearestTarget(piglin, brain.getMemory(MemoryModuleType.AVOID_TARGET), target);
        livingEntity = BehaviorUtils.getNearestTarget(piglin, brain.getMemory(MemoryModuleType.ATTACK_TARGET), livingEntity);
        setAvoidTargetAndDontHuntForAWhile(piglin, livingEntity);
    }

    private static boolean wantsToStopFleeing(Piglin piglin) {
        Brain<Piglin> brain = piglin.getBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET)) {
            return true;
        } else {
            LivingEntity livingEntity = brain.getMemory(MemoryModuleType.AVOID_TARGET).get();
            EntityType<?> type = livingEntity.getType();
            return type == EntityType.HOGLIN
                ? piglinsEqualOrOutnumberHoglins(piglin)
                : isZombified(type) && !brain.isMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, livingEntity);
        }
    }

    private static boolean piglinsEqualOrOutnumberHoglins(Piglin piglin) {
        return !hoglinsOutnumberPiglins(piglin);
    }

    private static boolean hoglinsOutnumberPiglins(Piglin piglin) {
        int i = piglin.getBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT).orElse(0) + 1;
        int i1 = piglin.getBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT).orElse(0);
        return i1 > i;
    }

    private static void setAvoidTargetAndDontHuntForAWhile(Piglin piglin, LivingEntity target) {
        piglin.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        piglin.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        piglin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, RETREAT_DURATION.sample(piglin.level().random));
        dontKillAnyMoreHoglinsForAWhile(piglin);
    }

    protected static void dontKillAnyMoreHoglinsForAWhile(AbstractPiglin piglin) {
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, TIME_BETWEEN_HUNTS.sample(piglin.level().random));
    }

    private static void eat(Piglin piglin) {
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ATE_RECENTLY, true, 200L);
    }

    private static Vec3 getRandomNearbyPos(Piglin piglin) {
        Vec3 pos = LandRandomPos.getPos(piglin, 4, 2);
        return pos == null ? piglin.position() : pos;
    }

    private static boolean hasEatenRecently(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ATE_RECENTLY);
    }

    protected static boolean isIdle(AbstractPiglin piglin) {
        return piglin.getBrain().isActive(Activity.IDLE);
    }

    private static boolean hasCrossbow(LivingEntity piglin) {
        return piglin.isHolding(Items.CROSSBOW);
    }

    private static void admireGoldItem(LivingEntity piglin) {
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_ITEM, true, 119L);
    }

    private static boolean isAdmiringItem(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
    }

    // CraftBukkit start - Changes to allow custom payment for bartering
    private static boolean isBarterCurrency(ItemStack item, Piglin piglin) {
        return PiglinAi.isBarterCurrency(item) || piglin.allowedBarterItems.contains(item.getItem());
    }
    // CraftBukkit end
    private static boolean isBarterCurrency(ItemStack stack) {
        return stack.is(BARTERING_ITEM);
    }

    private static boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.PIGLIN_FOOD);
    }

    private static boolean isNearRepellent(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT);
    }

    private static boolean seesPlayerHoldingLovedItem(LivingEntity piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM);
    }

    private static boolean doesntSeeAnyPlayerHoldingLovedItem(LivingEntity piglin) {
        return !seesPlayerHoldingLovedItem(piglin);
    }

    public static boolean isPlayerHoldingLovedItem(LivingEntity player) {
        return player.getType() == EntityType.PLAYER && player.isHolding(PiglinAi::isLovedItem);
    }

    private static boolean isAdmiringDisabled(Piglin piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED);
    }

    private static boolean wasHurtRecently(LivingEntity piglin) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    private static boolean isHoldingItemInOffHand(Piglin piglin) {
        return !piglin.getOffhandItem().isEmpty();
    }

    private static boolean isNotHoldingLovedItemInOffHand(Piglin piglin) {
        return piglin.getOffhandItem().isEmpty() || !isLovedItem(piglin.getOffhandItem(), piglin); // CraftBukkit - Changes to allow custom payment for bartering
    }

    public static boolean isZombified(EntityType<?> entityType) {
        return entityType == EntityType.ZOMBIFIED_PIGLIN || entityType == EntityType.ZOGLIN;
    }
}
