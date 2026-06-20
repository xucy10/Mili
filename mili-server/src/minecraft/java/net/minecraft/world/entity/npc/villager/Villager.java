package net.minecraft.world.entity.npc.villager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Villager extends AbstractVillager implements ReputationEventHandler, VillagerDataHolder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.VILLAGER_DATA);
    public static final int BREEDING_FOOD_THRESHOLD = 12;
    public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
    private static final int TRADES_PER_LEVEL = 2;
    private static final int MAX_GOSSIP_TOPICS = 10;
    private static final int GOSSIP_COOLDOWN = 1200;
    private static final int GOSSIP_DECAY_INTERVAL = 24000;
    private static final int HOW_FAR_AWAY_TO_TALK_TO_OTHER_VILLAGERS_ABOUT_GOLEMS = 10;
    private static final int HOW_MANY_VILLAGERS_NEED_TO_AGREE_TO_SPAWN_A_GOLEM = 5;
    private static final long TIME_SINCE_SLEEPING_FOR_GOLEM_SPAWNING = 24000L;
    @VisibleForTesting
    public static final float SPEED_MODIFIER = 0.5F;
    private static final int DEFAULT_XP = 0;
    private static final byte DEFAULT_FOOD_LEVEL = 0;
    private static final int DEFAULT_LAST_RESTOCK = 0;
    private static final int DEFAULT_LAST_GOSSIP_DECAY = 0;
    private static final int DEFAULT_RESTOCKS_TODAY = 0;
    private static final boolean DEFAULT_ASSIGN_PROFESSION_WHEN_SPAWNED = false;
    private int updateMerchantTimer;
    private boolean increaseProfessionLevelOnUpdate;
    private @Nullable Player lastTradedPlayer;
    private boolean chasing;
    private int foodLevel = 0;
    private final GossipContainer gossips = new GossipContainer();
    private long lastGossipTime;
    private long lastGossipDecayTime = 0L;
    private int villagerXp = 0;
    private long lastRestockGameTime = 0L;
    public int numberOfRestocksToday = 0;
    private long lastRestockCheckDay;
    private boolean assignProfessionWhenSpawned = false;
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
        MemoryModuleType.HOME,
        MemoryModuleType.JOB_SITE,
        MemoryModuleType.POTENTIAL_JOB_SITE,
        MemoryModuleType.MEETING_POINT,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.VISIBLE_VILLAGER_BABIES,
        MemoryModuleType.NEAREST_PLAYERS,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.INTERACTION_TARGET,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.PATH,
        MemoryModuleType.DOORS_TO_CLOSE,
        MemoryModuleType.NEAREST_BED,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.NEAREST_HOSTILE,
        MemoryModuleType.SECONDARY_JOB_SITE,
        MemoryModuleType.HIDING_PLACE,
        MemoryModuleType.HEARD_BELL_TIME,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.LAST_SLEPT,
        MemoryModuleType.LAST_WOKEN,
        MemoryModuleType.LAST_WORKED_AT_POI,
        MemoryModuleType.GOLEM_DETECTED_RECENTLY
    );
    private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.NEAREST_ITEMS,
        SensorType.NEAREST_BED,
        SensorType.HURT_BY,
        SensorType.VILLAGER_HOSTILES,
        SensorType.VILLAGER_BABIES,
        SensorType.SECONDARY_POIS,
        SensorType.GOLEM_DETECTED
    );
    public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<Villager, Holder<PoiType>>> POI_MEMORIES = ImmutableMap.of(
        MemoryModuleType.HOME,
        (villager, poiType) -> poiType.is(PoiTypes.HOME),
        MemoryModuleType.JOB_SITE,
        (villager, poiType) -> villager.getVillagerData().profession().value().heldJobSite().test(poiType),
        MemoryModuleType.POTENTIAL_JOB_SITE,
        (villager, poiType) -> VillagerProfession.ALL_ACQUIRABLE_JOBS.test(poiType),
        MemoryModuleType.MEETING_POINT,
        (villager, poiType) -> poiType.is(PoiTypes.MEETING)
    );

    public Villager(EntityType<? extends Villager> type, Level level) {
        this(type, level, VillagerType.PLAINS);
    }

    public Villager(EntityType<? extends Villager> type, Level level, ResourceKey<VillagerType> villagerType) {
        this(type, level, level.registryAccess().getOrThrow(villagerType));
    }

    public Villager(EntityType<? extends Villager> type, Level level, Holder<VillagerType> villagerType) {
        super(type, level);
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
        this.getNavigation().setRequiredPathLength(48.0F);
        this.setCanPickUpLoot(true);
        this.setVillagerData(this.getVillagerData().withType(villagerType).withProfession(level.registryAccess(), VillagerProfession.NONE));
    }

    // Purpur start
    private boolean isLobotomized = false; public boolean isLobotomized() { return this.isLobotomized; } // Purpur
    private int notLobotomizedCount = 0; // Purpur

    private boolean checkLobotomized() {
        int interval = me.earthme.luminol.config.modules.optimizations.LobotomizeVillageConfig.villagerLobotomizeCheckInterval;
        boolean shouldCheckForTradeLocked = me.earthme.luminol.config.modules.optimizations.LobotomizeVillageConfig.villagerLobotomizeWaitUntilTradeLocked;
        if (this.notLobotomizedCount > 3) {
            // check half as often if not lobotomized for the last 3+ consecutive checks
            interval *= 2;
        }
        if (this.level().getGameTime() % interval == 0) {
            // offset Y for short blocks like dirt_path/farmland
            this.isLobotomized = !(shouldCheckForTradeLocked && this.getVillagerXp() == 0) && !canTravelFrom(BlockPos.containing(this.position().x, this.getBoundingBox().minY + 0.0625D, this.position().z));

            if (this.isLobotomized) {
                this.notLobotomizedCount = 0;
            } else {
                this.notLobotomizedCount++;
            }
        }
        return this.isLobotomized;
    }
    // Purpur end

    private boolean canTravelFrom(BlockPos pos) {
        return canTravelTo(pos.east()) || canTravelTo(pos.west()) || canTravelTo(pos.north()) || canTravelTo(pos.south());
    }

    private boolean canTravelTo(BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = this.level().getBlockStateIfLoaded(pos);
        if (state == null) {
            // chunk not loaded
            return false;
        }
        net.minecraft.world.level.block.Block bottom = state.getBlock();
        if (bottom instanceof net.minecraft.world.level.block.FenceBlock ||
                bottom instanceof net.minecraft.world.level.block.FenceGateBlock ||
                bottom instanceof net.minecraft.world.level.block.WallBlock) {
            // bottom block is too tall to get over
            return false;
        }
        net.minecraft.world.level.block.Block top = level().getBlockState(pos.above()).getBlock();
        // only if both blocks have no collision
        return !bottom.hasCollision && !top.hasCollision;
    }

    @Override
    public Brain<Villager> getBrain() {
        return (Brain<Villager>)super.getBrain();
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Villager> brain = this.brainProvider().makeBrain(dynamic);
        this.registerBrainGoals(brain);
        return brain;
    }

    public void refreshBrain(ServerLevel level) {
        Brain<Villager> brain = this.getBrain();
        brain.stopAll(level, this);
        this.brain = brain.copyWithoutBehaviors();
        this.registerBrainGoals(this.getBrain());
    }

    private void registerBrainGoals(Brain<Villager> villagerBrain) {
        Holder<VillagerProfession> holder = this.getVillagerData().profession();
        if (this.isBaby()) {
            villagerBrain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
            villagerBrain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
        } else {
            villagerBrain.setSchedule(EnvironmentAttributes.VILLAGER_ACTIVITY);
            villagerBrain.addActivityWithConditions(
                Activity.WORK,
                VillagerGoalPackages.getWorkPackage(holder, 0.5F),
                ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT))
            );
        }

        villagerBrain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(holder, 0.5F));
        villagerBrain.addActivityWithConditions(
            Activity.MEET,
            VillagerGoalPackages.getMeetPackage(holder, 0.5F),
            ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT))
        );
        villagerBrain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(holder, 0.5F));
        villagerBrain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(holder, 0.5F));
        villagerBrain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(holder, 0.5F));
        villagerBrain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(holder, 0.5F));
        villagerBrain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(holder, 0.5F));
        villagerBrain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(holder, 0.5F));
        villagerBrain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        villagerBrain.setDefaultActivity(Activity.IDLE);
        villagerBrain.setActiveActivityIfPossible(Activity.IDLE);
        villagerBrain.updateActivityFromSchedule(this.level().environmentAttributes(), this.level().getLevelData().getGameTime(), this.position()); // Folia - region threading - not in the world yet
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (this.level() instanceof ServerLevel) {
            this.refreshBrain((ServerLevel)this.level());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    public boolean assignProfessionWhenSpawned() {
        return this.assignProfessionWhenSpawned;
    }

    // Paper start - EAR 2
    @Override
    public void inactiveTick() {
        // SPIGOT-3874, SPIGOT-3894, SPIGOT-3846, SPIGOT-5286 :(
        if (this.getUnhappyCounter() > 0) {
            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }
        if (this.isEffectiveAi()) {
            if (this.level().spigotConfig.tickInactiveVillagers) {
                this.customServerAiStep(this.level().getMinecraftWorld());
            } else {
                this.customServerAiStep(this.level().getMinecraftWorld(), true);
            }
        }
        this.maybeDecayGossip();
        super.inactiveTick();
    }
    // Paper end - EAR 2

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Paper start - EAR 2
        this.customServerAiStep(level, false);
    }
    protected void customServerAiStep(ServerLevel level, boolean inactive) { // Purpur - not final
        // Paper end - EAR 2
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("villagerBrain");
        // Purpur start
        if (me.earthme.luminol.config.modules.optimizations.LobotomizeVillageConfig.villagerLobotomizeEnabled) {
            // treat as inactive if lobotomized
            inactive = inactive || checkLobotomized();
        } else {
            this.isLobotomized = false;
        }
        // Purpur end
        if (!inactive) this.getBrain().tick(level, this); // Paper - EAR 2
        else if (this.isLobotomized && shouldRestock(level)) restock(); // Purpur - Lobotomize stuck villagers
        profilerFiller.pop();
        if (this.assignProfessionWhenSpawned) {
            this.assignProfessionWhenSpawned = false;
        }

        if (!this.isTrading() && this.updateMerchantTimer > 0) {
            this.updateMerchantTimer--;
            if (this.updateMerchantTimer <= 0) {
                if (this.increaseProfessionLevelOnUpdate) {
                    this.increaseMerchantCareer(level);
                    this.increaseProfessionLevelOnUpdate = false;
                }

                this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.VILLAGER_TRADE); // CraftBukkit
            }
        }

        if (this.lastTradedPlayer != null) {
            level.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
            level.broadcastEntityEvent(this, EntityEvent.VILLAGER_HAPPY);
            this.lastTradedPlayer = null;
        }

        if (!inactive && !this.isNoAi() && this.random.nextInt(100) == 0) { // Paper - EAR 2
            Raid raidAt = level.getRaidAt(this.blockPosition());
            if (raidAt != null && raidAt.isActive() && !raidAt.isOver()) {
                level.broadcastEntityEvent(this, EntityEvent.VILLAGER_SWEAT);
            }
        }

        if (this.getVillagerData().profession().is(VillagerProfession.NONE) && this.isTrading()) {
            this.stopTrading();
        }

        if (inactive) return; // Paper - EAR 2
        super.customServerAiStep(level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getUnhappyCounter() > 0) {
            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        this.maybeDecayGossip();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.isTrading() || this.isSleeping()) {
            return super.mobInteract(player, hand);
        } else if (this.isBaby()) {
            this.setUnhappy();
            return InteractionResult.SUCCESS;
        } else {
            if (!this.level().isClientSide()) {
                boolean isEmpty = this.getOffers().isEmpty();
                if (hand == InteractionHand.MAIN_HAND) {
                    if (isEmpty) {
                        this.setUnhappy();
                    }

                    player.awardStat(Stats.TALKED_TO_VILLAGER);
                }

                if (isEmpty) {
                    return InteractionResult.CONSUME;
                }

                this.startTrading(player);
            }

            return InteractionResult.SUCCESS;
        }
    }

    public void setUnhappy() {
        this.setUnhappyCounter(40);
        if (!this.level().isClientSide()) {
            this.makeSound(SoundEvents.VILLAGER_NO);
        }
    }

    private void startTrading(Player player) {
        this.updateSpecialPrices(player);
        this.setTradingPlayer(player);
        this.openTradingScreen(player, this.getDisplayName(), this.getVillagerData().level());
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        boolean flag = this.getTradingPlayer() != null && player == null;
        super.setTradingPlayer(player);
        if (flag) {
            this.stopTrading();
        }
    }

    @Override
    protected void stopTrading() {
        super.stopTrading();
        this.resetSpecialPrices();
    }

    private void resetSpecialPrices() {
        if (!this.level().isClientSide()) {
            for (MerchantOffer merchantOffer : this.getOffers()) {
                merchantOffer.resetSpecialPriceDiff();
            }
        }
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    public void restock() {
        this.updateDemand();

        for (MerchantOffer merchantOffer : this.getOffers()) {
            // CraftBukkit start
            org.bukkit.event.entity.VillagerReplenishTradeEvent event = new org.bukkit.event.entity.VillagerReplenishTradeEvent((org.bukkit.entity.Villager) this.getBukkitEntity(), merchantOffer.asBukkit());
            if (event.callEvent()) {
                merchantOffer.resetUses();
            }
            // CraftBukkit end
        }

        this.resendOffersToTradingPlayer();
        this.lastRestockGameTime = this.level().getGameTime();
        this.numberOfRestocksToday++;
    }

    private void resendOffersToTradingPlayer() {
        MerchantOffers offers = this.getOffers();
        Player tradingPlayer = this.getTradingPlayer();
        if (tradingPlayer != null && !offers.isEmpty()) {
            tradingPlayer.sendMerchantOffers(
                tradingPlayer.containerMenu.containerId,
                offers,
                this.getVillagerData().level(),
                this.getVillagerXp(),
                this.showProgressBar(),
                this.canRestock()
            );
        }
    }

    private boolean needsToRestock() {
        for (MerchantOffer merchantOffer : this.getOffers()) {
            if (merchantOffer.needsRestock()) {
                return true;
            }
        }

        return false;
    }

    private boolean allowedToRestock() {
        return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level().getGameTime() > this.lastRestockGameTime + 2400L;
    }

    public boolean shouldRestock(ServerLevel level) {
        long l = this.lastRestockGameTime + 12000L;
        long gameTime = this.level().getGameTime();
        boolean flag = gameTime > l;
        long dayCount = level.getDayCount();
        flag |= this.lastRestockCheckDay > 0L && dayCount > this.lastRestockCheckDay;
        this.lastRestockCheckDay = dayCount;
        if (flag) {
            this.lastRestockGameTime = gameTime;
            this.resetNumberOfRestocks();
        }

        return this.allowedToRestock() && this.needsToRestock();
    }

    private void catchUpDemand() {
        int i = 2 - this.numberOfRestocksToday;
        if (i > 0) {
            for (MerchantOffer merchantOffer : this.getOffers()) {
                // CraftBukkit start
                org.bukkit.event.entity.VillagerReplenishTradeEvent event = new org.bukkit.event.entity.VillagerReplenishTradeEvent((org.bukkit.entity.Villager) this.getBukkitEntity(), merchantOffer.asBukkit());
                if (event.callEvent()) {
                    merchantOffer.resetUses();
                }
                // CraftBukkit end
            }
        }

        for (int i1 = 0; i1 < i; i1++) {
            this.updateDemand();
        }

        this.resendOffersToTradingPlayer();
    }

    public void updateDemand() {
        for (MerchantOffer merchantOffer : this.getOffers()) {
            merchantOffer.updateDemand();
        }
    }

    private void updateSpecialPrices(Player player) {
        int playerReputation = this.getPlayerReputation(player);
        if (playerReputation != 0) {
            for (MerchantOffer merchantOffer : this.getOffers()) {
                if (merchantOffer.ignoreDiscounts) continue; // Paper - Add ignore discounts API
                merchantOffer.addToSpecialPriceDiff(-Mth.floor(playerReputation * merchantOffer.getPriceMultiplier()));
            }
        }

        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
            int amplifier = effect.getAmplifier();

            for (MerchantOffer merchantOffer1 : this.getOffers()) {
                if (merchantOffer1.ignoreDiscounts) continue; // Paper - Add ignore discounts API
                double d = 0.3 + 0.0625 * amplifier;
                int i = (int)Math.floor(d * merchantOffer1.getBaseCostA().getCount());
                merchantOffer1.addToSpecialPriceDiff(-Math.max(i, 1));
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VILLAGER_DATA, createDefaultVillagerData());
    }

    public static VillagerData createDefaultVillagerData() {
        return new VillagerData(
            BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), 1
        );
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
        output.putByte("FoodLevel", (byte)this.foodLevel);
        output.store("Gossips", GossipContainer.CODEC, this.gossips);
        output.putInt("Xp", this.villagerXp);
        output.putLong("LastRestock", this.lastRestockGameTime);
        output.putLong("LastGossipDecay", this.lastGossipDecayTime);
        output.putInt("RestocksToday", this.numberOfRestocksToday);
        if (this.assignProfessionWhenSpawned) {
            output.putBoolean("AssignProfessionWhenSpawned", true);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_VILLAGER_DATA, input.read("VillagerData", VillagerData.CODEC).orElseGet(Villager::createDefaultVillagerData));
        this.foodLevel = input.getByteOr("FoodLevel", (byte)0);
        this.gossips.clear();
        input.read("Gossips", GossipContainer.CODEC).ifPresent(this.gossips::putAll);
        this.villagerXp = input.getIntOr("Xp", 0);
        this.lastRestockGameTime = input.getLongOr("LastRestock", 0L);
        this.lastGossipDecayTime = input.getLongOr("LastGossipDecay", 0L);
        if (this.level() instanceof ServerLevel) {
            this.refreshBrain((ServerLevel)this.level());
        }

        this.numberOfRestocksToday = input.getIntOr("RestocksToday", 0);
        this.assignProfessionWhenSpawned = input.getBooleanOr("AssignProfessionWhenSpawned", false);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return null;
        } else {
            return this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
        }
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    public void playWorkSound() {
        this.makeSound(this.getVillagerData().profession().value().workSound());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        VillagerData villagerData = this.getVillagerData();
        if (!villagerData.profession().equals(data.profession())) {
            this.offers = null;
        }

        this.entityData.set(DATA_VILLAGER_DATA, data);
    }

    @Override
    public VillagerData getVillagerData() {
        return this.entityData.get(DATA_VILLAGER_DATA);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        int i = 3 + this.random.nextInt(4);
        this.villagerXp = this.villagerXp + offer.getXp();
        this.lastTradedPlayer = this.getTradingPlayer();
        if (this.shouldIncreaseLevel()) {
            this.updateMerchantTimer = 40;
            this.increaseProfessionLevelOnUpdate = true;
            i += 5;
        }

        if (offer.shouldRewardExp()) {
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), i, org.bukkit.entity.ExperienceOrb.SpawnReason.VILLAGER_TRADE, this.getTradingPlayer(), this)); // Paper
        }
    }

    @Override
    public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
        if (livingEntity != null && this.level() instanceof ServerLevel) {
            ((ServerLevel)this.level()).onReputationEvent(ReputationEventType.VILLAGER_HURT, livingEntity, this);
            if (this.isAlive() && livingEntity instanceof Player) {
                this.level().broadcastEntityEvent(this, EntityEvent.VILLAGER_ANGRY);
            }
        }

        super.setLastHurtByMob(livingEntity);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (org.spigotmc.SpigotConfig.logVillagerDeaths) LOGGER.info("Villager {} died, message: '{}'", this, damageSource.getLocalizedDeathMessage(this).getString()); // Spigot
        Entity entity = damageSource.getEntity();
        if (entity != null) {
            this.tellWitnessesThatIWasMurdered(entity);
        }

        this.releaseAllPois();
        super.die(damageSource);
    }

    public void releaseAllPois() {
        this.releasePoi(MemoryModuleType.HOME);
        this.releasePoi(MemoryModuleType.JOB_SITE);
        this.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        this.releasePoi(MemoryModuleType.MEETING_POINT);
    }

    private void tellWitnessesThatIWasMurdered(Entity murderer) {
        if (this.level() instanceof ServerLevel serverLevel) {
            Optional<NearestVisibleLivingEntities> memory = this.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            if (!memory.isEmpty()) {
                memory.get()
                    .findAll(ReputationEventHandler.class::isInstance)
                    .forEach(entity -> serverLevel.onReputationEvent(ReputationEventType.VILLAGER_KILLED, murderer, (ReputationEventHandler)entity));
            }
        }
    }

    public void releasePoi(MemoryModuleType<GlobalPos> moduleType) {
        if (this.level() instanceof ServerLevel) {
            MinecraftServer server = ((ServerLevel)this.level()).getServer();
            this.brain.getMemory(moduleType).ifPresent(pos -> {
                ServerLevel level = server.getLevel(pos.dimension());
                if (level != null) {
                    level.regionizedBlockableEventLoop.asExecutor(pos.pos(), false, true).execute(() -> { // Luminol - Fix possible delayed POI updates while block updating
                    /*io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue( // Folia - region threading // Luminol - Fix possible delayed POI updates while block updating
                            level, pos.pos().getX() >> 4, pos.pos().getZ() >> 4, () -> { // Folia - region threading*/ // Luminol - Fix possible delayed POI updates while block updating
                    PoiManager poiManager = level.getPoiManager();
                    Optional<Holder<PoiType>> type = poiManager.getType(pos.pos());
                    BiPredicate<Villager, Holder<PoiType>> biPredicate = POI_MEMORIES.get(moduleType);
                    if (type.isPresent() && biPredicate.test(this, type.get())) {
                        poiManager.release(pos.pos());
                        level.debugSynchronizers().updatePoi(pos.pos());
                    }
                    // }); // Folia - region threading // Luminol - Fix possible delayed POI updates while block updating
                    }); // Luminol - Fix possible delayed POI updates while block updating
                }
            });
        }
    }

    @Override
    public boolean canBreed() {
        return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
    }

    private boolean hungry() {
        return this.foodLevel < 12;
    }

    private void eatUntilFull() {
        if (this.hungry() && this.countFoodPointsInInventory() != 0) {
            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack item = this.getInventory().getItem(i);
                if (!item.isEmpty()) {
                    Integer integer = FOOD_POINTS.get(item.getItem());
                    if (integer != null) {
                        int count = item.getCount();

                        for (int i1 = count; i1 > 0; i1--) {
                            this.foodLevel = this.foodLevel + integer;
                            this.getInventory().removeItem(i, 1);
                            if (!this.hungry()) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public int getPlayerReputation(Player player) {
        return this.gossips.getReputation(player.getUUID(), type -> true);
    }

    private void digestFood(int level) {
        this.foodLevel -= level;
    }

    public void eatAndDigestFood() {
        this.eatUntilFull();
        this.digestFood(12);
    }

    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    private boolean shouldIncreaseLevel() {
        int level = this.getVillagerData().level();
        return VillagerData.canLevelUp(level) && this.villagerXp >= VillagerData.getMaxXpPerLevel(level);
    }

    public void increaseMerchantCareer(ServerLevel level) { // Paper - public
        this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
        this.updateTrades(level);
    }

    @Override
    protected Component getTypeName() {
        return this.getVillagerData().profession().value().name();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.LOVE_HEARTS) {
            this.addParticlesAroundSelf(ParticleTypes.HEART);
        } else if (id == EntityEvent.VILLAGER_ANGRY) {
            this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
        } else if (id == EntityEvent.VILLAGER_HAPPY) {
            this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
        } else if (id == EntityEvent.VILLAGER_SWEAT) {
            this.addParticlesAroundSelf(ParticleTypes.SPLASH);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnReason == EntitySpawnReason.BREEDING) {
            this.setVillagerData(this.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
        }

        if (spawnReason == EntitySpawnReason.COMMAND
            || spawnReason == EntitySpawnReason.SPAWN_ITEM_USE
            || EntitySpawnReason.isSpawner(spawnReason)
            || spawnReason == EntitySpawnReason.DISPENSER) {
            this.setVillagerData(this.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(this.blockPosition()))));
        }

        if (spawnReason == EntitySpawnReason.STRUCTURE) {
            this.assignProfessionWhenSpawned = true;
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public @Nullable Villager getBreedOffspring(ServerLevel level, AgeableMob partner) {
        double randomDouble = this.random.nextDouble();
        Holder<VillagerType> orThrow;
        if (randomDouble < 0.5) {
            orThrow = level.registryAccess().getOrThrow(VillagerType.byBiome(level.getBiome(this.blockPosition())));
        } else if (randomDouble < 0.75) {
            orThrow = this.getVillagerData().type();
        } else {
            orThrow = ((Villager)partner).getVillagerData().type();
        }

        Villager villager = new Villager(EntityType.VILLAGER, level, orThrow);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.BREEDING, null);
        return villager;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            // Paper - Add EntityZapEvent; move log down, event can cancel
            Witch witch = this.convertTo(EntityType.WITCH, ConversionParams.single(this, false, false), witch1 -> {
                // Paper start - Add EntityZapEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityZapEvent(this, lightning, witch1).isCancelled()) {
                    return false;
                }
                if (org.spigotmc.SpigotConfig.logVillagerDeaths) LOGGER.info("Villager {} was struck by lightning {}.", this, lightning); // Move down
                // Paper end - Add EntityZapEvent
                witch1.finalizeSpawn(level, level.getCurrentDifficultyAt(witch1.blockPosition()), EntitySpawnReason.CONVERSION, null);
                witch1.setPersistenceRequired();
                this.releaseAllPois();
                return true; // Paper start - Add EntityZapEvent
            }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.LIGHTNING, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
            if (witch == null) {
                super.thunderHit(level, lightning);
            }
        } else {
            super.thunderHit(level, lightning);
        }
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        InventoryCarrier.pickUpItem(level, this, this, entity);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        Item item = stack.getItem();
        return (stack.is(ItemTags.VILLAGER_PICKS_UP) || this.getVillagerData().profession().value().requestedItems().contains(item))
            && this.getInventory().canAddItem(stack);
    }

    public boolean hasExcessFood() {
        return this.countFoodPointsInInventory() >= 24;
    }

    public boolean wantsMoreFood() {
        return this.countFoodPointsInInventory() < 12;
    }

    private int countFoodPointsInInventory() {
        SimpleContainer inventory = this.getInventory();
        return FOOD_POINTS.entrySet().stream().mapToInt(entry -> inventory.countItem(entry.getKey()) * entry.getValue()).sum();
    }

    public boolean hasFarmSeeds() {
        return this.getInventory().hasAnyMatching(stack -> stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS));
    }

    @Override
    protected void updateTrades(ServerLevel level) {
        // Paper start - More vanilla friendly methods to update trades
        this.updateTrades(TRADES_PER_LEVEL);
    }

    public boolean updateTrades(int amount) {
        // Paper end - More vanilla friendly methods to update trades
        VillagerData villagerData = this.getVillagerData();
        ResourceKey<VillagerProfession> resourceKey = villagerData.profession().unwrapKey().orElse(null);
        if (resourceKey != null) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> map1;
            if (this.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
                Int2ObjectMap<VillagerTrades.ItemListing[]> map = VillagerTrades.EXPERIMENTAL_TRADES.get(resourceKey);
                map1 = map != null ? map : VillagerTrades.TRADES.get(resourceKey);
            } else {
                map1 = VillagerTrades.TRADES.get(resourceKey);
            }

            if (map1 != null && !map1.isEmpty()) {
                VillagerTrades.ItemListing[] itemListings = map1.get(villagerData.level());
                if (itemListings != null) {
                    MerchantOffers offers = this.getOffers();
                    this.addOffersFromItemListings((net.minecraft.server.level.ServerLevel) this.level(), offers, itemListings, amount); // Paper - More vanilla friendly methods to update trades
                    if (SharedConstants.DEBUG_UNLOCK_ALL_TRADES && villagerData.level() < map1.size()) {
                        this.increaseMerchantCareer((net.minecraft.server.level.ServerLevel) this.level());
                    }
                    return true; // Paper - More vanilla friendly methods to update trades
                }
            }
        }
        return false; // Paper - More vanilla friendly methods to update trades
    }

    public void gossip(ServerLevel level, Villager target, long gameTime) {
        if ((gameTime < this.lastGossipTime || gameTime >= this.lastGossipTime + 1200L)
            && (gameTime < target.lastGossipTime || gameTime >= target.lastGossipTime + 1200L)) {
            this.gossips.transferFrom(target.gossips, this.random, 10);
            this.lastGossipTime = gameTime;
            target.lastGossipTime = gameTime;
            this.spawnGolemIfNeeded(level, gameTime, 5);
        }
    }

    private void maybeDecayGossip() {
        long gameTime = this.level().getGameTime();
        if (this.lastGossipDecayTime == 0L) {
            this.lastGossipDecayTime = gameTime;
        } else if (gameTime >= this.lastGossipDecayTime + 24000L) {
            this.gossips.decay();
            this.lastGossipDecayTime = gameTime;
        }
    }

    public void spawnGolemIfNeeded(ServerLevel level, long gameTime, int minVillagerAmount) {
        if (this.wantsToSpawnGolem(gameTime)) {
            AABB aabb = this.getBoundingBox().inflate(10.0, 10.0, 10.0);
            List<Villager> entitiesOfClass = level.getEntitiesOfClass(Villager.class, aabb);
            List<Villager> list = entitiesOfClass.stream().filter(villager -> villager.wantsToSpawnGolem(gameTime)).limit(5L).toList();
            if (list.size() >= minVillagerAmount) {
                if (SpawnUtil.trySpawnMob( // Paper - Set Golem Last Seen to stop it from spawning another one - switch to isPresent
                        EntityType.IRON_GOLEM,
                        EntitySpawnReason.MOB_SUMMONED,
                        level,
                        this.blockPosition(),
                        10,
                        8,
                        6,
                        SpawnUtil.Strategy.LEGACY_IRON_GOLEM,
                        false,
                        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE, // CraftBukkit,
                        () -> {GolemSensor.golemDetected(this);} // Paper - Set Golem Last Seen to stop it from spawning another one
                    )
                    .isPresent()) { // Paper - Set Golem Last Seen to stop it from spawning another one - switch to isPresent
                    entitiesOfClass.forEach(GolemSensor::golemDetected);
                }
            }
        }
    }

    public boolean wantsToSpawnGolem(long gameTime) {
        return this.golemSpawnConditionsMet(this.level().getGameTime()) && !this.brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
    }

    @Override
    public void onReputationEventFrom(ReputationEventType type, Entity target) {
        if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
            this.gossips.add(target.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            this.gossips.add(target.getUUID(), GossipType.MINOR_POSITIVE, 25);
        } else if (type == ReputationEventType.TRADE) {
            this.gossips.add(target.getUUID(), GossipType.TRADING, 2);
        } else if (type == ReputationEventType.VILLAGER_HURT) {
            this.gossips.add(target.getUUID(), GossipType.MINOR_NEGATIVE, 25);
        } else if (type == ReputationEventType.VILLAGER_KILLED) {
            this.gossips.add(target.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
        }
    }

    @Override
    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int villagerXp) {
        this.villagerXp = villagerXp;
    }

    private void resetNumberOfRestocks() {
        this.catchUpDemand();
        this.numberOfRestocksToday = 0;
    }

    public GossipContainer getGossips() {
        return this.gossips;
    }

    public void setGossips(GossipContainer gossips) {
        this.gossips.putAll(gossips);
    }

    @Override
    public void startSleeping(BlockPos pos) {
        super.startSleeping(pos);
        this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level().getGameTime());
        this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level().getGameTime());
    }

    private boolean golemSpawnConditionsMet(long gameTime) {
        Optional<Long> memory = this.brain.getMemory(MemoryModuleType.LAST_SLEPT);
        return memory.filter(lastSlept -> gameTime - lastSlept < 24000L).isPresent();
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.VILLAGER_VARIANT
            ? castComponentValue((DataComponentType<T>)component, this.getVillagerData().type())
            : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.VILLAGER_VARIANT);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.VILLAGER_VARIANT) {
            Holder<VillagerType> holder = castComponentValue(DataComponents.VILLAGER_VARIANT, value);
            this.setVillagerData(this.getVillagerData().withType(holder));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }
}
