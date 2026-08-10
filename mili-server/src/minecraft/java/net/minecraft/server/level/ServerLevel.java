package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerLevel extends Level implements ServerEntityGetter, WorldGenLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader, ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel { // Paper - rewrite chunk system // Paper - chunk tick iteration
    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList<>(); // Folia - region threading
    public final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final net.minecraft.world.level.storage.PrimaryLevelData serverLevelData; // CraftBukkit - type
    //final EntityTickList entityTickList = new EntityTickList(); // Folia - region threading
    private final me.earthme.luminol.utils.FoliaServerWaypointManager waypointManager; // Luminol - Restore waypoints
    private final EnvironmentAttributeSystem environmentAttributes;
    // Paper - rewrite chunk system
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    //private final LevelTicks<Block> blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded); // Folia - region threading
    //private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded); // Folia - region threading
    //private final PathTypeCache pathTypesByPosCache = new PathTypeCache(); // Folia - region threading
    //final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>(); // Folia - region threading
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    //private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>(); // Folia - region threading
    //private final List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64); // Folia - region threading
    //private boolean handlingTick; // Folia - region threading
    private final List<CustomSpawner> customSpawners;
    private @Nullable EndDragonFight dragonFight;
    final ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<EnderDragonPart> dragonParts = new ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable<>(); // Folia - region threading
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    public final boolean tickTime; // Folia - region threading
    private final RandomSequences randomSequences;
    final LevelDebugSynchronizers debugSynchronizers = new LevelDebugSynchronizers(this);
    final List<ServerPlayer> realPlayers; // Leaves - skip

    // CraftBukkit start
    public final LevelStorageSource.LevelStorageAccess levelStorageAccess;
    public final UUID uuid;
    public final net.minecraft.server.level.progress.LevelLoadListener levelLoadListener;
    // Folia - region threading - move to regionised world data

    @Override
    public @Nullable LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunkAtIfLoadedImmediately(x, z); // Paper - Use getChunkIfLoadedImmediately
    }

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return this.levelStorageAccess.dimensionType;
    }

    // Paper start
    public final boolean areChunksLoadedForMove(AABB box) {
        // copied code from collision methods, so that we can guarantee that they won't load chunks (we don't override
        // CollisionGetter methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(box.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(box.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(box.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(box.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        // Folia start - region threading
        // don't let players move into regions not owned
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            return false;
        }
        // Folia end - region threading

        ServerChunkCache chunkProvider = this.getChunkSource();

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public final void loadChunksForMoveAsync(AABB box, ca.spottedleaf.concurrentutil.util.Priority priority,
                                             java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        // Paper - rewrite chunk system
        int minBlockX = Mth.floor(box.minX - 1.0E-7D) - 3;
        int minBlockZ = Mth.floor(box.minZ - 1.0E-7D) - 3;

        int maxBlockX = Mth.floor(box.maxX + 1.0E-7D) + 3;
        int maxBlockZ = Mth.floor(box.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;

        int maxChunkX = maxBlockX >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        this.loadChunks(minChunkX, minChunkZ, maxChunkX, maxChunkZ, priority, onLoad);
    }

    public final void loadChunks(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                                 ca.spottedleaf.concurrentutil.util.Priority priority,
                                 java.util.function.Consumer<List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, priority, onLoad); // Paper - rewrite chunk system
    }
    // Paper end

    // Paper start - optimise getPlayerByUUID
    @Nullable
    @Override
    public Player getPlayerByUUID(java.util.UUID uuid) {
        final Player player = this.getServer().getPlayerList().getPlayer(uuid);
        return player != null && player.level() == this ? player : null;
    }
    // Paper end - optimise getPlayerByUUID
    // Paper start - rewrite chunk system
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder();
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader chunkLoader = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader((ServerLevel)(Object)this);
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController entityDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController poiDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController chunkDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler;
    private long lastMidTickFailure;
    private long tickedBlocksOrFluids;
    // Folia - region threading - move to regionized data

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.chunkSource.getChunkNow(chunkX, chunkZ);
    }

    @Override
    public final ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (newChunkHolder == null) {
            return null;
        }
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder.ChunkCompletion lastCompletion = newChunkHolder.getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
    }

    @Override
    public final ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus leastStatus) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(leastStatus);
    }

    @Override
    public final void moonrise$midTickTasks() {
        ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
    }

    @Override
    public final ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus status) {
        return this.moonrise$getChunkTaskScheduler().syncLoadNonFull(chunkX, chunkZ, status);
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler moonrise$getChunkTaskScheduler() {
        return this.chunkTaskScheduler;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController  moonrise$getChunkDataController() {
        return this.chunkDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController moonrise$getPoiChunkDataController() {
        return this.poiDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController moonrise$getEntityChunkDataController() {
        return this.entityDataController;
    }

    @Override
    public final int moonrise$getRegionChunkShift() {
        return this.regioniser.sectionChunkShift; // Folia - region threading
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader moonrise$getPlayerChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            chunkStatus, priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, priority, onLoad);
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, chunkStatus, priority, onLoad, null);
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.util.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad, final java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> onEachLoad) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = this.moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        final java.util.concurrent.atomic.AtomicInteger loadedChunks = new java.util.concurrent.atomic.AtomicInteger();
        final Long holderIdentifier = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getNextChunkLoadId();
        final int ticketLevel = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getTicketLevel(chunkStatus);

        final List<ChunkAccess> ret = new ArrayList<>(requiredChunks);

        final java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> consumer = (final ChunkAccess chunk) -> {
            if (chunk != null) {
                synchronized (ret) {
                    ret.add(chunk);
                }
                chunkHolderManager.addTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (onEachLoad != null) {
                onEachLoad.accept(chunk);
            }
            if (loadedChunks.incrementAndGet() == requiredChunks) {
                try {
                    if (onLoad != null) {
                        onLoad.accept(java.util.Collections.unmodifiableList(ret));
                    }
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        final ChunkPos chunkPos = ret.get(i).getPos();

                        chunkHolderManager.removeTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                ca.spottedleaf.moonrise.common.PlatformHooks.get().scheduleChunkLoad(
                    this, cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true, priority, consumer
                );
            }
        }
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }

    @Override
    public final long moonrise$getLastMidTickFailure() {
        return this.lastMidTickFailure;
    }

    @Override
    public final void moonrise$setLastMidTickFailure(final long time) {
        this.lastMidTickFailure = time;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.misc.NearbyPlayers moonrise$getNearbyPlayers() {
        return this.getCurrentWorldData().getNearbyPlayers(); // Folia - region threading
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<LevelChunk> moonrise$getLoadedChunks() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<LevelChunk> moonrise$getTickingChunks() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<LevelChunk> moonrise$getEntityTickingChunks() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public final boolean moonrise$areChunksLoaded(final int fromX, final int fromZ, final int toX, final int toZ) {
        final ServerChunkCache chunkSource = this.chunkSource;

        for (int currZ = fromZ; currZ <= toZ; ++currZ) {
            for (int currX = fromX; currX <= toX; ++currX) {
                if (!chunkSource.hasChunk(currX, currZ)) {
                    return false;
                }
            }
        }

        return true;
    }

   @Override
   public final void moonrise$issueEmergencySave() {
       this.moonrise$getChunkTaskScheduler().chunkHolderManager.saveAllChunks(
           true, true, true, true
       );
   }
    // Paper end - rewrite chunk system
    // Paper start - chunk tick iteration
    // Folia - region threading

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<LevelChunk> moonrise$getPlayerTickingChunks() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }

    @Override
    public final void moonrise$markChunkForPlayerTicking(final LevelChunk chunk) {
        // Folia - region threading
    }

    @Override
    public final void moonrise$removeChunkForPlayerTicking(final LevelChunk chunk) {
        // Folia - region threading
    }

    @Override
    public final void moonrise$addPlayerTickingRequest(final int chunkX, final int chunkZ) {
        // Folia - region threading
    }

    @Override
    public final void moonrise$removePlayerTickingRequest(final int chunkX, final int chunkZ) {
        // Folia - region threading
    }
    // Paper end - chunk tick iteration

    public ServerLevel(
        MinecraftServer server,
        Executor dispatcher,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        net.minecraft.world.level.storage.PrimaryLevelData serverLevelData, // CraftBukkit
        ResourceKey<Level> dimension,
        LevelStem levelStem,
        boolean isDebug,
        long biomeZoomSeed,
        List<CustomSpawner> customSpawners,
        boolean tickTime,
        @Nullable RandomSequences randomSequences,
        org.bukkit.World.Environment env, // CraftBukkit
        org.bukkit.generator.ChunkGenerator gen, // CraftBukkit
        org.bukkit.generator.BiomeProvider biomeProvider // CraftBukkit
    ) {
        // CraftBukkit start
        super(serverLevelData, dimension, server.registryAccess(), levelStem.type(), false, isDebug, biomeZoomSeed, server.getMaxChainedNeighborUpdates(), gen, biomeProvider, env, spigotConfig -> server.paperConfigurations.createWorldConfig(io.papermc.paper.configuration.PaperConfigurations.createWorldContextMap(levelStorageAccess.levelDirectory.path(), serverLevelData.getLevelName(), dimension.identifier(), spigotConfig, server.registryAccess(), serverLevelData.getGameRules())), dispatcher); // Paper - create paper world configs & Async-Anti-Xray: Pass executor
        this.levelStorageAccess = levelStorageAccess;
        this.uuid = org.bukkit.craftbukkit.util.WorldUUID.getOrCreate(levelStorageAccess.levelDirectory.path().toFile());
        this.levelLoadListener = new net.minecraft.server.level.progress.LoggingLevelLoadListener(false, this);
        // CraftBukkit end
        this.tickTime = tickTime;
        this.server = server;
        this.customSpawners = customSpawners;
        this.serverLevelData = serverLevelData;
        ChunkGenerator chunkGenerator = levelStem.generator();
        // CraftBukkit start
        this.serverLevelData.setWorld(this);

        if (biomeProvider != null) {
            net.minecraft.world.level.biome.BiomeSource biomeSource = new org.bukkit.craftbukkit.generator.CustomWorldChunkManager(this.getWorld(), biomeProvider, this.server.registryAccess().lookupOrThrow(Registries.BIOME), chunkGenerator.getBiomeSource()); // Paper - add vanillaBiomeProvider
            if (chunkGenerator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseBased) {
                chunkGenerator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseBased.settings);
            } else if (chunkGenerator instanceof net.minecraft.world.level.levelgen.FlatLevelSource flatLevel) {
                chunkGenerator = new net.minecraft.world.level.levelgen.FlatLevelSource(flatLevel.settings(), biomeSource);
            }
        }

        if (gen != null) {
            chunkGenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkGenerator, gen);
        }
        // CraftBukkit end
        su.plo.matter.Globals.setupGlobals(this); // Leaf - Matter - Secure Seed
        boolean flag = server.forceSynchronousWrites();
        DataFixer fixerUpper = server.getFixerUpper();
        // Paper - rewrite chunk system
        this.chunkSource = new ServerChunkCache(
            this,
            levelStorageAccess,
            fixerUpper,
            server.getStructureManager(),
            dispatcher,
            chunkGenerator,
            this.spigotConfig.viewDistance, // Spigot
            this.spigotConfig.simulationDistance, // Spigot
            flag,
            null, // Paper - rewrite chunk system
            () -> server.overworld().getDataStorage()
        );
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        if (this.canHaveWeather()) {
            this.prepareWeather();
        }

        this.raids = this.getDataStorage().computeIfAbsent(Raids.getType(this.dimensionTypeRegistration()));
        if (!server.isSingleplayer()) {
            serverLevelData.setGameType(server.getDefaultGameType());
        }

        long seed = server.getWorldData().worldGenOptions().seed();
        this.structureCheck = new StructureCheck(
            this.chunkSource.chunkScanner(),
            this.registryAccess(),
            server.getStructureManager(),
            getTypeKey(), // Paper - Fix missing CB diff
            chunkGenerator,
            this.chunkSource.randomState(),
            this,
            chunkGenerator.getBiomeSource(),
            seed,
            fixerUpper
        );
        this.structureManager = new StructureManager(this, this.serverLevelData.worldGenOptions(), this.structureCheck); // CraftBukkit
        if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END) || env == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EndDragonFight(this, this.serverLevelData.worldGenOptions().seed(), this.serverLevelData.endDragonFightData()); // CraftBukkit
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = Objects.requireNonNullElseGet(randomSequences, () -> this.getDataStorage().computeIfAbsent(RandomSequences.TYPE));
        this.waypointManager = new me.earthme.luminol.utils.FoliaServerWaypointManager(); // Luminol - Restore waypoints
        this.environmentAttributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
        //this.updateSkyBrightness(); // Folia - region threading - delay until first tick
        // Paper start - rewrite chunk system
        this.moonrise$setEntityLookup(new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup((ServerLevel)(Object)this, ((ServerLevel)(Object)this).new EntityCallbacks()));
        this.chunkTaskScheduler = new ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler((ServerLevel)(Object)this);
        this.entityDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController(
            new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController.EntityRegionFileStorage(
                new RegionStorageInfo(levelStorageAccess.getLevelId(), dimension, "entities"),
                levelStorageAccess.getDimensionPath(dimension).resolve("entities"),
                server.forceSynchronousWrites()
            ),
            this.chunkTaskScheduler
        );
        this.poiDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController((ServerLevel)(Object)this, this.chunkTaskScheduler);
        this.chunkDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController((ServerLevel)(Object)this, this.chunkTaskScheduler);
        // Paper end - rewrite chunk system
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit
        this.updateTickData(); // Folia - region threading - make sure it is initialised before ticked
        this.realPlayers = Lists.newArrayList(); // Leaves - skip
    }

    // Folia start - region threading
    public final io.papermc.paper.threadedregions.TickRegions tickRegions = new io.papermc.paper.threadedregions.TickRegions();
    public final io.papermc.paper.threadedregions.ThreadedRegionizer<io.papermc.paper.threadedregions.TickRegions.TickRegionData, io.papermc.paper.threadedregions.TickRegions.TickRegionSectionData> regioniser;
    {
        this.regioniser = new io.papermc.paper.threadedregions.ThreadedRegionizer<>(
                (int)Math.max(1L, (8L * 16L * 16L) / (1L << (2 * (io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift())))),
                (1.0 / 6.0),
                Math.max(1, 8 / (1 << io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift())),
                1,
                io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift(),
                this,
                this.tickRegions
        );
    }
    public final io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData taskQueueRegionData = new io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData(this);

    public static final record PendingTeleport(Entity.EntityTreeNode rootVehicle, Vec3 to) {}
    private final it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<PendingTeleport> pendingTeleports = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();

    public long lastMapAutoSave = System.nanoTime();

    public void pushPendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            this.pendingTeleports.add(teleport);
        }
    }

    public boolean removePendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            return this.pendingTeleports.remove(teleport);
        }
    }

    public List<PendingTeleport> removeAllRegionTeleports() {
        final List<PendingTeleport> ret = new ArrayList<>();

        synchronized (this.pendingTeleports) {
            for (final java.util.Iterator<net.minecraft.server.level.ServerLevel.PendingTeleport> iterator = this.pendingTeleports.iterator(); iterator.hasNext(); ) {
                final PendingTeleport pendingTeleport = iterator.next();
                if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this, pendingTeleport.to())) {
                    ret.add(pendingTeleport);
                    iterator.remove();
                }
            }
        }

        return ret;
    }
    // Folia end - region threading
    // Folia start - region threading
    public void updateTickData() {
        this.tickData = new io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData(this, this.serverLevelData.getGameTime(), this.serverLevelData.getDayTime());
    }
    // Folia end - region threading

    // Paper start
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkSource().getChunkAtIfLoadedImmediately(chunkX, chunkZ) != null;
    }
    // Paper end

    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight dragonFight) {
        this.dragonFight = dragonFight;
    }

    public void setWeatherParameters(int clearTime, int weatherTime, boolean isRaining, boolean isThundering) {
        this.serverLevelData.setClearWeatherTime(clearTime);
        this.serverLevelData.setRainTime(weatherTime);
        this.serverLevelData.setThunderTime(weatherTime);
        this.serverLevelData.setRaining(isRaining, org.bukkit.event.weather.WeatherChangeEvent.Cause.COMMAND); // Paper - Add cause to Weather/ThunderChangeEvents
        this.serverLevelData.setThundering(isThundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.COMMAND); // Paper - Add cause to Weather/ThunderChangeEvents
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x, y, z, this.getChunkSource().randomState().sampler());
    }

    @Override // Folia - region threading
    public StructureManager structureManager() {
        return this.structureManager;
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return this.environmentAttributes;
    }

    public void tick(BooleanSupplier hasTimeLeft, io.papermc.paper.threadedregions.TickRegions.TickRegionData region) { // Folia - regionised ticking
        // Mili start - Global Entities Counter
        if (fun.bm.mili.config.modules.experiment.GlobalEntitiesCounter.enabled) {
            io.papermc.paper.threadedregions.RegionizedWorldData data = this.getCurrentWorldData();
            if (data != null && data.underGlobalEntitiesCounter) {
                fun.bm.mili.utils.EntitiesCounterUtil.tick(this, data.uniqueId, (ca.spottedleaf.moonrise.common.list.ReferenceList<Entity>) data.getLoadedEntities(), data.spawnChunkTracker);
            }
        }
        // Mili end - Global Entities Counter
        // Mili start - Cross Region Helper
        if (fun.bm.mili.config.modules.experiment.CrossRegionHelperConfig.enabled) {
            io.papermc.paper.threadedregions.RegionizedWorldData data = this.getCurrentWorldData();
            if (data != null) {
                fun.bm.mili.utils.CrossRegionHelper.onRegionTick(this, data);
            }
        }
        // Mili end - Cross Region Helper
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - regionised ticking
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        ProfilerFiller profilerFiller = Profiler.get();
        regionizedWorldData.setHandlingTick(true); // Folia - regionised ticking
        TickRateManager tickRateManager = this.tickRateManager();
        boolean runsNormally = tickRateManager.runsNormally();
        if (runsNormally) {
            profilerFiller.push("world border");
            //this.getWorldBorder().tick(); // Folia - regionised ticking
            // Folia start - regionised ticking
            // tick per-player world borders here so we can detect duplicates and avoid double ticking
            it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<WorldBorder> worldBorders = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
            for (ServerPlayer player : regionizedWorldData.getLocalPlayers()) {
                org.bukkit.craftbukkit.CraftWorldBorder worldBorder = (org.bukkit.craftbukkit.CraftWorldBorder)player.getBukkitEntity().getWorldBorder();
                if (worldBorder != null) {
                    worldBorders.add(worldBorder.getHandle());
                }
            }

            for (WorldBorder worldBorder : worldBorders) {
                worldBorder.tick();
            }
            // Folia end - regionised ticking
            profilerFiller.popPush("weather");
            //this.advanceWeatherCycle(); // Folia - regionised ticking
            profilerFiller.pop();
        }

        // Folia - region threading - move into tickSleep - handled by global region

        //this.updateSkyBrightness(); // Folia - region threading
        if (runsNormally) {
            this.tickTime();
        }

        profilerFiller.push("tickPending");
        if (!this.isDebug() && runsNormally) {
            long l = regionizedWorldData.getRedstoneGameTime(); // Folia - region threading
            profilerFiller.push("blockTicks");
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_TICK); try { // Folia - profiler
            regionizedWorldData.getBlockLevelTicks().tick(l, paperConfig().environment.maxBlockTicks, this::tickBlock); // Paper - configurable max block ticks // Folia - region ticking
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_TICK); } // Folia - profiler
            profilerFiller.popPush("fluidTicks");
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.FLUID_TICK); try { // Folia - profiler
            regionizedWorldData.getFluidLevelTicks().tick(l, paperConfig().environment.maxFluidTicks, this::tickFluid); // Paper - configurable max fluid ticks // Folia - region ticking
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.FLUID_TICK); } // Folia - profiler
            profilerFiller.pop();
        }

        profilerFiller.popPush("raid");
        if (runsNormally) {
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RAIDS_TICK); try { // Folia - profiler
            this.raids.tick(this);
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.RAIDS_TICK); } // Folia - profiler
        }

        profilerFiller.popPush("chunkSource");
        profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_PROVIDER_TICK); try { // Folia - profiler
        this.getChunkSource().tick(hasTimeLeft, true);
        } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.CHUNK_PROVIDER_TICK); } // Folia - profiler
        profilerFiller.popPush("blockEvents");
        if (runsNormally) {
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_EVENT_TICK); try { // Folia - profiler
            this.runBlockEvents();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.BLOCK_EVENT_TICK); } // Folia - profiler
        }

        regionizedWorldData.setHandlingTick(false); // Folia - regionised ticking
        profilerFiller.pop();
        boolean hasActiveTickets = true || !paperConfig().unsupportedSettings.disableWorldTickingWhenEmpty || this.chunkSource.hasActiveTickets(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players // Paper - restore this // Folia - unrestore this, we always need to tick empty worlds
        if (hasActiveTickets) {
            this.resetEmptyTime();
        }

        if (runsNormally) {
            this.emptyTime++;
        }

        if (this.emptyTime < 300) {
            profilerFiller.push("entities");
            if (this.dragonFight != null && runsNormally) {
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.DRAGON_FIGHT_TICK); try { // Folia - profiler
                if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this, this.dragonFight.origin)) { // Folia - region threading
                profilerFiller.push("dragonFight");
                this.dragonFight.tick();
                profilerFiller.pop();
                } else { // Folia start - region threading
                    // try to load dragon fight
                    ChunkPos fightCenter = new ChunkPos(this.dragonFight.origin);
                    this.moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAtLevel(
                            TicketType.UNKNOWN, fightCenter, ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                            null
                    );
                } // Folia end - region threading
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.DRAGON_FIGHT_TICK); } // Folia - profiler
            }

            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ACTIVATE_ENTITIES); try { // Folia - profiler
            if (dev.kaiijumc.kaiiju.KaiijuEntityLimits.enabled) regionizedWorldData.entityThrottler.tickLimiterStart(); // Kaiiju
            io.papermc.paper.entity.activation.ActivationRange.activateEntities(this); // Paper - EAR
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ACTIVATE_ENTITIES); } // Folia - profiler
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TICK); try { // Folia - profiler
            regionizedWorldData // Folia - regionised ticking
                .forEachTickingEntity( // Folia - regionised ticking
                    entity -> {
                        if (!entity.isRemoved()) {
                            if (!tickRateManager.isEntityFrozen(entity)) {
                                profilerFiller.push("checkDespawn");
                                entity.checkDespawn();
                                if (entity.isRemoved()) return; // Folia - region threading - if we despawned, DON'T TICK IT!
                                profilerFiller.pop();
                                if (true) { // Paper - rewrite chunk system
                                    Entity vehicle = entity.getVehicle();
                                    if (vehicle != null) {
                                        if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                                            return;
                                        }

                                        entity.stopRiding();
                                    }
                                    // Kaiiju start
                                    if (dev.kaiijumc.kaiiju.KaiijuEntityLimits.enabled) {
                                        dev.kaiijumc.kaiiju.KaiijuEntityThrottler.EntityThrottlerReturn throttle = regionizedWorldData.entityThrottler.tickLimiterShouldSkip(entity);
                                        if (throttle.remove && !entity.hasCustomName()) entity.remove(Entity.RemovalReason.DISCARDED);
                                        if (throttle.skip) return;
                                    }
                                    // Kaiiju end

                                    profilerFiller.push("tick");
                                    // Mili start - update suppression crash fix
                                    if (fun.bm.mili.config.modules.fixes.UpdateSuppressionCrashFixConfig.enabled) {
                                        try {
                                            this.guardEntityTick(this::tickNonPassenger, entity); // Mili changed
                                            // Leaves start - update suppression crash fix - for dragon dupe
                                        } catch (org.leavesmc.leaves.util.UpdateSuppressionException exception) {
                                            exception.provideLevel(this);
                                            exception.consume();
                                            // Leaves end - update suppression crash fix - for dragon dupe
                                        }
                                    } else {
                                        this.guardEntityTick(this::tickNonPassenger, entity);
                                    }
                                    // Mili end - update suppression crash fix
                                    profilerFiller.pop();
                                }
                            }
                        }
                    }
                );
                if (dev.kaiijumc.kaiiju.KaiijuEntityLimits.enabled) regionizedWorldData.entityThrottler.tickLimiterFinish(regionizedWorldData); // Kaiiju
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.ENTITY_TICK); } // Folia - profiler
            profilerFiller.popPush("blockEntities");
            profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY); try { // Folia - profiler
            this.tickBlockEntities();
            } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.TILE_ENTITY); } // Folia - profiler
            profilerFiller.pop();
        }

        profilerFiller.push("entityManagement");
        // Paper - rewrite chunk system
        profilerFiller.pop();
        profilerFiller.push("debugSynchronizers");
        // Mili start - instant neighbor updates
        if (regionizedWorldData.neighborUpdater instanceof net.minecraft.world.level.redstone.CollectingNeighborUpdater neighborUpdater) {
        if (this.debugSynchronizers.hasAnySubscriberFor(DebugSubscriptions.NEIGHBOR_UPDATES)) {
            neighborUpdater // Folia - region threading
                    .setDebugListener(blockPos -> this.debugSynchronizers.broadcastEventToTracking(blockPos, DebugSubscriptions.NEIGHBOR_UPDATES, blockPos));
        } else {
            neighborUpdater.setDebugListener(null); // Folia - region threading
        }
        }
        // Mili end - instant neighbor updates

        this.debugSynchronizers.tick(this.server.debugSubscribers());
        profilerFiller.pop();
        this.environmentAttributes().invalidateTickCache();
    }

    // Folia start - region threading
    public void tickSleep() {
        int i = this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            // Paper start - create time skip event - move up calculations
            final long newDayTime = this.levelData.getDayTime() + 24000L;
            org.bukkit.event.world.TimeSkipEvent event = new org.bukkit.event.world.TimeSkipEvent(
                this.getWorld(),
                org.bukkit.event.world.TimeSkipEvent.SkipReason.NIGHT_SKIP,
                (newDayTime - newDayTime % 24000L) - this.getDayTime()
            );
            // Paper end - create time skip event - move up calculations
            if (this.getGameRules().get(GameRules.ADVANCE_TIME)) {
                // Paper start - call time skip event if gamerule is enabled
                // long l = this.levelData.getDayTime() + 24000L; // Paper - diff on change to above - newDayTime
                // this.setDayTime(l - l % 24000L); // Paper - diff on change to above - event param
                if (event.callEvent()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
                // Paper end - call time skip event if gamerule is enabled
            }

            if (!event.isCancelled()) this.wakeUpAllPlayers(); // Paper - only wake up players if time skip event is not cancelled
            if (this.getGameRules().get(GameRules.ADVANCE_WEATHER) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }
    }
    // Folia end - region threading

    @Override
    public boolean shouldTickBlocksAt(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder holder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return holder != null && holder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    protected void tickTime() {
        if (this.tickTime) {
            io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - region threading
            long l = regionizedWorldData.getRedstoneGameTime() + 1L; // Folia - region threading
            regionizedWorldData.setRedstoneGameTime(l); // Folia - region threading
            Profiler.get().push("scheduledFunctions");
            //this.serverLevelData.getScheduledEvents().tick(this.server, l); // Folia - region threading - TODO any way to bring this in?
            Profiler.get().pop();
            if (false && this.getGameRules().get(GameRules.ADVANCE_TIME)) { // Folia - region threading
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }
        }
    }

    public void setDayTime(long time) {
        this.serverLevelData.setDayTime(time);
    }

    public long getDayCount() {
        return this.getDayTime() / 24000L;
    }

    public void tickCustomSpawners(boolean spawnEnemies) {
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        for (CustomSpawner customSpawner : this.customSpawners) {
            final int customSpawnerTimer = profiler.getOrCreateTimerAndStart(() -> "Misc Spawner: ".concat(io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(customSpawner.getClass().getName()))); try { // Folia - profiler
            customSpawner.tick(this, spawnEnemies);
            } finally { profiler.stopTimer(customSpawnerTimer); } // Folia - profiler
        }
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        // Folia start - region threading
        this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList()).forEach((ServerPlayer entityplayer) -> {
                    // Folia start - region threading
                    entityplayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> {
                        if (player.level() != ServerLevel.this || !player.isSleeping()) {
                            return;
                        }
                        player.stopSleepInBed(false, false);
                    }, null, 1L);
                }
        );
        // Folia end - region threading
    }

    // Paper start - optimise random ticking
    private final io.papermc.paper.threadedregions.util.SimpleThreadLocalRandomSource simpleRandom = io.papermc.paper.threadedregions.util.SimpleThreadLocalRandomSource.INSTANCE; // Folia - region threading

    private void optimiseRandomTick(final LevelChunk chunk, final int tickSpeed) {
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection((ServerLevel)(Object)this);
        final io.papermc.paper.threadedregions.util.SimpleThreadLocalRandomSource simpleRandom = this.simpleRandom; // Folia - region threading
        final boolean doubleTickFluids = !ca.spottedleaf.moonrise.common.PlatformHooks.get().configFixMC224294();

        final ChunkPos cpos = chunk.getPos();
        final int offsetX = cpos.x << 4;
        final int offsetZ = cpos.z << 4;

        for (int sectionIndex = 0, sectionsLen = sections.length; sectionIndex < sectionsLen; sectionIndex++) {
            final int offsetY = (sectionIndex + minSection) << 4;
            final LevelChunkSection section = sections[sectionIndex];
            final net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> states = section.states;
            if (!section.isRandomlyTickingBlocks()) {
                continue;
            }

            final ca.spottedleaf.moonrise.common.list.ShortList tickList = ((ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection)section).moonrise$getTickingBlockList();

            for (int i = 0; i < tickSpeed; ++i) {
                final int tickingBlocks = tickList.size();
                final int index = simpleRandom.nextInt() & ((16 * 16 * 16) - 1);

                if (index >= tickingBlocks) {
                    // most of the time we fall here
                    continue;
                }

                final int location = (int)tickList.getRaw(index) & 0xFFFF;
                final BlockState state = states.get(location);

                // do not use a mutable pos, as some random tick implementations store the input without calling immutable()!
                final BlockPos pos = new BlockPos((location & 15) | offsetX, ((location >>> (4 + 4)) & 15) | offsetY, ((location >>> 4) & 15) | offsetZ);

                state.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                if (doubleTickFluids) {
                    final FluidState fluidState = state.getFluidState();
                    if (fluidState.isRandomlyTicking()) {
                        fluidState.randomTick((ServerLevel)(Object)this, pos, simpleRandom);
                    }
                }
            }
        }

        return;
    }
    // Paper end - optimise random ticking

    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        final io.papermc.paper.threadedregions.util.SimpleThreadLocalRandomSource simpleRandom = this.simpleRandom; // Paper - optimise random ticking // Folia - region threading
        ChunkPos pos = chunk.getPos();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("iceandsnow");

        if (!this.paperConfig().environment.disableIceAndSnow) { // Paper - Option to disable ice and snow
        for (int i = 0; i < randomTickSpeed; i++) {
            if (simpleRandom.nextInt(48) == 0) {  // Paper - optimise random ticking
                this.tickPrecipitation(this.getBlockRandomPos(minBlockX, 0, minBlockZ, 15));
            }
        }
        } // Paper - Option to disable ice and snow

        profilerFiller.popPush("tickBlocks");
        if (randomTickSpeed > 0) {
            this.optimiseRandomTick(chunk, randomTickSpeed); // Paper - optimise random ticking
        }

        profilerFiller.pop();
    }

    public void tickThunder(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        boolean isRaining = this.isRaining();
        int minBlockX = pos.getMinBlockX();
        int minBlockZ = pos.getMinBlockZ();
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("thunder");
        if (!this.paperConfig().environment.disableThunder && isRaining && this.isThundering() && this.spigotConfig.thunderChance > 0 && this.random.nextInt(this.spigotConfig.thunderChance) == 0) { // Spigot // Paper - Option to disable thunder
            BlockPos blockPos = this.findLightningTargetAround(this.getBlockRandomPos(minBlockX, 0, minBlockZ, 15));
            if (this.isRainingAt(blockPos)) {
                DifficultyInstance currentDifficultyAt = this.getCurrentDifficultyAt(blockPos);
                boolean flag = this.getGameRules().get(GameRules.SPAWN_MOBS)
                    && this.random.nextDouble() < currentDifficultyAt.getEffectiveDifficulty() * this.paperConfig().entities.spawning.skeletonHorseThunderSpawnChance.or(0.01) // Paper - Configurable spawn chances for skeleton horses
                    && !this.getBlockState(blockPos.below()).is(BlockTags.LIGHTNING_RODS);
                if (flag) {
                    SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);
                    if (skeletonHorse != null) {
                        skeletonHorse.setTrap(true);
                        skeletonHorse.setAge(0);
                        skeletonHorse.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                        this.addFreshEntity(skeletonHorse, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                    }
                }

                LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(this, EntitySpawnReason.EVENT);
                if (lightningBolt != null) {
                    lightningBolt.snapTo(Vec3.atBottomCenterOf(blockPos));
                    lightningBolt.setVisualOnly(flag);
                    this.strikeLightning(lightningBolt, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
                }
            }
        }

        profilerFiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPos pos) {
        BlockPos heightmapPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockPos blockPos = heightmapPos.below();
        Biome biome = this.getBiome(heightmapPos).value();
        if (biome.shouldFreeze(this, blockPos)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockPos, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL, null); // CraftBukkit
        }

        if (this.isRaining()) {
            int i = this.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
            if (i > 0 && biome.shouldSnow(this, heightmapPos)) {
                BlockState blockState = this.getBlockState(heightmapPos);
                if (blockState.is(Blocks.SNOW)) {
                    int layersValue = blockState.getValue(SnowLayerBlock.LAYERS);
                    if (layersValue < Math.min(i, 8)) {
                        BlockState blockState1 = blockState.setValue(SnowLayerBlock.LAYERS, layersValue + 1);
                        Block.pushEntitiesUp(blockState, blockState1, this, heightmapPos);
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, heightmapPos, blockState1, Block.UPDATE_ALL, null); // CraftBukkit
                    }
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, heightmapPos, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL, null); // CraftBukkit
                }
            }

            Biome.Precipitation precipitationAt = biome.getPrecipitationAt(blockPos, this.getSeaLevel());
            if (precipitationAt != Biome.Precipitation.NONE) {
                BlockState blockState2 = this.getBlockState(blockPos);
                blockState2.getBlock().handlePrecipitation(blockState2, this, blockPos, precipitationAt);
            }
        }
    }

    public Optional<BlockPos> findLightningRod(BlockPos pos) {
        Optional<BlockPos> optional = this.getPoiManager()
            .findClosest(
                poiType -> poiType.is(PoiTypes.LIGHTNING_ROD),
                blockPos -> blockPos.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) - 1,
                pos,
                128,
                PoiManager.Occupancy.ANY
            );
        return optional.map(blockPos -> blockPos.above(1));
    }

    protected BlockPos findLightningTargetAround(BlockPos pos) {
        // Paper start - Add methods to find targets for lightning strikes
        return this.findLightningTargetAround(pos, false);
    }

    public BlockPos findLightningTargetAround(BlockPos pos, boolean returnNullWhenNoTarget) {
        // Paper end - Add methods to find targets for lightning strikes
        BlockPos heightmapPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        Optional<BlockPos> optional = this.findLightningRod(heightmapPos);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            AABB aabb = AABB.encapsulatingFullBlocks(heightmapPos, heightmapPos.atY(this.getMaxY() + 1)).inflate(3.0);
            List<LivingEntity> entitiesOfClass = this.getEntitiesOfClass(
                LivingEntity.class, aabb, entity ->  entity.isAlive() && this.canSeeSky(entity.blockPosition()) && !entity.isSpectator() // Paper - Fix lightning being able to hit spectators (MC-262422)
            );
            if (!entitiesOfClass.isEmpty()) {
                return entitiesOfClass.get(this.random.nextInt(entitiesOfClass.size())).blockPosition();
            } else {
                if (returnNullWhenNoTarget) return null; // Paper - Add methods to find targets for lightning strikes
                if (heightmapPos.getY() == this.getMinY() - 1) {
                    heightmapPos = heightmapPos.above(2);
                }

                return heightmapPos;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.getCurrentWorldData().isHandlingTick(); // Folia - regionised ticking
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
                Component component;
                if (this.sleepStatus.areEnoughSleeping(i)) {
                    component = Component.translatable("sleep.skipping_night");
                } else {
                    component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                for (ServerPlayer serverPlayer : this.players) {
                    serverPlayer.displayClientMessage(component, true);
                }
            }
        }
    }

    public void updateSleepingPlayerList() {
        // Folia start - region threading
        if (!io.papermc.paper.threadedregions.RegionizedServer.isGlobalTickThread()) {
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
                ServerLevel.this.updateSleepingPlayerList();
            });
            return;
        }
        // Folia end - region threading
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }
    }

    @Override
    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    public me.earthme.luminol.utils.FoliaServerWaypointManager getWaypointManager() { // Luminol - Restore waypoints
        return this.waypointManager;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        long l = 0L;
        float f = 0.0F;
        ChunkAccess chunk = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
        if (chunk != null) {
            l = chunk.getInhabitedTime();
            f = this.getMoonBrightness(pos);
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), l, f);
    }

    public float getMoonBrightness(BlockPos pos) {
        MoonPhase moonPhase = this.environmentAttributes.getValue(EnvironmentAttributes.MOON_PHASE, pos);
        return DimensionType.MOON_BRIGHTNESS_PER_PHASE[moonPhase.index()];
    }

    public void advanceWeatherCycle() { // Folia - region threading - public
        boolean isRaining = this.isRaining();
        if (this.canHaveWeather()) {
            if (this.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
                int clearWeatherTime = this.serverLevelData.getClearWeatherTime();
                int thunderTime = this.serverLevelData.getThunderTime();
                int rainTime = this.serverLevelData.getRainTime();
                boolean isThundering = this.levelData.isThundering();
                boolean isRaining1 = this.levelData.isRaining();
                if (clearWeatherTime > 0) {
                    clearWeatherTime--;
                    thunderTime = isThundering ? 0 : 1;
                    rainTime = isRaining1 ? 0 : 1;
                    isThundering = false;
                    isRaining1 = false;
                } else {
                    if (thunderTime > 0) {
                        if (--thunderTime == 0) {
                            isThundering = !isThundering;
                        }
                    } else if (isThundering) {
                        thunderTime = THUNDER_DURATION.sample(this.random);
                    } else {
                        thunderTime = THUNDER_DELAY.sample(this.random);
                    }

                    if (rainTime > 0) {
                        if (--rainTime == 0) {
                            isRaining1 = !isRaining1;
                        }
                    } else if (isRaining1) {
                        rainTime = RAIN_DURATION.sample(this.random);
                    } else {
                        rainTime = RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(thunderTime);
                this.serverLevelData.setRainTime(rainTime);
                this.serverLevelData.setClearWeatherTime(clearWeatherTime);
                this.serverLevelData.setThundering(isThundering, org.bukkit.event.weather.ThunderChangeEvent.Cause.NATURAL); // Paper - Add cause to Weather/ThunderChangeEvents
                this.serverLevelData.setRaining(isRaining1, org.bukkit.event.weather.WeatherChangeEvent.Cause.NATURAL); // Paper - Add cause to Weather/ThunderChangeEvents
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.oRainLevel != this.rainLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (isRaining != this.isRaining()) {
            if (isRaining) {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
        */
        ServerPlayer[] players = this.players.toArray(new ServerPlayer[0]); // Folia - region threading
        for (ServerPlayer player : players) { // Folia - region threading
            if (player.level() == this) {
                player.tickWeather();
            }
        }

        if (isRaining != this.isRaining()) {
            // Only send weather packets to those affected
            for (ServerPlayer player : players) { // Folia - region threading
                if (player.level() == this) {
                    player.setPlayerWeather((!isRaining ? org.bukkit.WeatherType.DOWNFALL : org.bukkit.WeatherType.CLEAR), false);
                }
            }
        }
        for (ServerPlayer player : players) { // Folia - region threading
            if (player.level() == this) {
                player.updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel);
            }
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        // CraftBukkit start
        this.serverLevelData.setRaining(false, org.bukkit.event.weather.WeatherChangeEvent.Cause.SLEEP); // Paper - Add cause to Weather/ThunderChangeEvents
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        this.serverLevelData.setThundering(false, org.bukkit.event.weather.ThunderChangeEvent.Cause.SLEEP); // Paper - Add cause to Weather/ThunderChangeEvents
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos pos, Fluid fluid) {
        BlockState blockState = this.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();
        if (fluidState.is(fluid)) {
            fluidState.tick(this, pos, blockState);
        }
        // Paper start - rewrite chunk system
        if ((++this.tickedBlocksOrFluids & 7L) != 0L) {
            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
        }
        // Paper end - rewrite chunk system

    }

    private void tickBlock(BlockPos pos, Block block) {
        BlockState blockState = this.getBlockState(pos);
        if (blockState.is(block)) {
            blockState.tick(this, pos, this.random);
        }
        // Paper start - rewrite chunk system
        if ((++this.tickedBlocksOrFluids & 7L) != 0L) {
            ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
        }
        // Paper end - rewrite chunk system

    }

    // Paper start - log detailed entity tick information
    // TODO replace with varhandle
    // Folia - region threading

    public static List<Entity> getCurrentlyTickingEntities() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }
    // Paper end - log detailed entity tick information

    public void tickNonPassenger(Entity entity) {
        // Paper start - log detailed entity tick information
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread("Cannot tick an entity off-main");
        try {
            // Folia - region threading
            // Paper end - log detailed entity tick information
        entity.setOldPosAndRot();
        ProfilerFiller profilerFiller = Profiler.get();
        entity.tickCount++;
        entity.totalEntityAge++; // Paper - age-like counter for all entities
        profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        profilerFiller.incrementCounter("tickNonPassenger");
        final boolean isActive = io.papermc.paper.entity.activation.ActivationRange.checkIfActive(entity); // Paper - EAR 2
        // Folia start - profiler
        final int timerId = isActive ? entity.getType().tickTimerId : entity.getType().inactiveTickTimerId;
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
        profiler.startTimer(timerId);
        LevelChunk levelChunk = entity.shouldTickHot() ? this.getChunkIfLoaded(entity.moonrise$getSectionX(),entity.moonrise$getSectionZ()) : null; // KioCG
        if (levelChunk != null) levelChunk.getChunkHot().startTicking(); try { // KioCG
        try {
        // Folia end - profiler
        // Luminol start - Entity portal-teleport speed fix
        if (isActive) { // Paper - EAR 2
            if (!(entity instanceof Player) && entity.teleportTickType == 2) { // Luminol - after portal compensate tick
                entity.tick();
                entity.tick();
                entity.teleportTickType = 0;
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity)) {
                    return;
                }
                // Luminol start - Portal rate limiter
                var worldData = entity.level().getCurrentWorldData();
                if (entity.portalProcess != null && worldData.isPortalTeleportationOutOfRate()) {
                    return;
                }
                // Luminol end
                if (entity.handlePortal()) {
                    worldData.portalRateThrottler.increase(); // Luminol - Portal rate limiter
                    return;
                }
            } else if (!(entity instanceof Player) && entity.teleportTickType == 1) { // Luminol - portal teleport only
                entity.teleportTickType++;
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity)) {
                    return;
                }
                // Luminol start - Portal rate limiter
                var worldData = entity.level().getCurrentWorldData();
                if (entity.portalProcess != null && worldData.isPortalTeleportationOutOfRate()) {
                    return;
                }
                // Luminol end
                if (entity.handlePortal()) {
                    worldData.portalRateThrottler.increase(); // Luminol - Portal rate limiter
                    return;
                }
            } else {
        entity.tick();
        // Folia start - region threading
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity)) {
            // removed from region while ticking
            return;
        }
        // Luminol start - Portal rate limiter
        var worldData = entity.level().getCurrentWorldData();
        if (entity.portalProcess != null && worldData.isPortalTeleportationOutOfRate()) {
            return;
        }
        // Luminol end
        if (entity.handlePortal()) {
            // portalled
            worldData.portalRateThrottler.increase(); // Luminol - Portal rate limiter
            return;
        }
        }
        // Luminol end - Entity portal-teleport speed fix
        // Folia end - region threading
        } else {entity.inactiveTick();} // Paper - EAR 2
        profilerFiller.pop();
        } finally { profiler.stopTimer(timerId); } // Folia - profiler
            } finally { if (levelChunk != null) levelChunk.getChunkHot().stopTickingAndCount(); } // KioCG

        for (Entity entity1 : entity.getPassengers()) {
            this.tickPassenger(entity, entity1, isActive); // Paper - EAR 2
        }
        // Paper start - log detailed entity tick information
        } finally {
            // Folia - region threading
        }
        // Paper end - log detailed entity tick information
    }

    private void tickPassenger(Entity ridingEntity, Entity passengerEntity, final boolean isActive) { // Paper - EAR 2
        if (passengerEntity.isRemoved() || passengerEntity.getVehicle() != ridingEntity) {
            passengerEntity.stopRiding();
        } else if (passengerEntity instanceof Player || this.getCurrentWorldData().hasEntityTickingEntity(passengerEntity)) { // Folia - region threading
            // Folia start - profiler
            final int timerId = isActive ? passengerEntity.getType().tickTimerId : passengerEntity.getType().inactiveTickTimerId;
            final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler();
            profiler.startTimer(timerId);
            LevelChunk levelChunk = !(passengerEntity instanceof Player) ? this.getChunkIfLoaded(passengerEntity.blockPosition()) : null; // KioCG
            if (levelChunk != null) levelChunk.getChunkHot().startTicking(); try { // KioCG
            try {
            // Folia end - profiler
            passengerEntity.setOldPosAndRot();
            passengerEntity.tickCount++;
            passengerEntity.totalEntityAge++; // Paper - age-like counter for all entities
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(passengerEntity.getType()).toString());
            profilerFiller.incrementCounter("tickPassenger");
            // Paper start - EAR 2
            if (isActive) {
            passengerEntity.rideTick();
            // Folia start - region threading
            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(passengerEntity)) {
                // removed from region while ticking
                return;
            }
            // Luminol start - Portal rate limiter
            var worldData = passengerEntity.level().getCurrentWorldData();
            if (passengerEntity.portalProcess != null && worldData.isPortalTeleportationOutOfRate()) {
                return;
            }
            // Luminol end
            if (passengerEntity.handlePortal()) {
                // portalled
                worldData.portalRateThrottler.increase(); // Luminol - Portal rate limiter
                return;
            }
            // Folia end - region threading
            } else {
                passengerEntity.setDeltaMovement(Vec3.ZERO);
                passengerEntity.inactiveTick();
                // copied from inside of if (isPassenger()) of passengerTick, but that ifPassenger is unnecessary
                ridingEntity.positionRider(passengerEntity);
            }
            // Paper end - EAR 2
            profilerFiller.pop();

            for (Entity entity : passengerEntity.getPassengers()) {
                this.tickPassenger(passengerEntity, entity, isActive); // Paper - EAR 2
            }
            } finally { profiler.stopTimer(timerId); } // Folia - profiler
            } finally { if (levelChunk != null) levelChunk.getChunkHot().stopTickingAndCount(); } // KioCG
        }
    }

    public void updateNeighboursOnBlockSet(BlockPos pos, BlockState state) {
        BlockState blockState = this.getBlockState(pos);
        Block block = blockState.getBlock();
        boolean flag = !state.is(block);
        if (flag) {
            state.affectNeighborsAfterRemoval(this, pos, false);
        }

        this.updateNeighborsAt(pos, blockState.getBlock());
        if (blockState.hasAnalogOutputSignal()) {
            this.updateNeighbourForOutputSignal(pos, block);
        }
    }

    @Override
    public boolean mayInteract(Entity entity, BlockPos pos) {
        return !(entity instanceof Player player && (this.server.isUnderSpawnProtection(this, pos, player) || !this.getWorldBorder().isWithinBounds(pos)));
    }

    // Paper start - Incremental chunk and player saving
    public void saveIncrementally(boolean doFull) {
        if (doFull) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(this.getWorld()));
        }

        if (doFull) {
            this.saveLevelData(false);
        }
        // chunk autosave is already called by the ChunkSystem during unload processing (ChunkMap#processUnloads)
        // Copied from save()
        // CraftBukkit start - moved from MinecraftServer.saveChunks
        if (doFull) { // Paper
            this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
            this.levelStorageAccess.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        }
        // CraftBukkit end
    }
    // Paper end - Incremental chunk and player saving

    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave) {
        // Paper start - add close param
        this.save(progress, flush, skipSave, false);
    }
    public void save(@Nullable ProgressListener progress, boolean flush, boolean skipSave, boolean close) {
        // Paper end - add close param
        ServerChunkCache chunkSource = this.getChunkSource();
        if (!skipSave) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(this.getWorld())); // CraftBukkit
            if (progress != null) {
                progress.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData(flush);
            if (progress != null) {
                progress.progressStage(Component.translatable("menu.savingChunks"));
            }

            if (!close) { chunkSource.save(flush); } // Paper - add close param
            // Paper - rewrite chunk system
        }
        // Paper start - add close param
        if (close) {
            try {
                chunkSource.close(!skipSave);
            } catch (IOException never) {
                throw new RuntimeException(never);
            }
        }
        // Paper end - add close param

        // Folia - move into saveLevelData
    }

    public void saveLevelData(boolean join) { // Folia - public
        if (this.dragonFight != null) {
            this.serverLevelData.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
        }
        // Folia start - moved into saveLevelData
        ServerLevel serverLevel1 = this;

        this.serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
        this.levelStorageAccess.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // Folia end - moved into saveLevelData

        DimensionDataStorage dataStorage = this.getChunkSource().getDataStorage();
        if (join) {
            dataStorage.saveAndJoin();
        } else {
            dataStorage.scheduleSave();
        }
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();
        this.getEntities(typeTest, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output) {
        this.getEntities(typeTest, predicate, output, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> typeTest, Predicate<? super T> predicate, List<? super T> output, int maxResults) {
        this.getEntities().get(typeTest, entity -> {
            if (predicate.test(entity)) {
                output.add(entity);
                if (output.size() >= maxResults) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int maxResults) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (predicate.test(serverPlayer)) {
                list.add(serverPlayer);
                if (list.size() >= maxResults) {
                    return list;
                }
            }
        }

        return list;
    }

    // Folia start - region threading
    @Nullable
    public ServerPlayer getRandomLocalPlayer() {
        List<ServerPlayer> list = this.getLocalPlayers();
        list = new java.util.ArrayList<>(list);
        list.removeIf((ServerPlayer player) -> {
            return !player.isAlive();
        });

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }
    // Folia end - region threading

    public @Nullable ServerPlayer getRandomPlayer() {
        List<ServerPlayer> players = this.getPlayers(LivingEntity::isAlive);
        return players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.@Nullable SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addWithUUID(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addWithUUID(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.@Nullable SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringTeleport(Entity entity) {
        // CraftBukkit start
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        this.addDuringTeleport(entity, null);
    }

    public void addDuringTeleport(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.@Nullable SpawnReason reason) {
        // CraftBukkit end
        if (entity instanceof ServerPlayer serverPlayer) {
            this.addPlayer(serverPlayer);
        } else {
            this.addEntity(entity, reason); // CraftBukkit
        }
    }

    public void addNewPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    public void addRespawnedPlayer(ServerPlayer player) {
        this.addPlayer(player);
    }

    private void addPlayer(ServerPlayer player) {
        Entity entity = this.getEntity(player.getUUID());
        if (entity != null) {
            LOGGER.warn("Force-added player with duplicate UUID {}", player.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer)entity, Entity.RemovalReason.DISCARDED);
        }

        this.moonrise$getEntityLookup().addNewEntity(player); // Paper - rewrite chunk system
    }

    // CraftBukkit start
    private boolean addEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.@Nullable SpawnReason spawnReason) {
        org.spigotmc.AsyncCatcher.catchOp("entity add"); // Spigot
        entity.generation = false; // Paper - Don't fire sync event during generation; Reset flag if it was added during a ServerLevel generation process
        // Paper start - extra debug info
        if (entity.valid) {
            MinecraftServer.LOGGER.error("Attempted Double World add on {}", entity, new Throwable());
            return true;
        }
        // Paper end - extra debug info
        if (entity.spawnReason == null) entity.spawnReason = spawnReason; // Paper - Entity#getEntitySpawnReason
        if (entity.isRemoved()) {
            // LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(entity.getType())); // CraftBukkit - remove warning
            return false;
        } else {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity && itemEntity.getItem().isEmpty()) return false; // Paper - Prevent empty items from being added
            // Paper start - capture all item additions to the world
            if (this.getCurrentWorldData().captureDrops != null && entity instanceof net.minecraft.world.entity.item.ItemEntity) { // Folia - region threading
                this.getCurrentWorldData().captureDrops.add((net.minecraft.world.entity.item.ItemEntity) entity); // Folia - region threading
                return true;
            }
            // Paper end - capture all item additions to the world
            // SPIGOT-6415: Don't call spawn event when reason is null. For example when an entity teleports to a new world.
            if (spawnReason != null && !org.bukkit.craftbukkit.event.CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end

            return this.moonrise$getEntityLookup().addNewEntity(entity); // Paper - rewrite chunk system
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        // CraftBukkit start
        return this.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        if (entity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.moonrise$getEntityLookup()::hasEntity)) { // Paper - rewrite chunk system
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity, reason); // CraftBukkit
            return true;
        }
    }

    public void unload(LevelChunk chunk) {
        // Spigot start
        for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof net.minecraft.world.Container) {
                // Paper start - this area looks like it can load chunks, change the behavior
                // chests for example can apply physics to the world
                // so instead we just change the active container and call the event
                for (org.bukkit.entity.HumanEntity human : Lists.newArrayList(((net.minecraft.world.Container) blockEntity).getViewers())) {
                    ((org.bukkit.craftbukkit.entity.CraftHumanEntity) human).getHandle().closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                }
                // Paper end - this area looks like it can load chunks, change the behavior
            }
        }
        // Spigot end
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
        this.debugSynchronizers.dropChunk(chunk.getPos());
    }

    public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        player.remove(reason, null); // CraftBukkit - add Bukkit remove cause
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause cause) {
        org.bukkit.event.weather.LightningStrikeEvent lightning = org.bukkit.craftbukkit.event.CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        // Gale start - SportPaper - reduce block destruction packet allocations
        var players = this.server.getPlayerList().getPlayers();

        if (players.isEmpty()) {
            return;
        }

        ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(breakerId, pos, progress);
        // Gale end - SportPaper - reduce block destruction packet allocations
        // CraftBukkit start
        Player breakerPlayer = null;
        Entity entity = this.getEntity(breakerId);
        if (entity instanceof Player) breakerPlayer = (Player) entity;
        // CraftBukkit end

        // Paper start - Add BlockBreakProgressUpdateEvent
        // If a plugin is using this method to send destroy packets for a client-side only entity id, no block progress occurred on the server.
        // Hence, do not call the event.
        if (entity != null) {
            float progressFloat = Mth.clamp(progress, 0, 10) / 10.0f;
            org.bukkit.craftbukkit.block.CraftBlock bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(this, pos);
            new io.papermc.paper.event.block.BlockBreakProgressUpdateEvent(bukkitBlock, progressFloat, entity.getBukkitEntity())
                .callEvent();
        }
        // Paper end - Add BlockBreakProgressUpdateEvent
        for (ServerPlayer serverPlayer : players) { // Gale - SportPaper - reduce block destruction packet allocations
            if (serverPlayer.level() == this && serverPlayer.getId() != breakerId) {
                double d = pos.getX() - serverPlayer.getX();
                double d1 = pos.getY() - serverPlayer.getY();
                double d2 = pos.getZ() - serverPlayer.getZ();
                // CraftBukkit start
                if (breakerPlayer != null && !serverPlayer.getBukkitEntity().canSee(breakerPlayer.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end
                if (d * d + d1 * d1 + d2 * d2 < 1024.0) {
                    serverPlayer.connection.send(packet); // Gale - SportPaper - reduce block destruction packet allocations
                }
            }
        }
    }

    @Override
    public void playSeededSound(
        @Nullable Entity entity, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed
    ) {
        this.server
            .getPlayerList()
            .broadcast(
                entity instanceof Player player ? player : null,
                x,
                y,
                z,
                sound.value().getRange(volume),
                this.dimension(),
                new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed)
            );
    }

    @Override
    public void playSeededSound(
        @Nullable Entity entity, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed
    ) {
        this.server
            .getPlayerList()
            .broadcast(
                entity instanceof Player player ? player : null,
                sourceEntity.getX(),
                sourceEntity.getY(),
                sourceEntity.getZ(),
                sound.value().getRange(volume),
                this.dimension(),
                new ClientboundSoundEntityPacket(sound, source, sourceEntity, volume, pitch, seed)
            );
    }

    @Override
    public void globalLevelEvent(int id, BlockPos pos, int data) {
        if (this.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().getPlayers().forEach(player -> {
                Vec3 vec31;
                if (player.level() == this) {
                    Vec3 vec3 = Vec3.atCenterOf(pos);
                    if (player.distanceToSqr(vec3) < Mth.square(32)) {
                        vec31 = vec3;
                    } else {
                        Vec3 vec32 = vec3.subtract(player.position()).normalize();
                        vec31 = player.position().add(vec32.scale(32.0));
                    }
                } else {
                    vec31 = player.position();
                }

                player.connection.send(new ClientboundLevelEventPacket(id, BlockPos.containing(vec31), data, true));
            });
        } else {
            this.levelEvent(null, id, pos, data);
        }
    }

    @Override
    public void levelEvent(@Nullable Entity entity, int type, BlockPos pos, int data) {
        this.server
            .getPlayerList()
            .broadcast(
                entity instanceof Player player ? player : null,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                64.0, // Paper - diff on change (the 64.0 distance is used as defaults for sound ranges in spigot config for ender dragon, end portal and wither)
                this.dimension(),
                new ClientboundLevelEventPacket(type, pos, data, false)
            );
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
        // Paper start - Prevent GameEvents being fired from unloaded chunks
        if (this.getChunkIfLoadedImmediately((Mth.floor(pos.x) >> 4), (Mth.floor(pos.z) >> 4)) == null) {
            return;
        }
        // Paper end - Prevent GameEvents being fired from unloaded chunks
        this.gameEventDispatcher.post(gameEvent, pos, context);
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - region threading
        if (false && this.isUpdatingNavigations) { // Folia - region threading
            String string = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(pos);
        regionizedWorldData.pathTypesByPosCache.invalidate(pos); // Folia - region threading
        if (this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape collisionShape = oldState.getCollisionShape(this, pos);
        VoxelShape collisionShape1 = newState.getCollisionShape(this, pos);
        if (Shapes.joinIsNotEmpty(collisionShape, collisionShape1, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList<>();

            try { // Paper - catch CME see below why
            for (java.util.Iterator<Mob> iterator = regionizedWorldData.getNavigatingMobs(); iterator.hasNext();) { // Folia - region threading
                Mob mob = iterator.next(); // Folia - region threading
                PathNavigation navigation = mob.getNavigation();
                if (navigation.shouldRecomputePath(pos)) {
                    list.add(navigation);
                }
            }
            // Paper start - catch CME see below why
            } catch (final java.util.ConcurrentModificationException concurrentModificationException) {
                // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                // In this case we just run the update again across all the iterators as the chunk will then be loaded
                // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                this.sendBlockUpdated(pos, oldState, newState, flags);
                return;
            }
            // Paper end - catch CME see below why

            try {
                //this.isUpdatingNavigations = true; // Folia - region threading

                for (PathNavigation pathNavigation : list) {
                    pathNavigation.recomputePath();
                }
            } finally {
                //this.isUpdatingNavigations = false; // Folia - region threading
            }
        }
        } // Paper - option to disable pathfinding updates
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block) {
        // CraftBukkit start
        if (this.getCurrentWorldData().populating) { // Folia - region threading
            return;
        }
        // CraftBukkit end
        if (this.getCurrentWorldData().captureBlockStates) { return; } // Paper - Cancel all physics during placement // Folia - region threading
        this.updateNeighborsAt(pos, block, ExperimentalRedstoneUtils.initialOrientation(this, null, null));
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block, @Nullable Orientation orientation) {
        if (this.getCurrentWorldData().captureBlockStates) { return; } // Paper - Cancel all physics during placement // Folia - region threading
        this.getCurrentWorldData().neighborUpdater.updateNeighborsAtExceptFromFacing(pos, block, null, orientation); // Folia - region threading
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, Direction facing, @Nullable Orientation orientation) {
        this.getCurrentWorldData().neighborUpdater.updateNeighborsAtExceptFromFacing(pos, block, facing, orientation); // Folia - region threading
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, @Nullable Orientation orientation) {
        this.getCurrentWorldData().neighborUpdater.neighborChanged(pos, block, orientation); // Folia - region threading
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        this.getCurrentWorldData().neighborUpdater.neighborChanged(state, pos, block, orientation, movedByPiston); // Folia - region threading
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte state) {
        this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundEntityEventPacket(entity, state));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
        this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundDamageEventPacket(entity, damageSource));
    }

    @Override
    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public void explode(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        WeightedList<ExplosionParticleInfo> blockParticles,
        Holder<SoundEvent> explosionSound
    ) {
        // CraftBukkit start
        this.explode0(source, damageSource, damageCalculator, x, y, z, radius, fire, explosionInteraction, smallExplosionParticles, largeExplosionParticles, blockParticles, explosionSound);
    }

    public ServerExplosion explode0(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        WeightedList<ExplosionParticleInfo> blockParticles,
        Holder<SoundEvent> explosionSound
    ) {
        return this.explode0(source, damageSource, damageCalculator, x, y, z, radius, fire, explosionInteraction, smallExplosionParticles, largeExplosionParticles, blockParticles, explosionSound, null);
    }
    public ServerExplosion explode0(
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Level.ExplosionInteraction explosionInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        WeightedList<ExplosionParticleInfo> blockParticles,
        Holder<SoundEvent> explosionSound,
        java.util.function.@Nullable Consumer<ServerExplosion> configurator
    ) {
        // CraftBukkit end
        Explosion.BlockInteraction blockInteraction = switch (explosionInteraction) {
            case NONE -> Explosion.BlockInteraction.KEEP;
            case BLOCK -> this.getDestroyType(GameRules.BLOCK_EXPLOSION_DROP_DECAY);
            case MOB -> this.getGameRules().get(GameRules.MOB_GRIEFING)
                ? this.getDestroyType(GameRules.MOB_EXPLOSION_DROP_DECAY)
                : Explosion.BlockInteraction.KEEP;
            case TNT -> this.getDestroyType(GameRules.TNT_EXPLOSION_DROP_DECAY);
            case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
            case STANDARD -> Explosion.BlockInteraction.DESTROY; // CraftBukkit - handle custom explosion type
        };
        Vec3 vec3 = new Vec3(x, y, z);
        ServerExplosion serverExplosion = new ServerExplosion(this, source, damageSource, damageCalculator, vec3, radius, fire, blockInteraction);
        if (configurator != null) configurator.accept(serverExplosion);// Paper - Allow explosions to damage source
        int i = serverExplosion.explode();
        // CraftBukkit start
        if (serverExplosion.wasCanceled) {
            return serverExplosion;
        }
        // CraftBukkit end
        ParticleOptions particleOptions = serverExplosion.isSmall() ? smallExplosionParticles : largeExplosionParticles;

        for (ServerPlayer serverPlayer : this.getLocalPlayers()) { // Folia - region thraeding
            if (serverPlayer.distanceToSqr(vec3) < 4096.0) {
                Optional<Vec3> optional = Optional.ofNullable(serverExplosion.getHitPlayers().get(serverPlayer));
                serverPlayer.connection.send(new ClientboundExplodePacket(vec3, radius, i, optional, particleOptions, explosionSound, blockParticles));
            }
        }

        return serverExplosion; // CraftBukkit
    }

    private Explosion.BlockInteraction getDestroyType(GameRule<Boolean> decayGameRule) {
        return this.getGameRules().get(decayGameRule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    @Override
    public void blockEvent(BlockPos pos, Block block, int eventId, int eventParam) {
        this.getCurrentWorldData().pushBlockEvent(new BlockEventData(pos, block, eventId, eventParam)); // Folia - regionised ticking
    }

    private void runBlockEvents() {
        List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64); // Folia - regionised ticking

        // Folia start - regionised ticking
        io.papermc.paper.threadedregions.RegionizedWorldData worldRegionData = this.getCurrentWorldData();
        BlockEventData blockEventData;
        while ((blockEventData = worldRegionData.removeFirstBlockEvent()) != null) {
            // Folia end - regionised ticking
            if (this.shouldTickBlocksAt(blockEventData.pos())) {
                if (this.doBlockEvent(blockEventData)) {
                    this.server
                        .getPlayerList()
                        .broadcast(
                            null,
                            blockEventData.pos().getX(),
                            blockEventData.pos().getY(),
                            blockEventData.pos().getZ(),
                            64.0,
                            this.dimension(),
                            new ClientboundBlockEventPacket(blockEventData.pos(), blockEventData.block(), blockEventData.paramA(), blockEventData.paramB())
                        );
                }
            } else {
                blockEventsToReschedule.add(blockEventData); // Folia - regionised ticking
            }
        }

        worldRegionData.pushBlockEvents(blockEventsToReschedule); // Folia - regionised ticking
    }

    private boolean doBlockEvent(BlockEventData event) {
        BlockState blockState = this.getBlockState(event.pos());
        return blockState.is(event.block()) && blockState.triggerEvent(this, event.pos(), event.paramA(), event.paramB());
    }

    @Override
    public LevelTicks<Block> getBlockTicks() {
        return this.getCurrentWorldData().getBlockLevelTicks(); // Folia - region ticking
    }

    @Override
    public LevelTicks<Fluid> getFluidTicks() {
        return this.getCurrentWorldData().getFluidLevelTicks(); // Folia - region ticking
    }

    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(
        T options, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed
    ) {
        return this.sendParticlesSource(null, options, false, false, x, y, z, count, xDist, yDist, zDist, speed); // CraftBukkit - visibility api support
    }

    public <T extends ParticleOptions> int sendParticles(
        T options, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, int count, double xDist, double yDist, double zDist, double speed
    ) {
        // Paper start - visibility api support
        return this.sendParticlesSource(null, options, overrideLimiter, alwaysShow, x, y, z, count, xDist, yDist, zDist, speed);
    }
    public <T extends ParticleOptions> int sendParticlesSource(
        @Nullable Entity sender,
        T options,
        boolean overrideLimiter,
        boolean alwaysShow,
        double x,
        double y,
        double z,
        int count,
        double xDist,
        double yDist,
        double zDist,
        double speed
    ) {
        return sendParticlesSource(this.getLocalPlayers(), sender, options, overrideLimiter, alwaysShow, x, y, z, count, xDist, yDist, zDist, speed); // Folia - region threading
    }
    public <T extends ParticleOptions> int sendParticlesSource(
        List<ServerPlayer> receivers,
        @Nullable Entity sender,
        T options,
        boolean overrideLimiter,
        boolean alwaysShow,
        double x,
        double y,
        double z,
        int count,
        double xDist,
        double yDist,
        double zDist,
        double speed
    ) {
        // Paper end - visibility api support
        ClientboundLevelParticlesPacket clientboundLevelParticlesPacket = new ClientboundLevelParticlesPacket(
            options, overrideLimiter, alwaysShow, x, y, z, (float)xDist, (float)yDist, (float)zDist, (float)speed, count
        );
        int i = 0;

        for (int i1 = 0; i1 < receivers.size(); i1++) { // Paper - particle API
            ServerPlayer serverPlayer = receivers.get(i1); // Paper - particle API
            if (sender != null && !serverPlayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit
            if (this.sendParticles(serverPlayer, overrideLimiter, x, y, z, clientboundLevelParticlesPacket)) {
                i++;
            }
        }

        return i;
    }

    public <T extends ParticleOptions> boolean sendParticles(
        ServerPlayer player,
        T particle,
        boolean overrideLimiter,
        boolean alwaysShow,
        double posX,
        double posY,
        double posZ,
        int count,
        double xDist,
        double yDist,
        double zDist,
        double maxSpeed
    ) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(
            particle, overrideLimiter, alwaysShow, posX, posY, posZ, (float)xDist, (float)yDist, (float)zDist, (float)maxSpeed, count
        );
        return this.sendParticles(player, overrideLimiter, posX, posY, posZ, packet);
    }

    private boolean sendParticles(ServerPlayer player, boolean overrideLimiter, double x, double y, double z, Packet<?> packet) {
        if (player.level() != this) {
            return false;
        } else {
            BlockPos blockPos = player.blockPosition();
            if (blockPos.closerToCenterThan(new Vec3(x, y, z), overrideLimiter ? 512.0 : 32.0)) {
                player.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public @Nullable Entity getEntity(int id) {
        return this.getEntities().get(id);
    }

    @Override
    public @Nullable Entity getEntityInAnyDimension(UUID id) {
        Entity entity = this.getEntity(id);
        if (entity != null) {
            return entity;
        } else {
            for (ServerLevel serverLevel : this.getServer().getAllLevels()) {
                if (serverLevel != this) {
                    Entity entity1 = serverLevel.getEntity(id);
                    if (entity1 != null) {
                        return entity1;
                    }
                }
            }

            return null;
        }
    }

    @Override
    public @Nullable Player getPlayerInAnyDimension(UUID id) {
        return this.getServer().getPlayerList().getPlayer(id);
    }

    @Deprecated
    public @Nullable Entity getEntityOrPart(int id) {
        Entity entity = this.getEntities().get(id);
        return entity != null ? entity : this.dragonParts.get((long)id); // Folia - diff on change
    }

    @Override
    public Collection<EnderDragonPart> dragonParts() {
        return this.dragonParts.values(); // Folia - diff on change
    }

    public @Nullable BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipKnownStructures) {
        if (!this.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureTag);
            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource()
                    .getGenerator()
                    .findNearestMapStructure(this, optional.get(), pos, radius, skipKnownStructures);
                return pair != null ? pair.getFirst() : null;
            }
        }
    }

    public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        Predicate<Holder<Biome>> biomePredicate, BlockPos pos, int radius, int horizontalStep, int verticalStep
    ) {
        return this.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .findClosestBiome3d(pos, radius, horizontalStep, verticalStep, biomePredicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public WorldBorder getWorldBorder() {
        WorldBorder worldBorder = this.getDataStorage().computeIfAbsent(WorldBorder.TYPE);
        worldBorder.applyInitialSettings(this.levelData.getGameTime());
        return worldBorder;
    }

    @Override
    public RecipeManager recipeAccess() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Override
    public @Nullable MapItemSavedData getMapData(MapId mapId) {
        // Paper start - Call missing map initialize event and set id
        final DimensionDataStorage storage = this.getServer().overworld().getDataStorage();

        // Folia start - region threading
        MapItemSavedData ret = storage.read(MapItemSavedData.type(mapId), (MapItemSavedData fromDisk) -> {
            if (fromDisk == null) {
                return;
            }

            fromDisk.id = mapId;
            new org.bukkit.event.server.MapInitializeEvent(fromDisk.mapView).callEvent();
        });

        if (ret != null) {
            ret.id = mapId;
        }

        return ret;
        // Folia end - region threading
        // Paper end - Call missing map initialize event and set id
    }

    public void setMapData(MapId mapId, MapItemSavedData data) {
        // CraftBukkit start
        data.id = mapId;
        org.bukkit.event.server.MapInitializeEvent event = new org.bukkit.event.server.MapInitializeEvent(data.mapView);
        event.callEvent();
        // CraftBukkit end
        this.getServer().overworld().getDataStorage().set(MapItemSavedData.type(mapId), data);
    }

    public MapId getFreeMapId() {
        return this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.TYPE).getNextMapId();
    }

    @Override
    public void setRespawnData(LevelData.RespawnData respawnData) {
        // Paper start
        if (!this.serverLevelData.getRespawnData().positionEquals(respawnData)) {
            org.bukkit.Location previousLocation = this.getWorld().getSpawnLocation();
            this.serverLevelData.setSpawn(respawnData);
            this.server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket(respawnData), this.dimension());
            this.server.updateEffectiveRespawnData();
            new org.bukkit.event.world.SpawnChangeEvent(this.getWorld(), previousLocation).callEvent();
        }
        if (this.server.overworld().serverLevelData.respawnDimension != this.dimension()) {
            this.server.overworld().serverLevelData.respawnDimension = this.dimension();
            this.server.updateEffectiveRespawnData();
        }
        // Paper end
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return this.getServer().getRespawnData();
    }

    public LongSet getForceLoadedChunks() {
        return this.chunkSource.getForceLoadedChunks();
    }

    public boolean setChunkForced(int chunkX, int chunkZ, boolean add) {
        boolean flag = this.chunkSource.updateChunkForced(new ChunkPos(chunkX, chunkZ), add);
        if (add && flag) {
            //this.getChunk(chunkX, chunkZ); // Folia - region threading - we must let the chunk load asynchronously
        }

        return flag;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    // Leaves start - fakeplayer skip
    public List<ServerPlayer> realPlayers() {
        return this.realPlayers;
    }
    // Leaves end - fakeplayer skip

    @Override
    public void updatePOIOnBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(oldState);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(newState);
        if (!Objects.equals(optional, optional1)) {
            BlockPos blockPos = pos.immutable();
            final java.util.concurrent.Executor scheduler = this.regionizedBlockableEventLoop.asExecutor(pos, true); // Luminol - Instant POI updates
            optional.ifPresent(holder -> /*io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(this, blockPos.getX() >> 4, blockPos.getZ() >> 4,*/scheduler.execute( () -> { // Folia - region threading // Luminol - Instant POI updates
                this.getPoiManager().remove(blockPos);
                this.debugSynchronizers.dropPoi(blockPos);
            }));
            optional1.ifPresent(holder -> /*io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueChunkTask(this, blockPos.getX() >> 4, blockPos.getZ() >> 4,*/scheduler.execute( () -> { // Folia - region threading // Luminol - Instant POI updates
                // Paper start - Remove stale POIs
                if (optional.isEmpty() && this.getPoiManager().exists(blockPos, ignored -> true)) {
                    this.getPoiManager().remove(blockPos);
                }
                // Paper end - Remove stale POIs
                PoiRecord poiRecord = this.getPoiManager().add(blockPos, (Holder<PoiType>)holder);
                if (poiRecord != null) {
                    this.debugSynchronizers.registerPoi(poiRecord);
                }
            }));
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos pos) {
        return this.isCloseToVillage(pos, 1);
    }

    public boolean isVillage(SectionPos pos) {
        return this.isVillage(pos.center());
    }

    public boolean isCloseToVillage(BlockPos pos, int sections) {
        return sections <= 6 && this.sectionsToVillage(SectionPos.of(pos)) <= sections;
    }

    public int sectionsToVillage(SectionPos pos) {
        return this.getPoiManager().sectionsToVillage(pos);
    }

    public Raids getRaids() {
        return this.raids;
    }

    public @Nullable Raid getRaidAt(BlockPos pos) {
        return this.raids.getNearbyRaid(this, pos, 9216); // Folia - make raids thread-safe - add ServerLevel param
    }

    public boolean isRaided(BlockPos pos) {
        return this.getRaidAt(pos) != null;
    }

    public void onReputationEvent(ReputationEventType type, Entity target, ReputationEventHandler host) {
        host.onReputationEventFrom(type, target);
    }

    public void saveDebugReport(Path path) throws IOException {
        ChunkMap chunkMap = this.getChunkSource().chunkMap;

        try (Writer bufferedWriter = Files.newBufferedWriter(path.resolve("stats.txt"))) {
            bufferedWriter.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkMap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState lastSpawnState = this.getChunkSource().getLastSpawnState();
            if (lastSpawnState != null) {
                for (Entry<MobCategory> entry : lastSpawnState.getMobCategoryCounts().object2IntEntrySet()) {
                    bufferedWriter.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", entry.getKey().getName(), entry.getIntValue()));
                }
            }

            bufferedWriter.write(String.format(Locale.ROOT, "entities: %s\n", this.moonrise$getEntityLookup().getDebugInfo()));  // Paper - rewrite chunk system
            //bufferedWriter.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size())); // Folia - region threading
            bufferedWriter.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            bufferedWriter.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            bufferedWriter.write("distance_manager: " + chunkMap.getDistanceManager().getDebugStatus() + "\n");
            bufferedWriter.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashReport = new CrashReport("Level dump", new Exception("dummy"));
        this.fillReportDetails(crashReport);

        try (Writer bufferedWriter1 = Files.newBufferedWriter(path.resolve("example_crash.txt"))) {
            bufferedWriter1.write(crashReport.getFriendlyReport(ReportType.TEST));
        }

        Path path1 = path.resolve("chunks.csv");

        try (Writer bufferedWriter2 = Files.newBufferedWriter(path1)) {
            //chunkMap.dumpChunks(bufferedWriter2); // Paper - rewrite chunk system
        }

        Path path2 = path.resolve("entity_chunks.csv");

        try (Writer bufferedWriter3 = Files.newBufferedWriter(path2)) {
            //this.entityManager.dumpSections(bufferedWriter3); // Paper - rewrite chunk system
        }

        Path path3 = path.resolve("entities.csv");

        try (Writer bufferedWriter4 = Files.newBufferedWriter(path3)) {
            dumpEntities(bufferedWriter4, this.getEntities().getAll());
        }

        Path path4 = path.resolve("block_entities.csv");

        try (Writer bufferedWriter5 = Files.newBufferedWriter(path4)) {
            this.dumpBlockEntityTickers(bufferedWriter5);
        }
    }

    private static void dumpEntities(Writer writer, Iterable<Entity> entities) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("uuid")
            .addColumn("type")
            .addColumn("alive")
            .addColumn("display_name")
            .addColumn("custom_name")
            .build(writer);

        for (Entity entity : entities) {
            Component customName = entity.getCustomName();
            Component displayName = entity.getDisplayName();
            csvOutput.writeRow(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.isAlive(),
                displayName.getString(),
                customName != null ? customName.getString() : null
            );
        }
    }

    private void dumpBlockEntityTickers(Writer output) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(output);

        for (TickingBlockEntity tickingBlockEntity : (Iterable<? extends net.minecraft.world.level.block.entity.TickingBlockEntity>)null) { // Folia - region threading
            BlockPos pos = tickingBlockEntity.getPos();
            if (me.earthme.luminol.config.modules.optimizations.LeavesSleepingBlockEntityConfig.enabled && pos == null) pos = BlockPos.ZERO; // Leaves - Lithium Sleeping Block Entity
            csvOutput.writeRow(pos.getX(), pos.getY(), pos.getZ(), tickingBlockEntity.getType());
        }
    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox boundingBox) {
        this.getCurrentWorldData().removeIfBlockEvents(blockEventData -> boundingBox.isInside(blockEventData.pos())); // Folia - regionised ticking
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    @Override
    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenOptions().seed(); // CraftBukkit
    }

    public @Nullable EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(
            Locale.ROOT,
            "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s",
            this.players.size(),
            this.moonrise$getEntityLookup().getDebugInfo(), // Paper - rewrite chunk system
            getTypeCount(this.moonrise$getEntityLookup().getAll(), entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()), // Paper - rewrite chunk system
            0, // Folia - region threading
            "null", // Folia - region threading
            this.getBlockTicks().count(),
            this.getFluidTicks().count(),
            this.gatherChunkSourceStats()
        );
    }

    private static <T> String getTypeCount(Iterable<T> objects, Function<T, String> typeGetter) {
        try {
            Object2IntOpenHashMap<String> map = new Object2IntOpenHashMap<>();

            for (T object : objects) {
                String string = typeGetter.apply(object);
                map.addTo(string, 1);
            }

            return map.object2IntEntrySet()
                .stream()
                .sorted(Comparator.<Entry<String>, Integer>comparing(Entry::getIntValue).reversed())
                .limit(5L)
                .map(entry -> entry.getKey() + ":" + entry.getIntValue())
                .collect(Collectors.joining(","));
        } catch (Exception var6) {
            return "";
        }
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        org.spigotmc.AsyncCatcher.catchOp("Chunk getEntities call"); // Spigot
        return this.moonrise$getEntityLookup(); // Paper - rewrite chunk system
    }

    public void addLegacyChunkEntities(Stream<Entity> entities) {
        // Paper start - add chunkpos param
        this.addLegacyChunkEntities(entities, null);
    }
    public void addLegacyChunkEntities(Stream<Entity> entities, ChunkPos chunkPos) {
        // Paper end - add chunkpos param
        this.moonrise$getEntityLookup().addLegacyChunkEntities(entities.toList(), chunkPos); // Paper - rewrite chunk system
    }

    public void addWorldGenChunkEntities(Stream<Entity> entities) {
        // Paper start - add chunkpos param
        this.addWorldGenChunkEntities(entities, null);
    }
    public void addWorldGenChunkEntities(Stream<Entity> entities, ChunkPos chunkPos) {
        // Paper end - add chunkpos param
        this.moonrise$getEntityLookup().addWorldGenChunkEntities(entities.toList(), chunkPos); // Paper - rewrite chunk system
    }

    public void startTickingChunk(LevelChunk chunk) {
        chunk.unpackTicks(this.getRedstoneGameTime()); // Folia - region threading
    }

    public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.structureCheck.onStructureLoad(chunk.getPos(), chunk.getAllStarts()); // Folia - region threading
    }

    public PathTypeCache getPathTypeCache() {
        return this.getCurrentWorldData().pathTypesByPosCache; // Folia - region threading
    }

    public void waitForEntities(ChunkPos chunkPos, int radius) {
        List<ChunkPos> list = ChunkPos.rangeClosed(chunkPos, radius).toList();
        this.chunkSource.mainThreadProcessor.managedBlock(() -> { // Paper - rewrite chunk system
            //this.entityManager.processPendingLoads(); // Paper - rewrite chunk system

            for (ChunkPos chunkPos1 : list) {
                if (!this.areEntitiesLoaded(chunkPos1.toLong())) {
                    return false;
                }
            }

            return true;
        });
    }

    public boolean isSpawningMonsters() {
        return this.getLevelData().getDifficulty() != Difficulty.PEACEFUL
            && this.getGameRules().get(GameRules.SPAWN_MOBS)
            && this.getGameRules().get(GameRules.SPAWN_MONSTERS);
    }

    @Override
    public void close() throws IOException {
        super.close();
        // Paper - rewrite chunk system
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[S] W: " + this.chunkSource.gatherStats() + " E: " + this.moonrise$getEntityLookup().getDebugInfo(); // Paper - rewrite chunk system
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.moonrise$getAnyChunkIfLoaded(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(chunkPos), ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(chunkPos)) != null; // Paper - rewrite chunk system
    }

    public boolean isPositionTickingWithEntitiesLoaded(long chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        // isTicking implies the chunk is loaded, and the chunk is loaded now implies the entities are loaded
        return chunkHolder != null && chunkHolder.isTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean areEntitiesActuallyLoadedAndTicking(ChunkPos chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkPos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
        // Paper end - rewrite chunk system
    }

    public boolean anyPlayerCloseEnoughForSpawning(BlockPos pos) {
        return this.anyPlayerCloseEnoughForSpawning(new ChunkPos(pos));
    }

    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
        return this.chunkSource.chunkMap.anyPlayerCloseEnoughForSpawning(chunkPos);
    }

    public boolean canSpreadFireAround(BlockPos pos) {
        int i = this.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        return i == -1 || this.chunkSource.chunkMap.anyPlayerCloseEnoughTo(pos, i);
    }

    public boolean canSpawnEntitiesInChunk(ChunkPos chunkPos) {
        // Paper start - rewrite chunk system
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkPos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady() && this.getWorldBorder().isWithinBounds(chunkPos);
        // Paper end - rewrite chunk system
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.server.potionBrewing();
    }

    @Override
    public FuelValues fuelValues() {
        return this.server.fuelValues();
    }

    public RandomSource getRandomSequence(Identifier location) {
        return this.randomSequences.get(location, this.getSeed());
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    public GameRules getGameRules() {
        return this.serverLevelData.getGameRules();
    }

    // Paper start - respect global sound events gamerule
    public List<net.minecraft.server.level.ServerPlayer> getPlayersForGlobalSoundGamerule() {
        return this.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS) ? ((ServerLevel) this).getServer().getPlayerList().players : ((ServerLevel) this).players();
    }

    public double getGlobalSoundRangeSquared(java.util.function.Function<org.spigotmc.SpigotWorldConfig, Integer> rangeFunction) {
        final double range = rangeFunction.apply(this.spigotConfig);
        return range <= 0 ? 64.0 * 64.0 : range * range; // 64 is taken from default in ServerLevel#levelEvent
    }
    // Paper end - respect global sound events gamerule
    // Paper start - notify observers even if grow failed
    @Deprecated
    public void checkCapturedTreeStateForObserverNotify(final BlockPos pos, final org.bukkit.craftbukkit.block.CraftBlockState craftBlockState) {
        // notify observers if the block state is the same and the Y level equals the original y level (for mega trees)
        // blocks at the same Y level with the same state can be assumed to be saplings which trigger observers regardless of if the
        // tree grew or not
        if (craftBlockState.getPosition().getY() == pos.getY() && this.getBlockState(craftBlockState.getPosition()) == craftBlockState.getHandle()) {
            this.notifyAndUpdatePhysics(craftBlockState.getPosition(), null, craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getFlags(), 512);
        }
    }
    // Paper end - notify observers even if grow failed

    @Override
    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashReportCategory = super.fillReportDetails(report);
        crashReportCategory.setDetail("Loaded entity count", () -> String.valueOf(this.moonrise$getEntityLookup().getEntityCount())); // Paper - rewrite chunk system
        return crashReportCategory;
    }

    @Override
    public int getSeaLevel() {
        return this.chunkSource.getGenerator().getSeaLevel();
    }

    @Override
    public void onBlockEntityAdded(BlockEntity entity) {
        super.onBlockEntityAdded(entity);
        this.debugSynchronizers.registerBlockEntity(entity);
    }

    public LevelDebugSynchronizers debugSynchronizers() {
        return this.debugSynchronizers;
    }

    // Paper start - optimize redstone (Alternate Current)
    @Override
    public alternate.current.wire.WireHandler getWireHandler() {
        return this.getCurrentWorldData().wireHandler; // Folia - region threading
    }
    // Paper end - optimize redstone (Alternate Current)

    public boolean isAllowedToEnterPortal(Level level) {
        return level.dimension() != Level.NETHER || this.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS);
    }

    public boolean isPvpAllowed() {
        return this.getGameRules().get(GameRules.PVP);
    }

    public boolean isCommandBlockEnabled() {
        return this.getGameRules().get(GameRules.COMMAND_BLOCKS_WORK);
    }

    public boolean isSpawnerBlockEnabled() {
        return this.getGameRules().get(GameRules.SPAWNER_BLOCKS_WORK);
    }

    final class EntityCallbacks implements LevelCallback<Entity> {
        @Override
        public void onCreated(Entity entity) {
            if (entity instanceof WaypointTransmitter waypointTransmitter && waypointTransmitter.isTransmittingWaypoint()) {
                ServerLevel.this.getWaypointManager().trackWaypoint(waypointTransmitter);
            }
            entity.setOldPosAndRot(); // Paper - update old pos / rot for new entities as it will default to Vec3.ZERO
        }

        @Override
        public void onDestroyed(Entity entity) {
            if (entity instanceof WaypointTransmitter waypointTransmitter) {
                ServerLevel.this.getWaypointManager().untrackWaypoint(waypointTransmitter);
            }

            //ServerLevel.this.getScoreboard().entityRemoved(entity); // Folia - region threading
        }

        @Override
        public void onTickingStart(Entity entity) {
            if (entity instanceof net.minecraft.world.entity.Marker && !paperConfig().entities.markers.tick) return; // Paper - Configurable marker ticking
            ServerLevel.this.getCurrentWorldData().addEntityTickingEntity(entity); // Folia - region threading
        }

        @Override
        public void onTickingEnd(Entity entity) {
            ServerLevel.this.getCurrentWorldData().removeEntityTickingEntity(entity); // Folia - region threading
            // Paper start - Reset pearls when they stop being ticked
            if (ServerLevel.this.paperConfig().fixes.disableUnloadedChunkEnderpearlExploit && ServerLevel.this.paperConfig().misc.legacyEnderPearlBehavior && entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl pearl) {
                pearl.setOwner(null);
            }
            // Paper end - Reset pearls when they stop being ticked
        }

        @Override
        public void onTrackingStart(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity register"); // Spigot
            ServerLevel.this.getCurrentWorldData().addLoadedEntity(entity); // Folia - region threading
            // ServerLevel.this.getChunkSource().addEntity(entity); // Paper - ignore and warn about illegal addEntity calls instead of crashing server; moved down below valid=true
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.add(serverPlayer);
                // Leaves start - skip
                if (!(serverPlayer instanceof org.leavesmc.leaves.bot.ServerBot) && !(serverPlayer instanceof org.leavesmc.leaves.replay.ServerPhotographer)) { // and photographer
                    ServerLevel.this.realPlayers.add(serverPlayer);
                }
                // Leaves end - skip
                if (serverPlayer.isReceivingWaypoints()) {
                    ServerLevel.this.getWaypointManager().addPlayer(serverPlayer);
                }

                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof WaypointTransmitter waypointTransmitter && waypointTransmitter.isTransmittingWaypoint()) {
                ServerLevel.this.getWaypointManager().trackWaypoint(waypointTransmitter);
            }

            if (entity instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper - Remove unnecessary onTrackingStart during navigation warning
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.getCurrentWorldData().addNavigatingMob(mob); // Folia - region threading
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.put((long)enderDragonPart.getId(), enderDragonPart); // Folia - diff on change
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
            entity.inWorld = true; // CraftBukkit - Mark entity as in world
            entity.valid = true; // CraftBukkit
            ServerLevel.this.getChunkSource().addEntity(entity); // Paper - ignore and warn about illegal addEntity calls instead of crashing server
            // Paper start - Entity origin API
            if (entity.origin == null) {
                entity.origin = entity.position();
            }
            // Default to current world if unknown, gross assumption but entities rarely change world
            if (entity.originWorld == null) {
                entity.originWorld = ServerLevel.this.getWorld().getUID();
            }
            // Paper end - Entity origin API
            new com.destroystokyo.paper.event.entity.EntityAddToWorldEvent(entity.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
        }

        @Override
        public void onTrackingEnd(Entity entity) {
            org.spigotmc.AsyncCatcher.catchOp("entity unregister"); // Spigot
            ServerLevel.this.getCurrentWorldData().removeLoadedEntity(entity); // Folia - region threading
            // Spigot start // TODO I don't think this is needed anymore
            if (entity instanceof Player player) {
                for (final ServerLevel level : ServerLevel.this.getServer().getAllLevels()) {
                    for (final Optional<net.minecraft.world.level.saveddata.SavedData> savedData : level.getDataStorage().cache.values()) {
                        if (savedData.isEmpty() || !(savedData.get() instanceof MapItemSavedData map)) {
                            continue;
                        }

                        synchronized (map) { // Folia - make map data thread-safe
                        map.carriedByPlayers.remove(player);
                        if (map.carriedBy.removeIf(holdingPlayer -> holdingPlayer.player == player)) {
                            map.decorations.remove(player.getName().getString());
                        }
                        } // Folia - make map data thread-safe
                    }
                }
            }
            // Spigot end
            // Spigot start
            if (entity.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder && (!(entity instanceof ServerPlayer) || entity.getRemovalReason() != Entity.RemovalReason.KILLED)) { // SPIGOT-6876: closeInventory clears death message
                // Paper start - Fix merchant inventory not closing on entity removal
                if (entity.getBukkitEntity() instanceof org.bukkit.inventory.Merchant merchant && merchant.getTrader() != null) {
                    merchant.getTrader().closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED);
                }
                // Paper end - Fix merchant inventory not closing on entity removal
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) entity.getBukkitEntity()).getInventory().getViewers())) {
                    h.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                }
            }
            // Spigot end
            ServerLevel.this.getChunkSource().removeEntity(entity);
            if (entity instanceof ServerPlayer serverPlayer) {
                ServerLevel.this.players.remove(serverPlayer);
                // Leaves start - skip
                if (!(serverPlayer instanceof org.leavesmc.leaves.bot.ServerBot) && !(serverPlayer instanceof org.leavesmc.leaves.replay.ServerPhotographer)) { // and photographer
                    ServerLevel.this.realPlayers.remove(serverPlayer);
                }
                // Leaves end - skip
                ServerLevel.this.getWaypointManager().removePlayer(serverPlayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (entity instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) { // Paper - Remove unnecessary onTrackingStart during navigation warning
                    String string = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.getCurrentWorldData().removeNavigatingMob(mob); // Folia - region threading
            }

            if (entity instanceof EnderDragon enderDragon) {
                for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
                    ServerLevel.this.dragonParts.remove((long)enderDragonPart.getId()); // Folia - diff on change
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            ServerLevel.this.debugSynchronizers.dropEntity(entity);
            // CraftBukkit start
            entity.valid = false;
            // Folia - region threading - TODO THIS SHIT
            if (!(entity instanceof ServerPlayer)) {
                for (ServerPlayer player : ServerLevel.this.server.getPlayerList().players) { // Paper - call onEntityRemove for all online players
                    // Mili fix - #406: schedule onEntityRemove on the player's region thread to avoid
                    // concurrent modification of invertedVisibilityEntities (non-thread-safe HashMap)
                    final ServerPlayer targetPlayer = player;
                    final Entity removedEntity = entity;
                    targetPlayer.getBukkitEntity().taskScheduler.schedule(
                        (Entity p) -> targetPlayer.getBukkitEntity().onEntityRemove(removedEntity),
                        null, 1L
                    );
                }
            }
            // CraftBukkit end
            new com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent(entity.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
        }

        @Override
        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }

    // Paper start - check global player list where appropriate
    @Override
    @Nullable
    public Player getGlobalPlayerByUUID(java.util.UUID uuid) {
        return this.server.getPlayerList().getPlayer(uuid);
    }
    // Paper end - check global player list where appropriate

    // Paper start - lag compensation
    private long lagCompensationTick = MinecraftServer.SERVER_INIT;

    public long getLagCompensationTick() {
        return this.getCurrentWorldData().getLagCompensationTick(); // Folia - region threading
    }

    public void updateLagCompensationTick() {
        throw new UnsupportedOperationException(); // Folia - region threading
    }
    // Paper end - lag compensation
}
