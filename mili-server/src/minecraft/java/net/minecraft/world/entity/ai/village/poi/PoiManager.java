package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.debug.DebugPoiInfo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.jspecify.annotations.Nullable;

public class PoiManager extends SectionStorage<PoiSection, PoiSection.Packed> implements ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiManager { // Paper - rewrite chunk system
    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;
    private final PoiManager.DistanceTracker distanceTracker;
    private final LongSet loadedChunks = new LongOpenHashSet();

    // Paper start - rewrite chunk system
    private final net.minecraft.server.level.ServerLevel world;

    // the vanilla tracker needs to be replaced because it does not support level removes, and we need level removes
    // to support poi unloading
    private final ca.spottedleaf.moonrise.common.misc.Delayed26WayDistancePropagator3D villageDistanceTracker = new ca.spottedleaf.moonrise.common.misc.Delayed26WayDistancePropagator3D();

    private static final int POI_DATA_SOURCE = 7;

    private static int convertBetweenLevels(final int level) {
        return POI_DATA_SOURCE - level;
    }

    private void updateDistanceTracking(long section) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        if (this.isVillageCenter(section)) {
            this.villageDistanceTracker.setSource(section, POI_DATA_SOURCE);
        } else {
            this.villageDistanceTracker.removeSource(section);
        }
        } // Folia - region threading
    }

    @Override
    public Optional<PoiSection> get(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager.getPoiChunkIfLoaded(chunkX, chunkZ, true);

        return ret == null ? Optional.empty() : ret.getSectionForVanilla(chunkY);
    }

    @Override
    public Optional<PoiSection> getOrLoad(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        if (chunkY >= ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(this.world) && chunkY <= ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(this.world)) {
            final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
            if (ret != null) {
                return ret.getSectionForVanilla(chunkY);
            } else {
                return manager.loadPoiChunk(chunkX, chunkZ).getSectionForVanilla(chunkY);
            }
        }
        // retain vanilla behavior: do not load section if out of bounds!
        return Optional.empty();
    }

    @Override
    protected PoiSection getOrCreate(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
        if (ret != null) {
            return ret.getOrCreateSection(chunkY);
        } else {
            return manager.loadPoiChunk(chunkX, chunkZ).getOrCreateSection(chunkY);
        }
    }

    @Override
    public final net.minecraft.server.level.ServerLevel moonrise$getWorld() {
        return this.world;
    }

    @Override
    public final void moonrise$onUnload(final long coordinate) { // Paper - rewrite chunk system
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(coordinate);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(coordinate);

        final int minY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(this.world);
        final int maxY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(this.world);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Unloading poi chunk off-main");
        for (int sectionY = minY; sectionY <= maxY; ++sectionY) {
            final long sectionPos = SectionPos.asLong(chunkX, sectionY, chunkZ);
            this.updateDistanceTracking(sectionPos);
        }
    }

    @Override
    public final void moonrise$loadInPoiChunk(final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk poiChunk) {
        final int chunkX = poiChunk.chunkX;
        final int chunkZ = poiChunk.chunkZ;

        final int minY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(this.world);
        final int maxY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(this.world);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Loading poi chunk off-main");
        for (int sectionY = minY; sectionY <= maxY; ++sectionY) {
            final PoiSection section = poiChunk.getSection(sectionY);
            if (section != null && !((ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiSection)section).moonrise$isEmpty()) {
                this.onSectionLoad(SectionPos.asLong(chunkX, sectionY, chunkZ));
            }
        }
    }

    @Override
    public final void moonrise$checkConsistency(final net.minecraft.world.level.chunk.ChunkAccess chunk) {
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;

        final int minY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(chunk);
        final int maxY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(chunk);
        final LevelChunkSection[] sections = chunk.getSections();
        for (int section = minY; section <= maxY; ++section) {
            this.checkConsistencyWithBlocks(SectionPos.of(chunkX, section, chunkZ), sections[section - minY]);
        }
    }
    // Paper end - rewrite chunk system

    public PoiManager(
        RegionStorageInfo info,
        Path folder,
        DataFixer fixerUpper,
        boolean sync,
        RegistryAccess registryAccess,
        ChunkIOErrorReporter errorReporter,
        LevelHeightAccessor levelHeightAccessor
    ) {
        super(
            new SimpleRegionStorage(info, folder, fixerUpper, sync, DataFixTypes.POI_CHUNK),
            PoiSection.Packed.CODEC,
            PoiSection::pack,
            PoiSection.Packed::unpack,
            PoiSection::new,
            registryAccess,
            errorReporter,
            levelHeightAccessor
        );
        this.distanceTracker = new PoiManager.DistanceTracker();
        this.world = (net.minecraft.server.level.ServerLevel)levelHeightAccessor; // Paper - rewrite chunk system
    }

    public @Nullable PoiRecord add(BlockPos pos, Holder<PoiType> type) {
        return this.getOrCreate(SectionPos.asLong(pos)).add(pos, type);
    }

    public void remove(BlockPos pos) {
        this.getOrLoad(SectionPos.asLong(pos)).ifPresent(section -> section.remove(pos));
    }

    public long getCountInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        return this.getInRange(typePredicate, pos, distance, status).count();
    }

    public boolean existsAtPosition(ResourceKey<PoiType> poiType, BlockPos pos) {
        return this.exists(pos, holder -> holder.is(poiType));
    }

    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        int i = Math.floorDiv(distance, 16) + 1;
        return ChunkPos.rangeClosed(new ChunkPos(pos), i).flatMap(chunkPos -> this.getInChunk(typePredicate, chunkPos, status)).filter(poiRecord -> {
            BlockPos pos1 = poiRecord.getPos();
            return Math.abs(pos1.getX() - pos.getX()) <= distance && Math.abs(pos1.getZ() - pos.getZ()) <= distance;
        });
    }

    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        int i = distance * distance;
        return this.getInSquare(typePredicate, pos, distance, status).filter(poiRecord -> poiRecord.getPos().distSqr(pos) <= i);
    }

    @VisibleForDebug
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos posChunk, PoiManager.Occupancy status) {
        return IntStream.rangeClosed(this.levelHeightAccessor.getMinSectionY(), this.levelHeightAccessor.getMaxSectionY())
            .boxed()
            .map(integer -> this.getOrLoad(SectionPos.of(posChunk, integer).asLong()))
            .filter(Optional::isPresent)
            .flatMap(optional -> optional.get().getRecords(typePredicate, status));
    }

    public Stream<BlockPos> findAll(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status).map(PoiRecord::getPos).filter(posPredicate);
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status)
            .filter(poiRecord -> posPredicate.test(poiRecord.getPos()))
            .map(poiRecord -> Pair.of(poiRecord.getPoiType(), poiRecord.getPos()));
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.findAllWithType(typePredicate, posPredicate, pos, distance, status)
            .sorted(Comparator.comparingDouble(pair -> pair.getSecond().distSqr(pos)));
    }

    public Optional<BlockPos> find(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findAnyPoiPosition(this, typePredicate, posPredicate, pos, distance, status, false);
        return Optional.ofNullable(ret);
        // Paper end
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        // Paper start - re-route to faster logic
        BlockPos closestPos = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, null, pos, distance, distance * distance, status, false);
        return Optional.ofNullable(closestPos);
        // Paper end - re-route to faster logic
    }

    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
        Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        // Paper start - re-route to faster logic
        return Optional.ofNullable(io.papermc.paper.util.PoiAccess.findClosestPoiDataTypeAndPosition(
            this, typePredicate, null, pos, distance, distance * distance, status, false
        ));
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> findClosest(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        // Paper start - re-route to faster logic
        BlockPos closestPos = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, posPredicate, pos, distance, distance * distance, status, false);
        return Optional.ofNullable(closestPos);
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> take(
        Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> combinedTypePosPredicate, BlockPos pos, int distance
    ) {
        // Paper start - re-route to faster logic
        final @javax.annotation.Nullable PoiRecord closest = io.papermc.paper.util.PoiAccess.findClosestPoiDataRecord(
            this, typePredicate, combinedTypePosPredicate, pos, distance, distance * distance, Occupancy.HAS_SPACE, false
        );
        return Optional.ofNullable(closest)
            // Paper end - re-route to faster logic
            .map(poiRecord -> {
                poiRecord.acquireTicket();
                return poiRecord.getPos();
            });
    }

    public Optional<BlockPos> getRandom(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> posPredicate,
        PoiManager.Occupancy status,
        BlockPos pos,
        int distance,
        RandomSource random
    ) {
        // Paper start - re-route to faster logic
        List<PoiRecord> list = new java.util.ArrayList<>();
        io.papermc.paper.util.PoiAccess.findAnyPoiRecords(
            this, typePredicate, posPredicate, pos, distance, status, false, Integer.MAX_VALUE, list
        );

        // the old method shuffled the list and then tried to find the first element in it that
        // matched positionPredicate, however we moved positionPredicate into the poi search. This means we can avoid a
        // shuffle entirely, and just pick a random element from list
        if (list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(list.get(random.nextInt(list.size())).getPos());
        // Paper end - re-route to faster logic
    }

    public boolean release(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos))
            .map(poiSection -> poiSection.release(pos))
            .orElseThrow(() -> Util.pauseInIde(new IllegalStateException("POI never registered at " + pos)));
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate) {
        return this.getOrLoad(SectionPos.asLong(pos)).map(poiSection -> poiSection.exists(pos, typePredicate)).orElse(false);
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap(poiSection -> poiSection.getType(pos));
    }

    @VisibleForDebug
    public @Nullable DebugPoiInfo getDebugPoiInfo(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap(poiSection -> poiSection.getDebugPoiInfo(pos)).orElse(null);
    }

    public int sectionsToVillage(SectionPos sectionPos) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        // Paper start - rewrite chunk system
        this.villageDistanceTracker.propagateUpdates();
        return convertBetweenLevels(this.villageDistanceTracker.getLevel(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionKey(sectionPos)));
        // Paper end - rewrite chunk system
        } // Folia - region threading
    }

    boolean isVillageCenter(long chunkPos) {
        Optional<PoiSection> optional = this.get(chunkPos);
        return optional != null
            && optional.<Boolean>map(
                    poiSection -> poiSection.getRecords(holder -> holder.is(PoiTypeTags.VILLAGE), PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent()
                )
                .orElse(false);
    }

    @Override
    public void tick(BooleanSupplier aheadOfTime) {
        synchronized (this.villageDistanceTracker) { // Folia - region threading
        this.villageDistanceTracker.propagateUpdates(); // Paper - rewrite chunk system
        } // Folia - region threading
    }

    @Override
    public void setDirty(long sectionPos) { // Paper - public
        // Paper start - rewrite chunk system
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(sectionPos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(sectionPos);
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk chunk = manager.getPoiChunkIfLoaded(chunkX, chunkZ, false);
        if (chunk != null) {
            chunk.setDirty(true);
        }
        this.updateDistanceTracking(sectionPos);
        // Paper end - rewrite chunk system
    }

    @Override
    protected void onSectionLoad(long sectionKey) {
        this.updateDistanceTracking(sectionKey); // Paper - rewrite chunk system
    }

    public void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection levelChunkSection) {
        Util.ifElse(this.getOrLoad(sectionPos.asLong()), poiSection -> poiSection.refresh(biConsumer -> {
            if (mayHavePoi(levelChunkSection)) {
                this.updateFromSection(levelChunkSection, sectionPos, biConsumer);
            }
        }), () -> {
            if (mayHavePoi(levelChunkSection)) {
                PoiSection poiSection = this.getOrCreate(sectionPos.asLong());
                this.updateFromSection(levelChunkSection, sectionPos, poiSection::add);
            }
        });
    }

    private static boolean mayHavePoi(LevelChunkSection section) {
        return section.maybeHas(PoiTypes::hasPoi);
    }

    private void updateFromSection(LevelChunkSection section, SectionPos sectionPos, BiConsumer<BlockPos, Holder<PoiType>> posToTypeConsumer) {
        sectionPos.blocksInside()
            .forEach(
                blockPos -> {
                    BlockState blockState = section.getBlockState(
                        SectionPos.sectionRelative(blockPos.getX()), SectionPos.sectionRelative(blockPos.getY()), SectionPos.sectionRelative(blockPos.getZ())
                    );
                    PoiTypes.forState(blockState).ifPresent(holder -> posToTypeConsumer.accept(blockPos, (Holder<PoiType>)holder));
                }
            );
    }

    public void ensureLoadedAndValid(LevelReader level, BlockPos pos, int radius) {
        SectionPos.aroundChunk(
                new ChunkPos(pos), Math.floorDiv(radius, 16), this.levelHeightAccessor.getMinSectionY(), this.levelHeightAccessor.getMaxSectionY()
            )
            .map(sectionPos -> Pair.of(sectionPos, this.getOrLoad(sectionPos.asLong())))
            .filter(pair -> !pair.getSecond().map(PoiSection::isValid).orElse(false))
            .map(pair -> pair.getFirst().chunk())
            // Paper - rewrite chunk system
            .forEach(chunkPos -> level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY));
    }

    final class DistanceTracker extends SectionTracker {
        private final Long2ByteMap levels = new Long2ByteOpenHashMap();

        protected DistanceTracker() {
            super(7, 16, 256);
            this.levels.defaultReturnValue((byte)7);
        }

        @Override
        protected int getLevelFromSource(long pos) {
            return PoiManager.this.isVillageCenter(pos) ? 0 : 7;
        }

        @Override
        protected int getLevel(long sectionPos) {
            return this.levels.get(sectionPos);
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            if (level > 6) {
                this.levels.remove(sectionPos);
            } else {
                this.levels.put(sectionPos, (byte)level);
            }
        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }
    }

    public static enum Occupancy {
        HAS_SPACE(PoiRecord::hasSpace),
        IS_OCCUPIED(PoiRecord::isOccupied),
        ANY(test -> true);

        private final Predicate<? super PoiRecord> test;

        private Occupancy(final Predicate<? super PoiRecord> test) {
            this.test = test;
        }

        public Predicate<? super PoiRecord> getTest() {
            return this.test;
        }
    }
}
