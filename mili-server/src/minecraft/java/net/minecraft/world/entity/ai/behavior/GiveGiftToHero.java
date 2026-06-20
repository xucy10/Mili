package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class GiveGiftToHero extends Behavior<Villager> {
    private static final int THROW_GIFT_AT_DISTANCE = 5;
    private static final int MIN_TIME_BETWEEN_GIFTS = 600;
    private static final int MAX_TIME_BETWEEN_GIFTS = 6600;
    private static final int TIME_TO_DELAY_FOR_HEAD_TO_FINISH_TURNING = 20;
    private static final Map<ResourceKey<VillagerProfession>, ResourceKey<LootTable>> GIFTS = ImmutableMap.<ResourceKey<VillagerProfession>, ResourceKey<LootTable>>builder()
        .put(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT)
        .put(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT)
        .put(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT)
        .put(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT)
        .put(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT)
        .put(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT)
        .put(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT)
        .put(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT)
        .put(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT)
        .put(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT)
        .put(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT)
        .put(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT)
        .put(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT)
        .build();
    private static final float SPEED_MODIFIER = 0.5F;
    private int timeUntilNextGift = 600;
    private boolean giftGivenDuringThisRun;
    private long timeSinceStart;

    public GiveGiftToHero(int duration) {
        super(
            ImmutableMap.of(
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryStatus.VALUE_PRESENT
            ),
            duration
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (!this.isHeroVisible(owner)) {
            return false;
        } else if (this.timeUntilNextGift > 0) {
            this.timeUntilNextGift--;
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void start(ServerLevel level, Villager entity, long gameTime) {
        this.giftGivenDuringThisRun = false;
        this.timeSinceStart = gameTime;
        Player player = this.getNearestTargetableHero(entity).get();
        entity.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, player);
        BehaviorUtils.lookAtEntity(entity, player);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.isHeroVisible(entity) && !this.giftGivenDuringThisRun;
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Player player = this.getNearestTargetableHero(owner).get();
        BehaviorUtils.lookAtEntity(owner, player);
        if (this.isWithinThrowingDistance(owner, player)) {
            if (gameTime - this.timeSinceStart > 20L) {
                this.throwGift(level, owner, player);
                this.giftGivenDuringThisRun = true;
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(owner, player, 0.5F, 5);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        this.timeUntilNextGift = calculateTimeUntilNextGift(level);
        entity.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void throwGift(ServerLevel level, Villager villager, LivingEntity target) {
        villager.dropFromGiftLootTable(
            level, getLootTableToThrow(villager), (serverLevel, itemStack) -> BehaviorUtils.throwItem(villager, itemStack, target.position())
        );
    }

    private static ResourceKey<LootTable> getLootTableToThrow(Villager villager) {
        if (villager.isBaby()) {
            return BuiltInLootTables.BABY_VILLAGER_GIFT;
        } else {
            Optional<ResourceKey<VillagerProfession>> optional = villager.getVillagerData().profession().unwrapKey();
            return optional.isEmpty() ? BuiltInLootTables.UNEMPLOYED_GIFT : GIFTS.getOrDefault(optional.get(), BuiltInLootTables.UNEMPLOYED_GIFT);
        }
    }

    private boolean isHeroVisible(Villager villager) {
        return this.getNearestTargetableHero(villager).isPresent();
    }

    private Optional<Player> getNearestTargetableHero(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).filter(this::isHero);
    }

    private boolean isHero(Player player) {
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    private boolean isWithinThrowingDistance(Villager villager, Player hero) {
        BlockPos blockPos = hero.blockPosition();
        BlockPos blockPos1 = villager.blockPosition();
        return blockPos1.closerThan(blockPos, 5.0);
    }

    private static int calculateTimeUntilNextGift(ServerLevel level) {
        return 600 + level.random.nextInt(6001);
    }
}
