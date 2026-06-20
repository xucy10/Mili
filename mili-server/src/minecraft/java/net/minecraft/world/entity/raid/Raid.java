package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Raid {
    // Paper start
    private static final String PDC_NBT_KEY = "BukkitValues";
    private static final org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry PDC_TYPE_REGISTRY = new org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry();
    public final org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer persistentDataContainer;
    public int idOrNegativeOne = -1;
    // Paper end
    public static final SpawnPlacementType RAVAGER_SPAWN_PLACEMENT_TYPE = SpawnPlacements.getPlacementType(EntityType.RAVAGER);
    public static final MapCodec<Raid> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.BOOL.fieldOf("started").forGetter(raid -> raid.started),
                Codec.BOOL.fieldOf("active").forGetter(raid -> raid.active),
                Codec.LONG.fieldOf("ticks_active").forGetter(raid -> raid.ticksActive),
                Codec.INT.fieldOf("raid_omen_level").forGetter(raid -> raid.raidOmenLevel),
                Codec.INT.fieldOf("groups_spawned").forGetter(raid -> raid.groupsSpawned),
                Codec.INT.fieldOf("cooldown_ticks").forGetter(raid -> raid.raidCooldownTicks),
                Codec.INT.fieldOf("post_raid_ticks").forGetter(raid -> raid.postRaidTicks),
                Codec.FLOAT.fieldOf("total_health").forGetter(raid -> raid.totalHealth),
                Codec.INT.fieldOf("group_count").forGetter(raid -> raid.numGroups),
                Raid.RaidStatus.CODEC.fieldOf("status").forGetter(raid -> raid.status),
                BlockPos.CODEC.fieldOf("center").forGetter(raid -> raid.center),
                UUIDUtil.CODEC_SET.fieldOf("heroes_of_the_village").forGetter(raid -> raid.heroesOfTheVillage)
                , org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer.createCodec(PDC_TYPE_REGISTRY).lenientOptionalFieldOf(PDC_NBT_KEY) // Paper - add persistent data container
                    .forGetter(raid -> raid.persistentDataContainer.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(raid.persistentDataContainer)) // Paper - add persistent data container
            )
            .apply(instance, Raid::new)
    );
    private static final int ALLOW_SPAWNING_WITHIN_VILLAGE_SECONDS_THRESHOLD = 7;
    private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
    private static final int VILLAGE_SEARCH_RADIUS = 32;
    private static final int RAID_TIMEOUT_TICKS = 48000;
    private static final int NUM_SPAWN_ATTEMPTS = 5;
    private static final Component OMINOUS_BANNER_PATTERN_NAME = Component.translatable("block.minecraft.ominous_banner");
    private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
    public static final int VILLAGE_RADIUS_BUFFER = 16;
    private static final int POST_RAID_TICK_LIMIT = 40;
    private static final int DEFAULT_PRE_RAID_TICKS = 300;
    public static final int MAX_NO_ACTION_TIME = 2400;
    public static final int MAX_CELEBRATION_TICKS = 600;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    public static final int DEFAULT_MAX_RAID_OMEN_LEVEL = 5;
    private static final int LOW_MOB_THRESHOLD = 2;
    private static final Component RAID_NAME_COMPONENT = Component.translatable("event.minecraft.raid");
    private static final Component RAID_BAR_VICTORY_COMPONENT = Component.translatable("event.minecraft.raid.victory.full");
    private static final Component RAID_BAR_DEFEAT_COMPONENT = Component.translatable("event.minecraft.raid.defeat.full");
    private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
    private static final int VALID_RAID_RADIUS = 96;
    public static final int VALID_RAID_RADIUS_SQR = 9216;
    public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
    private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
    public final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    public long ticksActive;
    private BlockPos center;
    private boolean started;
    public float totalHealth;
    public int raidOmenLevel;
    private boolean active;
    private int groupsSpawned;
    public final ServerBossEvent raidEvent = new ServerBossEvent(RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final RandomSource random = RandomSource.create();
    public int numGroups;
    private Raid.RaidStatus status;
    private int celebrationTicks;
    private Optional<BlockPos> waveSpawnPos = Optional.empty();

    // Folia start - make raids thread-safe
    public boolean ownsRaid(ServerLevel world) {
        BlockPos center = this.getCenter();
        return center != null && ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(world, center.getX() >> 4, center.getZ() >> 4, 8);
    }
    // Folia end - make raids thread-safe

    public Raid(BlockPos center, Difficulty difficulty) {
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setProgress(0.0F);
        this.center = center;
        this.numGroups = this.getNumGroups(difficulty);
        this.status = Raid.RaidStatus.ONGOING;
        this.persistentDataContainer = new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(PDC_TYPE_REGISTRY); // Paper - add persistent data container
    }

    private Raid(
        boolean started,
        boolean active,
        long ticksActive,
        int raidOmenLevel,
        int groupsSpawned,
        int raidCooldownTicks,
        int postRaidTicks,
        float totalHealth,
        int numGroups,
        Raid.RaidStatus status,
        BlockPos center,
        Set<UUID> heroesOfTheVillage
        , final Optional<org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer> persistentDataContainer // Paper - add persistent data container
    ) {
        this.started = started;
        this.active = active;
        this.ticksActive = ticksActive;
        this.raidOmenLevel = raidOmenLevel;
        this.groupsSpawned = groupsSpawned;
        this.raidCooldownTicks = raidCooldownTicks;
        this.postRaidTicks = postRaidTicks;
        this.totalHealth = totalHealth;
        this.center = center;
        this.numGroups = numGroups;
        this.status = status;
        this.heroesOfTheVillage.addAll(heroesOfTheVillage);
        this.persistentDataContainer = persistentDataContainer.orElseGet(() -> new org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer(PDC_TYPE_REGISTRY)); // Paper - add persistent data container
    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.RaidStatus.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.RaidStatus.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.RaidStatus.LOSS;
    }

    // CraftBukkit start
    public boolean isInProgress() {
        return this.status == RaidStatus.ONGOING;
    }
    // CraftBukkit end

    public float getTotalHealth() {
        return this.totalHealth;
    }

    public Set<Raider> getAllRaiders() {
        Set<Raider> set = Sets.newHashSet();

        for (Set<Raider> set1 : this.groupRaiderMap.values()) {
            set.addAll(set1);
        }

        return set;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<ServerPlayer> validPlayer() {
        return player -> {
            BlockPos blockPos = player.blockPosition();
            return ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player) && player.isAlive() && player.level().getRaidAt(blockPos) == this; // Folia - make raids thread-safe
        };
    }

    private void updatePlayers(ServerLevel level) {
        Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<ServerPlayer> players = level.getPlayers(this.validPlayer());

        for (ServerPlayer serverPlayer : players) {
            if (!set.contains(serverPlayer)) {
                this.raidEvent.addPlayer(serverPlayer);
            }
        }

        for (ServerPlayer serverPlayerx : set) {
            if (!players.contains(serverPlayerx)) {
                this.raidEvent.removePlayer(serverPlayerx);
            }
        }
    }

    public int getMaxRaidOmenLevel() {
        return 5;
    }

    public int getRaidOmenLevel() {
        return this.raidOmenLevel;
    }

    public void setRaidOmenLevel(int raidOmenLevel) {
        this.raidOmenLevel = raidOmenLevel;
    }

    public boolean absorbRaidOmen(ServerPlayer player) {
        MobEffectInstance effect = player.getEffect(MobEffects.RAID_OMEN);
        if (effect == null) {
            return false;
        } else {
            this.raidOmenLevel = this.raidOmenLevel + effect.getAmplifier() + 1;
            this.raidOmenLevel = Mth.clamp(this.raidOmenLevel, 0, this.getMaxRaidOmenLevel());
            if (!this.hasFirstWaveSpawned()) {
                player.awardStat(Stats.RAID_TRIGGER);
                CriteriaTriggers.RAID_OMEN.trigger(player);
            }

            return true;
        }
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.RaidStatus.STOPPED;
    }

    public void tick(ServerLevel level) {
        if (!this.isStopped()) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean flag = this.active;
                this.active = level.hasChunkAt(this.center);
                if (level.getDifficulty() == Difficulty.PEACEFUL) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(level, this, org.bukkit.event.raid.RaidStopEvent.Reason.PEACE); // CraftBukkit
                    this.stop();
                    return;
                }

                if (flag != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!level.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection(level);
                }

                if (!level.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(level, this, new java.util.ArrayList<>()); // CraftBukkit
                    } else {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(level, this, org.bukkit.event.raid.RaidStopEvent.Reason.NOT_IN_VILLAGE); // CraftBukkit
                        this.stop();
                    }
                }

                this.ticksActive++;
                if (this.ticksActive >= 48000L) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(level, this, org.bukkit.event.raid.RaidStopEvent.Reason.TIMEOUT); // CraftBukkit
                    this.stop();
                    return;
                }

                int totalRaidersAlive = this.getTotalRaidersAlive();
                if (totalRaidersAlive == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks <= 0) {
                        if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                            this.raidCooldownTicks = 300;
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                            return;
                        }
                    } else {
                        boolean isPresent = this.waveSpawnPos.isPresent();
                        boolean flag1 = !isPresent && this.raidCooldownTicks % 5 == 0;
                        if (isPresent && !level.isPositionEntityTicking(this.waveSpawnPos.get())) {
                            flag1 = true;
                        }

                        if (flag1) {
                            this.waveSpawnPos = this.getValidSpawnPos(level);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers(level);
                        }

                        this.raidCooldownTicks--;
                        this.raidEvent.setProgress(Mth.clamp((300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers(level);
                    this.updateRaiders(level);
                    if (totalRaidersAlive > 0) {
                        if (totalRaidersAlive <= 2) {
                            this.raidEvent
                                .setName(
                                    RAID_NAME_COMPONENT.copy()
                                        .append(" - ")
                                        .append(Component.translatable("event.minecraft.raid.raiders_remaining", totalRaidersAlive))
                                );
                        } else {
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(RAID_NAME_COMPONENT);
                    }
                }

                if (SharedConstants.DEBUG_RAIDS) {
                    this.raidEvent
                        .setName(
                            RAID_NAME_COMPONENT.copy()
                                .append(" wave: ")
                                .append(this.groupsSpawned + "")
                                .append(CommonComponents.SPACE)
                                .append("Raiders alive: ")
                                .append(this.getTotalRaidersAlive() + "")
                                .append(CommonComponents.SPACE)
                                .append(this.getHealthOfLivingRaiders() + "")
                                .append(" / ")
                                .append(this.totalHealth + "")
                                .append(" Is bonus? ")
                                .append((this.hasBonusWave() && this.hasSpawnedBonusWave()) + "")
                                .append(" Status: ")
                                .append(this.status.getSerializedName())
                        );
                }

                boolean isPresentx = false;
                int i = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPos blockPos = this.waveSpawnPos.orElseGet(() -> this.findRandomSpawnPos(level, 20));
                    if (blockPos != null) {
                        this.started = true;
                        this.spawnGroup(level, blockPos);
                        if (!isPresentx) {
                            this.playSound(level, blockPos);
                            isPresentx = true;
                        }
                    } else {
                        i++;
                    }

                    if (i > 5) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(level, this, org.bukkit.event.raid.RaidStopEvent.Reason.UNSPAWNABLE); // CraftBukkit
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && totalRaidersAlive == 0) {
                    if (this.postRaidTicks < 40) {
                        this.postRaidTicks++;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;

                        List<org.bukkit.entity.Player> winners = new java.util.ArrayList<>(); // CraftBukkit
                        for (UUID uuid : this.heroesOfTheVillage) {
                            Entity entity = level.getEntity(uuid);
                            if (entity instanceof LivingEntity livingEntity && !entity.isSpectator()) {
                                //livingEntity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true)); // Folia start - Fix off region raid heroes - moved down
                                if (livingEntity instanceof ServerPlayer serverPlayer) {
                                    //serverPlayer.awardStat(Stats.RAID_WIN); // Folia start - Fix off region raid heroes - moved down
                                    //CriteriaTriggers.RAID_WIN.trigger(serverPlayer); // Folia start - Fix off region raid heroes - moved down
                                    winners.add(serverPlayer.getBukkitEntity()); // CraftBukkit
                                }
                                // Folia start - Fix off region raid heroes
                                livingEntity.getBukkitEntity().taskScheduler.schedule((LivingEntity lv) -> {
                                    lv.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                    if (lv instanceof ServerPlayer serverPlayer) {
                                        serverPlayer.awardStat(Stats.RAID_WIN);
                                        CriteriaTriggers.RAID_WIN.trigger(serverPlayer);
                                    }
                                }, null, 1L);
                                // Folia end - Fix off region raid heroes
                            }
                        }
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(level, this, winners); // CraftBukkit
                    }
                }

                this.setDirty(level);
            } else if (this.isOver()) {
                this.celebrationTicks++;
                if (this.celebrationTicks >= 600) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(level, this, org.bukkit.event.raid.RaidStopEvent.Reason.FINISHED); // CraftBukkit
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers(level);
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }
        }
    }

    private void moveRaidCenterToNearbyVillageSection(ServerLevel level) {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
        stream.filter(level::isVillage).map(SectionPos::center).min(Comparator.comparingDouble(pos -> pos.distSqr(this.center))).ifPresent(this::setCenter);
    }

    private Optional<BlockPos> getValidSpawnPos(ServerLevel level) {
        BlockPos blockPos = this.findRandomSpawnPos(level, 8);
        return blockPos != null ? Optional.of(blockPos) : Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.raidOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders(ServerLevel level) {
        Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
        Set<Raider> set = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<Raider> set1 = iterator.next();

            for (Raider raider : set1) {
                BlockPos blockPos = raider.blockPosition();
                if (raider.isRemoved() || raider.level().dimension() != level.dimension() || this.center.distSqr(blockPos) >= 12544.0) {
                    set.add(raider);
                } else if (raider.tickCount > 600) {
                    if (level.getEntity(raider.getUUID()) == null) {
                        set.add(raider);
                    }

                    if (!level.isVillage(blockPos) && raider.getNoActionTime() > 2400) {
                        raider.setTicksOutsideRaid(raider.getTicksOutsideRaid() + 1);
                    }

                    if (raider.getTicksOutsideRaid() >= 30) {
                        set.add(raider);
                    }
                }
            }
        }

        for (Raider raider1 : set) {
            this.removeFromRaid(level, raider1, true);
            if (raider1.isPatrolLeader()) {
                this.removeLeader(raider1.getWave());
            }
        }
    }

    private void playSound(ServerLevel level, BlockPos pos) {
        float f = 13.0F;
        int i = 64;
        Collection<ServerPlayer> players = this.raidEvent.getPlayers();
        long randomLong = this.random.nextLong();

        for (ServerPlayer serverPlayer : level.getLocalPlayers()) { // Folia - region threading
            Vec3 vec3 = serverPlayer.position();
            Vec3 vec31 = Vec3.atCenterOf(pos);
            double squareRoot = Math.sqrt((vec31.x - vec3.x) * (vec31.x - vec3.x) + (vec31.z - vec3.z) * (vec31.z - vec3.z));
            double d = vec3.x + 13.0 / squareRoot * (vec31.x - vec3.x);
            double d1 = vec3.z + 13.0 / squareRoot * (vec31.z - vec3.z);
            if (squareRoot <= 64.0 || players.contains(serverPlayer)) {
                serverPlayer.connection
                    .send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, d, serverPlayer.getY(), d1, 64.0F, 1.0F, randomLong));
            }
        }
    }

    private void spawnGroup(ServerLevel level, BlockPos pos) {
        boolean flag = false;
        int i = this.groupsSpawned + 1; final int wave = i; // Paper - OBFHELPER
        this.totalHealth = 0.0F;
        DifficultyInstance currentDifficultyAt = level.getCurrentDifficultyAt(pos);
        boolean shouldSpawnBonusGroup = this.shouldSpawnBonusGroup();

        for (Raid.RaiderType raiderType : Raid.RaiderType.VALUES) {
            int i1 = this.getDefaultNumSpawns(raiderType, i, shouldSpawnBonusGroup)
                + this.getPotentialBonusSpawns(raiderType, this.random, i, currentDifficultyAt, shouldSpawnBonusGroup);
            int i2 = 0;

            for (int i3 = 0; i3 < i1; i3++) {
                Raider raider = raiderType.entityType.create(level, EntitySpawnReason.EVENT);
                if (raider == null) {
                    break;
                }

                if (!flag && raider.canBeLeader()) {
                    raider.setPatrolLeader(true);
                    this.setLeader(i, raider);
                    flag = true;
                }

                this.joinRaid(level, i, raider, pos, false);
                if (raiderType.entityType == EntityType.RAVAGER) {
                    Raider raider1 = null;
                    if (i == this.getNumGroups(Difficulty.NORMAL)) {
                        raider1 = EntityType.PILLAGER.create(level, EntitySpawnReason.EVENT);
                    } else if (i >= this.getNumGroups(Difficulty.HARD)) {
                        if (i2 == 0) {
                            raider1 = EntityType.EVOKER.create(level, EntitySpawnReason.EVENT);
                        } else {
                            raider1 = EntityType.VINDICATOR.create(level, EntitySpawnReason.EVENT);
                        }
                    }

                    i2++;
                    if (raider1 != null) {
                        this.joinRaid(level, i, raider1, pos, false);
                        raider1.snapTo(pos, 0.0F, 0.0F);
                        raider1.startRiding(raider, false, false);
                    }
                }
            }
        }

        this.waveSpawnPos = Optional.empty();
        this.groupsSpawned++;
        this.updateBossbar();
        this.setDirty(level);
        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidSpawnWaveEvent(level, this, java.util.Objects.requireNonNull(this.getLeader(wave)), this.groupRaiderMap.get(wave)); // CraftBukkit
    }

    public void joinRaid(ServerLevel level, int wave, Raider raider, @Nullable BlockPos pos, boolean isRecruited) {
        boolean flag = this.addWaveMob(level, wave, raider);
        if (flag) {
            raider.setCurrentRaid(this);
            raider.setWave(wave);
            raider.setCanJoinRaid(true);
            raider.setTicksOutsideRaid(0);
            if (!isRecruited && pos != null) {
                raider.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
                raider.applyRaidBuffs(level, wave, false);
                raider.setOnGround(true);
                level.addFreshEntityWithPassengers(raider, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID); // CraftBukkit
            }
        }
    }

    public void updateBossbar() {
        this.raidEvent.setProgress(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;

        for (Set<Raider> set : this.groupRaiderMap.values()) {
            for (Raider raider : set) {
                f += raider.getHealth();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(ServerLevel level, Raider raider, boolean wanderedOutOfRaid) {
        Set<Raider> set = this.groupRaiderMap.get(raider.getWave());
        if (set != null) {
            boolean flag = set.remove(raider);
            if (flag) {
                if (wanderedOutOfRaid) {
                    this.totalHealth = this.totalHealth - raider.getHealth();
                }

                raider.setCurrentRaid(null);
                this.updateBossbar();
                this.setDirty(level);
            }
        }
    }

    private void setDirty(ServerLevel level) {
        level.getRaids().setDirty();
    }

    public static ItemStack getOminousBannerInstance(HolderGetter<BannerPattern> patternRegistry) {
        ItemStack itemStack = new ItemStack(Items.WHITE_BANNER);
        BannerPatternLayers bannerPatternLayers = new BannerPatternLayers.Builder()
            .addIfRegistered(patternRegistry, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_CENTER, DyeColor.GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK)
            .addIfRegistered(patternRegistry, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.BORDER, DyeColor.BLACK)
            .build();
        itemStack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers);
        itemStack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
        itemStack.set(DataComponents.ITEM_NAME, OMINOUS_BANNER_PATTERN_NAME);
        itemStack.set(DataComponents.RARITY, Rarity.UNCOMMON);
        return itemStack;
    }

    public @Nullable Raider getLeader(int wave) {
        return this.groupToLeaderMap.get(wave);
    }

    private @Nullable BlockPos findRandomSpawnPos(ServerLevel level, int attempts) {
        int i = this.raidCooldownTicks / 20;
        float f = 0.22F * i - 0.24F;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        float f1 = level.random.nextFloat() * (float) (Math.PI * 2);

        for (int i1 = 0; i1 < attempts; i1++) {
            float f2 = f1 + (float) Math.PI * i1 / 8.0F;
            int i2 = this.center.getX() + Mth.floor(Mth.cos(f2) * 32.0F * f) + level.random.nextInt(3) * Mth.floor(f);
            int i3 = this.center.getZ() + Mth.floor(Mth.sin(f2) * 32.0F * f) + level.random.nextInt(3) * Mth.floor(f);
            int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, i2, i3);
            if (Mth.abs(height - this.center.getY()) <= 96) {
                mutableBlockPos.set(i2, height, i3);
                if (!level.isVillage(mutableBlockPos) || i <= 7) {
                    int i4 = 10;
                    if (level.hasChunksAt(mutableBlockPos.getX() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getZ() + 10)
                        && level.isPositionEntityTicking(mutableBlockPos)
                        && (
                            RAVAGER_SPAWN_PLACEMENT_TYPE.isSpawnPositionOk(level, mutableBlockPos, EntityType.RAVAGER)
                                || level.getBlockState(mutableBlockPos.below()).is(Blocks.SNOW) && level.getBlockState(mutableBlockPos).isAir()
                        )) {
                        return mutableBlockPos;
                    }
                }
            }
        }

        return null;
    }

    private boolean addWaveMob(ServerLevel level, int wave, Raider raider) {
        // Folia start - make raids thread-safe
        if (!this.ownsRaid(level)) {
            return false;
        }
        // Folia end - make raids thread-safe
        return this.addWaveMob(level, wave, raider, true);
    }

    public boolean addWaveMob(ServerLevel level, int wave, Raider raider, boolean isRecruited) {
        this.groupRaiderMap.computeIfAbsent(wave, integer -> Sets.newHashSet());
        Set<Raider> set = this.groupRaiderMap.get(wave);
        Raider raider1 = null;

        for (Raider raider2 : set) {
            if (raider2.getUUID().equals(raider.getUUID())) {
                raider1 = raider2;
                break;
            }
        }

        if (raider1 != null) {
            set.remove(raider1);
            set.add(raider);
        }

        set.add(raider);
        if (isRecruited) {
            this.totalHealth = this.totalHealth + raider.getHealth();
        }

        this.updateBossbar();
        this.setDirty(level);
        return true;
    }

    public void setLeader(int wave, Raider raider) {
        this.groupToLeaderMap.put(wave, raider);
        raider.setItemSlot(EquipmentSlot.HEAD, getOminousBannerInstance(raider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        raider.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public void removeLeader(int wave) {
        this.groupToLeaderMap.remove(wave);
    }

    public BlockPos getCenter() {
        return this.center;
    }

    private void setCenter(BlockPos center) {
        this.center = center;
    }

    private int getDefaultNumSpawns(Raid.RaiderType raiderType, int wave, boolean shouldSpawnBonusGroup) {
        return shouldSpawnBonusGroup ? raiderType.spawnsPerWaveBeforeBonus[this.numGroups] : raiderType.spawnsPerWaveBeforeBonus[wave];
    }

    private int getPotentialBonusSpawns(Raid.RaiderType raiderType, RandomSource random, int wave, DifficultyInstance difficulty, boolean shouldSpawnBonusGroup) {
        Difficulty difficulty1 = difficulty.getDifficulty();
        boolean flag = difficulty1 == Difficulty.EASY;
        boolean flag1 = difficulty1 == Difficulty.NORMAL;
        int i;
        switch (raiderType) {
            case VINDICATOR:
            case PILLAGER:
                if (flag) {
                    i = random.nextInt(2);
                } else if (flag1) {
                    i = 1;
                } else {
                    i = 2;
                }
                break;
            case EVOKER:
            default:
                return 0;
            case WITCH:
                if (flag || wave <= 2 || wave == 4) {
                    return 0;
                }

                i = 1;
                break;
            case RAVAGER:
                i = !flag && shouldSpawnBonusGroup ? 1 : 0;
        }

        return i > 0 ? random.nextInt(i + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getNumGroups(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> 3;
            case NORMAL -> 5;
            case HARD -> 7;
        };
    }

    public float getEnchantOdds() {
        int raidOmenLevel = this.getRaidOmenLevel();
        if (raidOmenLevel == 2) {
            return 0.1F;
        } else if (raidOmenLevel == 3) {
            return 0.25F;
        } else if (raidOmenLevel == 4) {
            return 0.5F;
        } else {
            return raidOmenLevel == 5 ? 0.75F : 0.0F;
        }
    }

    public void addHeroOfTheVillage(Entity player) {
        this.heroesOfTheVillage.add(player.getUUID());
    }

    // CraftBukkit start - a method to get all raiders
    public java.util.Collection<Raider> getRaiders() {
        return this.groupRaiderMap.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
    }
    // CraftBukkit end

    static enum RaidStatus implements StringRepresentable {
        ONGOING("ongoing"),
        VICTORY("victory"),
        LOSS("loss"),
        STOPPED("stopped");

        public static final Codec<Raid.RaidStatus> CODEC = StringRepresentable.fromEnum(Raid.RaidStatus::values);
        private final String name;

        private RaidStatus(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    static enum RaiderType {
        VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}),
        EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}),
        PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}),
        WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}),
        RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        static final Raid.RaiderType[] VALUES = values();
        final EntityType<? extends Raider> entityType;
        final int[] spawnsPerWaveBeforeBonus;

        private RaiderType(final EntityType<? extends Raider> entityType, final int[] spawnsPerWaveBeforeBonus) {
            this.entityType = entityType;
            this.spawnsPerWaveBeforeBonus = spawnsPerWaveBeforeBonus;
        }
    }
}
