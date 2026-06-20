package net.minecraft.world.level.dimension.end;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockPredicate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EndDragonFight {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TICKS_BEFORE_DRAGON_RESPAWN = 1200;
    private static final int TIME_BETWEEN_CRYSTAL_SCANS = 100;
    public static final int TIME_BETWEEN_PLAYER_SCANS = 20;
    private static final int ARENA_SIZE_CHUNKS = 8;
    public static final int ARENA_TICKET_LEVEL = 9;
    public static final int GATEWAY_COUNT = 20;
    private static final int GATEWAY_DISTANCE = 96;
    public static final int DRAGON_SPAWN_Y = 128;
    private final Predicate<Entity> validPlayer;
    private static final Component DEFAULT_BOSS_EVENT_NAME = Component.translatable("entity.minecraft.ender_dragon"); // Paper - reset EnderDragon boss event name
    public final ServerBossEvent dragonEvent = (ServerBossEvent)new ServerBossEvent(
            DEFAULT_BOSS_EVENT_NAME, BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS // Paper - reset EnderDragon boss event name
        )
        .setPlayBossMusic(true)
        .setCreateWorldFog(true);
    public final ServerLevel level;
    public final BlockPos origin; // Folia - region threading
    public final ObjectArrayList<Integer> gateways = new ObjectArrayList<>();
    private final BlockPattern exitPortalPattern;
    private int ticksSinceDragonSeen;
    private int crystalsAlive;
    private int ticksSinceCrystalsScanned;
    private int ticksSinceLastPlayerScan = 21;
    private boolean dragonKilled;
    public boolean previouslyKilled;
    private boolean skipArenaLoadedCheck = false;
    public @Nullable UUID dragonUUID;
    private boolean needsStateScanning = true;
    public @Nullable BlockPos portalLocation;
    public @Nullable DragonRespawnAnimation respawnStage;
    private int respawnTime;
    public @Nullable List<EndCrystal> respawnCrystals;

    public EndDragonFight(ServerLevel level, long seed, EndDragonFight.Data data) {
        this(level, seed, data, BlockPos.ZERO);
    }

    public EndDragonFight(ServerLevel level, long seed, EndDragonFight.Data data, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        this.validPlayer = EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.withinDistance(origin.getX(), 128 + origin.getY(), origin.getZ(), 192.0));
        this.needsStateScanning = data.needsStateScanning;
        this.dragonUUID = data.dragonUUID.orElse(null);
        this.dragonKilled = data.dragonKilled;
        this.previouslyKilled = data.previouslyKilled;
        if (data.isRespawning) {
            this.respawnStage = DragonRespawnAnimation.START;
        }
        // Paper start - Add config to disable ender dragon legacy check
        if (data == EndDragonFight.Data.DEFAULT && !level.paperConfig().entities.spawning.scanForLegacyEnderDragon) {
            this.needsStateScanning = false;
            this.dragonKilled = true;
        }
        // Paper end - Add config to disable ender dragon legacy check
        this.portalLocation = data.exitPortalLocation.orElse(null);
        this.gateways.addAll(data.gateways.orElseGet(() -> {
            ObjectArrayList<Integer> list = new ObjectArrayList<>(ContiguousSet.create(Range.closedOpen(0, 20), DiscreteDomain.integers()));
            Util.shuffle(list, RandomSource.create(seed));
            return list;
        }));
        this.exitPortalPattern = BlockPatternBuilder.start()
            .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
            .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
            .aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ")
            .aisle("  ###  ", " #   # ", "#     #", "#  #  #", "#     #", " #   # ", "  ###  ")
            .aisle("       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       ")
            .where('#', BlockInWorld.hasState(BlockPredicate.forBlock(Blocks.BEDROCK)))
            .build();
    }

    @Deprecated
    @VisibleForTesting
    public void skipArenaLoadedCheck() {
        this.skipArenaLoadedCheck = true;
    }

    public EndDragonFight.Data saveData() {
        return new EndDragonFight.Data(
            this.needsStateScanning,
            this.dragonKilled,
            this.previouslyKilled,
            false,
            Optional.ofNullable(this.dragonUUID),
            Optional.ofNullable(this.portalLocation),
            Optional.of(this.gateways)
        );
    }

    public void tick() {
        this.dragonEvent.setVisible(!this.dragonKilled);
        if (++this.ticksSinceLastPlayerScan >= 20) {
            this.updatePlayers();
            this.ticksSinceLastPlayerScan = 0;
        }

        if (!this.dragonEvent.getPlayers().isEmpty()) {
            this.level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, new ChunkPos(0, 0), 9);
            boolean isArenaLoaded = this.isArenaLoaded(); if (!isArenaLoaded) { return; } // Folia - region threading - don't tick if we don't own the entire region
            if (this.needsStateScanning && isArenaLoaded) {
                this.scanState();
                this.needsStateScanning = false;
            }

            if (this.respawnStage != null) {
                if (this.respawnCrystals == null && isArenaLoaded) {
                    this.respawnStage = null;
                    this.tryRespawn();
                }

                this.respawnStage.tick(this.level, this, this.respawnCrystals, this.respawnTime++, this.portalLocation);
            }

            if (!this.dragonKilled) {
                if ((this.dragonUUID == null || ++this.ticksSinceDragonSeen >= 1200) && isArenaLoaded) {
                    this.findOrCreateDragon();
                    this.ticksSinceDragonSeen = 0;
                }

                if (++this.ticksSinceCrystalsScanned >= 100 && isArenaLoaded) {
                    this.updateCrystalCount();
                    this.ticksSinceCrystalsScanned = 0;
                }
            }
        } else {
            this.level.getChunkSource().removeTicketWithRadius(TicketType.DRAGON, new ChunkPos(0, 0), 9);
        }
    }

    private void scanState() {
        LOGGER.info("Scanning for legacy world dragon fight...");
        boolean hasActiveExitPortal = this.hasActiveExitPortal();
        if (hasActiveExitPortal) {
            LOGGER.info("Found that the dragon has been killed in this world already.");
            this.previouslyKilled = true;
        } else {
            LOGGER.info("Found that the dragon has not yet been killed in this world.");
            this.previouslyKilled = false;
            if (this.findExitPortal() == null) {
                this.spawnExitPortal(false);
            }
        }

        List<? extends EnderDragon> dragons = this.level.getDragons();
        // Folia start - region threading
        // we do not want to deal with any dragons NOT nearby
        dragons.removeIf((EnderDragon dragon) -> {
            return !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(dragon);
        });
        // Folia end - region threading
        if (dragons.isEmpty()) {
            this.dragonKilled = true;
        } else {
            EnderDragon enderDragon = dragons.get(0);
            this.dragonUUID = enderDragon.getUUID();
            LOGGER.info("Found that there's a dragon still alive ({})", enderDragon);
            this.dragonKilled = false;
            if (!hasActiveExitPortal && this.level.paperConfig().entities.behavior.shouldRemoveDragon) { // Paper - Toggle for removing existing dragon
                LOGGER.info("But we didn't have a portal, let's remove it.");
                enderDragon.discard(null); // CraftBukkit - add Bukkit remove cause
                this.dragonUUID = null;
            }
        }

        if (!this.previouslyKilled && this.dragonKilled) {
            this.dragonKilled = false;
        }
    }

    private void findOrCreateDragon() {
        List<? extends EnderDragon> dragons = this.level.getDragons();
        if (dragons.isEmpty()) {
            LOGGER.debug("Haven't seen the dragon, respawning it");
            this.createNewDragon();
        } else {
            LOGGER.debug("Haven't seen our dragon, but found another one to use.");
            this.dragonUUID = dragons.get(0).getUUID();
        }
    }

    public void setRespawnStage(DragonRespawnAnimation state) {
        if (this.respawnStage == null) {
            throw new IllegalStateException("Dragon respawn isn't in progress, can't skip ahead in the animation.");
        } else {
            this.respawnTime = 0;
            if (state == DragonRespawnAnimation.END) {
                this.respawnStage = null;
                this.dragonKilled = false;
                EnderDragon enderDragon = this.createNewDragon();
                if (enderDragon != null) {
                    for (ServerPlayer serverPlayer : this.dragonEvent.getPlayers()) {
                        CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, enderDragon);
                    }
                }
            } else {
                this.respawnStage = state;
            }
        }
    }

    private boolean hasActiveExitPortal() {
        for (int i = -8; i <= 8; i++) {
            for (int i1 = -8; i1 <= 8; i1++) {
                LevelChunk chunk = this.level.getChunk(i, i1);

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TheEndPortalBlockEntity) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Leaves start - optimizedDragonRespawn
    private int cachePortalChunkIteratorX = -8;
    private int cachePortalChunkIteratorZ = -8;
    private int cachePortalOriginIteratorY = -1;

    public BlockPattern.@Nullable BlockPatternMatch findExitPortal() {
        if (me.earthme.luminol.config.modules.optimizations.OptimizedDragonRespawnConfig.optimizedRespawn) {
            int i, j;
            for (i = cachePortalChunkIteratorX; i <= 8; ++i) {
                for (j = cachePortalChunkIteratorZ; j <= 8; ++j) {
                    LevelChunk worldChunk = this.level.getChunk(i, j);
                    for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
                        if (blockEntity instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity) {
                            continue;
                        }
                        if (blockEntity instanceof TheEndPortalBlockEntity) {
                            BlockPattern.BlockPatternMatch blockPatternMatch = this.exitPortalPattern.find(this.level, blockEntity.getBlockPos());
                            if (blockPatternMatch != null) {
                                BlockPos blockPos = blockPatternMatch.getBlock(3, 3, 3).getPos();
                                if (this.portalLocation == null) {
                                    this.portalLocation = blockPos;
                                }
                                //No need to judge whether optimizing option is open
                                cachePortalChunkIteratorX = i;
                                cachePortalChunkIteratorZ = j;
                                return blockPatternMatch;
                            }
                        }
                    }
                }
            }

            if (this.needsStateScanning || this.portalLocation == null) {
                if (cachePortalOriginIteratorY != -1) {
                    i = cachePortalOriginIteratorY;
                } else {
                    i = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(BlockPos.ZERO)).getY();
                }
                boolean notFirstSearch = false;
                for (j = i; j >= 0; --j) {
                    BlockPattern.BlockPatternMatch result2 = null;
                    if (notFirstSearch) {
                        result2 = org.leavesmc.leaves.util.BlockPatternHelper.partialSearchAround(this.exitPortalPattern, this.level, new BlockPos(EndPodiumFeature.getLocation(BlockPos.ZERO).getY(), j, EndPodiumFeature.getLocation(BlockPos.ZERO).getZ()));
                    } else {
                        result2 = this.exitPortalPattern.find(this.level, new BlockPos(EndPodiumFeature.getLocation(BlockPos.ZERO).getX(), j, EndPodiumFeature.getLocation(BlockPos.ZERO).getZ()));
                    }
                    if (result2 != null) {
                        if (this.portalLocation == null) {
                            this.portalLocation = result2.getBlock(3, 3, 3).getPos();
                        }
                        cachePortalOriginIteratorY = j;
                        return result2;
                    }
                    notFirstSearch = true;
                }
            }

            return null;
        }
        // Leaves end - optimizedDragonRespawn

        ChunkPos chunkPos = new ChunkPos(this.origin);

        for (int i = -8 + chunkPos.x; i <= 8 + chunkPos.x; i++) {
            for (int i1 = -8 + chunkPos.z; i1 <= 8 + chunkPos.z; i1++) {
                LevelChunk chunk = this.level.getChunk(i, i1);

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TheEndPortalBlockEntity) {
                        BlockPattern.BlockPatternMatch blockPatternMatch = this.exitPortalPattern.find(this.level, blockEntity.getBlockPos());
                        if (blockPatternMatch != null) {
                            BlockPos pos = blockPatternMatch.getBlock(3, 3, 3).getPos();
                            if (this.portalLocation == null) {
                                this.portalLocation = pos;
                            }

                            return blockPatternMatch;
                        }
                    }
                }
            }
        }

        BlockPos location = EndPodiumFeature.getLocation(this.origin);
        int i1 = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, location).getY();

        for (int i2 = i1; i2 >= this.level.getMinY(); i2--) {
            BlockPattern.BlockPatternMatch blockPatternMatch1 = this.exitPortalPattern.find(this.level, new BlockPos(location.getX(), i2, location.getZ()));
            if (blockPatternMatch1 != null) {
                if (this.portalLocation == null) {
                    this.portalLocation = blockPatternMatch1.getBlock(3, 3, 3).getPos();
                }

                return blockPatternMatch1;
            }
        }

        return null;
    }

    private boolean isArenaLoaded() {
        if (this.skipArenaLoadedCheck) {
            return true;
        } else {
            ChunkPos chunkPos = new ChunkPos(this.origin);

            for (int i = -8 + chunkPos.x; i <= 8 + chunkPos.x; i++) {
                for (int i1 = 8 + chunkPos.z; i1 <= 8 + chunkPos.z; i1++) {
                    ChunkAccess chunk = this.level.getChunkIfLoaded(i, i1); // Folia - region threading
                    if (!(chunk instanceof LevelChunk) || !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, i, i1, this.level.regioniser.regionSectionChunkSize)) {
                        return false;
                    }

                    FullChunkStatus fullStatus = ((LevelChunk)chunk).getFullStatus();
                    if (!fullStatus.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet();

        for (ServerPlayer serverPlayer : this.level.getPlayers(this.validPlayer)) {
            this.dragonEvent.addPlayer(serverPlayer);
            set.add(serverPlayer);
        }

        Set<ServerPlayer> set1 = Sets.newHashSet(this.dragonEvent.getPlayers());
        set1.removeAll(set);

        for (ServerPlayer serverPlayer1 : set1) {
            this.dragonEvent.removePlayer(serverPlayer1);
        }
    }

    private void updateCrystalCount() {
        this.ticksSinceCrystalsScanned = 0;
        this.crystalsAlive = 0;

        for (SpikeFeature.EndSpike endSpike : SpikeFeature.getSpikesForLevel(this.level)) {
            this.crystalsAlive = this.crystalsAlive + this.level.getEntitiesOfClass(EndCrystal.class, endSpike.getTopBoundingBox()).size();
        }

        LOGGER.debug("Found {} end crystals still alive", this.crystalsAlive);
    }

    public void setDragonKilled(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(0.0F);
            this.dragonEvent.setVisible(false);
            this.spawnExitPortal(true);
            this.spawnNewGateway();
            // Paper start - Add DragonEggFormEvent
            BlockPos eggPosition = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(this.origin));
            org.bukkit.craftbukkit.block.CraftBlockState eggState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(this.level, eggPosition);
            eggState.setData(Blocks.DRAGON_EGG.defaultBlockState());
            io.papermc.paper.event.block.DragonEggFormEvent eggEvent = new io.papermc.paper.event.block.DragonEggFormEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this.level, eggPosition), eggState,
                new org.bukkit.craftbukkit.boss.CraftDragonBattle(this));
            // Paper end - Add DragonEggFormEvent
            if (this.level.paperConfig().entities.behavior.enderDragonsDeathAlwaysPlacesDragonEgg || !this.previouslyKilled) { // Paper - Add toggle for always placing the dragon egg
                // Paper start - Add DragonEggFormEvent
                // this.level.setBlockAndUpdate(this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(this.origin)), Blocks.DRAGON_EGG.defaultBlockState());
            } else {
                eggEvent.setCancelled(true);
            }
            if (eggEvent.callEvent()) {
                ((org.bukkit.craftbukkit.block.CraftBlockState) eggEvent.getNewState()).place(net.minecraft.world.level.block.Block.UPDATE_ALL);
                // Paper end - Add DragonEggFormEvent
            }

            this.previouslyKilled = true;
            this.dragonKilled = true;
        }
    }

    @Deprecated
    @VisibleForTesting
    public void removeAllGateways() {
        this.gateways.clear();
    }

    // Paper start - More DragonBattle API
    public boolean spawnNewGatewayIfPossible() {
        if (!this.gateways.isEmpty()) {
            this.spawnNewGateway();
            return true;
        }
        return false;
    }

    public List<EndCrystal> getSpikeCrystals() {
        final List<EndCrystal> endCrystals = new java.util.ArrayList<>();
        for (final SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(this.level)) {
            endCrystals.addAll(this.level.getEntitiesOfClass(EndCrystal.class, spike.getTopBoundingBox()));
        }
        return endCrystals;
    }
    // Paper end - More DragonBattle API

    private void spawnNewGateway() {
        if (!this.gateways.isEmpty()) {
            int i = this.gateways.remove(this.gateways.size() - 1);
            int floor = Mth.floor(96.0 * Math.cos(2.0 * (-Math.PI + (Math.PI / 20) * i)));
            int floor1 = Mth.floor(96.0 * Math.sin(2.0 * (-Math.PI + (Math.PI / 20) * i)));
            this.spawnNewGateway(new BlockPos(floor, 75, floor1));
        }
    }

    public void spawnNewGateway(BlockPos pos) {
        this.level.levelEvent(LevelEvent.ANIMATION_END_GATEWAY_SPAWN, pos, 0);
        this.level
            .registryAccess()
            .lookup(Registries.CONFIGURED_FEATURE)
            .flatMap(registry -> registry.get(EndFeatures.END_GATEWAY_DELAYED))
            .ifPresent(endGatewayFeature -> endGatewayFeature.value().place(this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), pos));
    }

    public void spawnExitPortal(boolean active) {
        EndPodiumFeature endPodiumFeature = new EndPodiumFeature(active);
        if (this.portalLocation == null) {
            this.portalLocation = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.origin)).below();

            while (this.level.getBlockState(this.portalLocation).is(Blocks.BEDROCK) && this.portalLocation.getY() > 63) {
                this.portalLocation = this.portalLocation.below();
            }

            this.portalLocation = this.portalLocation.atY(Math.max(this.level.getMinY() + 1, this.portalLocation.getY()));
        }

        // Paper start - Prevent "softlocked" exit portal generation
        if (this.portalLocation.getY() <= this.level.getMinY()) {
            this.portalLocation = this.portalLocation.atY(this.level.getMinY() + 1);
        }
        // Paper end - Prevent "softlocked" exit portal generation
        if (endPodiumFeature.place(
            FeatureConfiguration.NONE, this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), this.portalLocation
        )) {
            int i = Mth.positiveCeilDiv(4, 16);
            this.level.getChunkSource().chunkMap.waitForLightBeforeSending(new ChunkPos(this.portalLocation), i);
        }
    }

    private @Nullable EnderDragon createNewDragon() {
        this.level.getChunkAt(new BlockPos(this.origin.getX(), 128 + this.origin.getY(), this.origin.getZ()));
        EnderDragon enderDragon = EntityType.ENDER_DRAGON.create(this.level, EntitySpawnReason.EVENT);
        if (enderDragon != null) {
            enderDragon.setDragonFight(this);
            enderDragon.setFightOrigin(this.origin);
            enderDragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            enderDragon.snapTo(this.origin.getX(), 128 + this.origin.getY(), this.origin.getZ(), this.level.random.nextFloat() * 360.0F, 0.0F);
            this.level.addFreshEntity(enderDragon);
            this.dragonUUID = enderDragon.getUUID();
            this.resetSpikeCrystals(); // Paper - Reset ender crystals on dragon spawn
        }

        return enderDragon;
    }

    public void updateDragon(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(dragon.getHealth() / dragon.getMaxHealth());
            this.ticksSinceDragonSeen = 0;
            if (dragon.hasCustomName()) {
                this.dragonEvent.setName(dragon.getDisplayName());
                // Paper start - ensure reset EnderDragon boss event name
            } else {
                this.dragonEvent.setName(DEFAULT_BOSS_EVENT_NAME);
                // Paper end - ensure reset EnderDragon boss event name
            }
        }
    }

    public int getCrystalsAlive() {
        return this.crystalsAlive;
    }

    public void onCrystalDestroyed(EndCrystal crystal, DamageSource damageSource) {
        // Folia start - region threading
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, this.origin)) {
            return;
        }
        // Folia end - region threading
        if (this.respawnStage != null && this.respawnCrystals.contains(crystal)) {
            LOGGER.debug("Aborting respawn sequence");
            this.respawnStage = null;
            this.respawnTime = 0;
            this.resetSpikeCrystals();
            this.spawnExitPortal(true);
        } else {
            this.updateCrystalCount();
            if (this.level.getEntity(this.dragonUUID) instanceof EnderDragon enderDragon) {
                enderDragon.onCrystalDestroyed(this.level, crystal, crystal.blockPosition(), damageSource);
            }
        }
    }

    public boolean hasPreviouslyKilledDragon() {
        return this.previouslyKilled;
    }

    public boolean tryRespawn() { // CraftBukkit - return boolean
        // Paper start - Perf: Do crystal-portal proximity check before entity lookup
        return this.tryRespawn(null);
    }

    public boolean tryRespawn(@Nullable BlockPos placedEndCrystalPos) { // placedEndCrystalPos is null if the tryRespawn() call was not caused by a placed end crystal
        // Paper end - Perf: Do crystal-portal proximity check before entity lookup
        if (this.dragonKilled && this.respawnStage == null && ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this.level, this.origin)) { // Folia - region threading
            BlockPos blockPos = this.portalLocation;
            if (blockPos == null) {
                LOGGER.debug("Tried to respawn, but need to find the portal first.");
                BlockPattern.BlockPatternMatch blockPatternMatch = this.findExitPortal();
                if (blockPatternMatch == null) {
                    LOGGER.debug("Couldn't find a portal, so we made one.");
                    this.spawnExitPortal(true);
                } else {
                    LOGGER.debug("Found the exit portal & saved its location for next time.");
                }

                blockPos = this.portalLocation;
            }
            // Paper start - Perf: Do crystal-portal proximity check before entity lookup
            if (placedEndCrystalPos != null && !level.paperConfig().misc.allowRemoteEnderDragonRespawning) {
                // The end crystal must be 0 or 1 higher than the portal origin
                int dy = placedEndCrystalPos.getY() - blockPos.getY();
                if (dy != 0 && dy != 1) {
                    return false;
                }
                // The end crystal must be within a distance of 1 in one planar direction, and 3 in the other
                int dx = placedEndCrystalPos.getX() - blockPos.getX();
                int dz = placedEndCrystalPos.getZ() - blockPos.getZ();
                if (!((dx >= -1 && dx <= 1 && dz >= -3 && dz <= 3) || (dx >= -3 && dx <= 3 && dz >= -1 && dz <= 1))) {
                    return false;
                }
            }
            // Paper end - Perf: Do crystal-portal proximity check before entity lookup


            List<EndCrystal> list = Lists.newArrayList();
            BlockPos blockPos1 = blockPos.above(1);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                List<EndCrystal> entitiesOfClass = this.level.getEntitiesOfClass(EndCrystal.class, new AABB(blockPos1.relative(direction, 2)));
                if (entitiesOfClass.isEmpty()) {
                    return false; // CraftBukkit - return value
                }

                list.addAll(entitiesOfClass);
            }

            LOGGER.debug("Found all crystals, respawning dragon.");
            return this.respawnDragon(list); // CraftBukkit - return value
        }
        return false; // CraftBukkit - return value
    }

    public boolean respawnDragon(List<EndCrystal> crystals) { // CraftBukkit - return boolean
        // Leaves - start optimizedDragonRespawn
        cachePortalChunkIteratorX = -8;
        cachePortalChunkIteratorZ = -8;
        cachePortalOriginIteratorY = -1;
        // Leaves - end optimizedDragonRespawn
        if (this.dragonKilled && this.respawnStage == null) {
            for (BlockPattern.BlockPatternMatch blockPatternMatch = this.findExitPortal(); blockPatternMatch != null; blockPatternMatch = this.findExitPortal()) {
                for (int i = 0; i < this.exitPortalPattern.getWidth(); i++) {
                    for (int i1 = 0; i1 < this.exitPortalPattern.getHeight(); i1++) {
                        for (int i2 = 0; i2 < this.exitPortalPattern.getDepth(); i2++) {
                            BlockInWorld block = blockPatternMatch.getBlock(i, i1, i2);
                            if (block.getState().is(Blocks.BEDROCK) || block.getState().is(Blocks.END_PORTAL)) {
                                this.level.setBlockAndUpdate(block.getPos(), Blocks.END_STONE.defaultBlockState());
                            }
                        }
                    }
                }
            }

            this.respawnStage = DragonRespawnAnimation.START;
            this.respawnTime = 0;
            this.spawnExitPortal(false);
            this.respawnCrystals = crystals;
            return true; // CraftBukkit - return value
        }
        return false; // CraftBukkit - return value
    }

    public void resetSpikeCrystals() {
        for (SpikeFeature.EndSpike endSpike : SpikeFeature.getSpikesForLevel(this.level)) {
            for (EndCrystal endCrystal : this.level.getEntitiesOfClass(EndCrystal.class, endSpike.getTopBoundingBox())) {
                endCrystal.setInvulnerable(false);
                endCrystal.setBeamTarget(null);
            }
        }
    }

    public @Nullable UUID getDragonUUID() {
        return this.dragonUUID;
    }

    public record Data(
        boolean needsStateScanning,
        boolean dragonKilled,
        boolean previouslyKilled,
        boolean isRespawning,
        Optional<UUID> dragonUUID,
        Optional<BlockPos> exitPortalLocation,
        Optional<List<Integer>> gateways
    ) {
        public static final Codec<EndDragonFight.Data> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.BOOL.fieldOf("NeedsStateScanning").orElse(true).forGetter(EndDragonFight.Data::needsStateScanning),
                    Codec.BOOL.fieldOf("DragonKilled").orElse(false).forGetter(EndDragonFight.Data::dragonKilled),
                    Codec.BOOL.fieldOf("PreviouslyKilled").orElse(false).forGetter(EndDragonFight.Data::previouslyKilled),
                    Codec.BOOL.lenientOptionalFieldOf("IsRespawning", false).forGetter(EndDragonFight.Data::isRespawning),
                    UUIDUtil.CODEC.lenientOptionalFieldOf("Dragon").forGetter(EndDragonFight.Data::dragonUUID),
                    BlockPos.CODEC.lenientOptionalFieldOf("ExitPortalLocation").forGetter(EndDragonFight.Data::exitPortalLocation),
                    Codec.list(Codec.INT).lenientOptionalFieldOf("Gateways").forGetter(EndDragonFight.Data::gateways)
                )
                .apply(instance, EndDragonFight.Data::new)
        );
        public static final EndDragonFight.Data DEFAULT = new EndDragonFight.Data(
            true, false, false, false, Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
