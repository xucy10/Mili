package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class LevelChunkSection implements ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection { // Paper - block counting
    public static final int SECTION_WIDTH = 16;
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_SIZE = 4096;
    public static final int BIOME_CONTAINER_BITS = 2;
    short nonEmptyBlockCount; // Paper - package private
    private short tickingBlockCount;
    private short tickingFluidCount;
    public final PalettedContainer<BlockState> states;
    private PalettedContainer<Holder<Biome>> biomes; // CraftBukkit - read/write

    // Paper start - block counting
    private static final it.unimi.dsi.fastutil.shorts.ShortArrayList FULL_LIST = new it.unimi.dsi.fastutil.shorts.ShortArrayList(16*16*16);
    static {
        for (short i = 0; i < (16*16*16); ++i) {
            FULL_LIST.add(i);
        }
    }

    private boolean isClient;
    private static final short CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS = (short)9999;
    private short specialCollidingBlocks;
    private final ca.spottedleaf.moonrise.common.list.ShortList tickingBlocks = new ca.spottedleaf.moonrise.common.list.ShortList();

    @Override
    public final boolean moonrise$hasSpecialCollidingBlocks() {
        return this.specialCollidingBlocks != 0;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ShortList moonrise$getTickingBlockList() {
        return this.tickingBlocks;
    }
    // Paper end - block counting

    private LevelChunkSection(LevelChunkSection section) {
        this.nonEmptyBlockCount = section.nonEmptyBlockCount;
        this.tickingBlockCount = section.tickingBlockCount;
        this.tickingFluidCount = section.tickingFluidCount;
        this.states = section.states.copy();
        this.biomes = section.biomes.copy();
    }

    public LevelChunkSection(PalettedContainer<BlockState> states, PalettedContainer<Holder<Biome>> biomes) { // CraftBukkit - read/write
        this.states = states;
        this.biomes = biomes;
        this.recalcBlockCounts();
    }

    // Paper start - Anti-Xray - Add parameters
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public LevelChunkSection(PalettedContainerFactory containerFactory) {
        this(containerFactory, null, null, 0);
    }
    public LevelChunkSection(PalettedContainerFactory containerFactory, net.minecraft.world.level.Level level, net.minecraft.world.level.ChunkPos chunkPos, int chunkSectionY) {
        this.states = containerFactory.createForBlockStates(level, chunkPos, chunkSectionY);
        this.biomes = containerFactory.createForBiomes();
        // Paper end - Anti-Xray
    }

    public BlockState getBlockState(int x, int y, int z) {
        return this.states.get(x, y, z);
    }

    public FluidState getFluidState(int x, int y, int z) {
        return this.states.get(x, y, z).getFluidState(); // Paper - Perf: Optimise LevelChunk#getFluidState; diff on change - we expect this to be effectively just get(x, y, z).getFluidState(). If this changes we need to check other patches that use BlockBehaviour#getFluidState.
    }

    public void acquire() {
        this.states.acquire();
    }

    public void release() {
        this.states.release();
    }

    public BlockState setBlockState(int x, int y, int z, BlockState state) {
        return this.setBlockState(x, y, z, state, true);
    }

    // Paper start - block counting
    private void updateBlockCallback(final int x, final int y, final int z, final BlockState newState,
                                     final BlockState oldState) {
        if (oldState == newState) {
            return;
        }

        if (this.isClient) {
            if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(newState)) {
                this.specialCollidingBlocks = CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS;
            }
            return;
        }

        final boolean isSpecialOld = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(oldState);
        final boolean isSpecialNew = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(newState);
        if (isSpecialOld != isSpecialNew) {
            if (isSpecialOld) {
                --this.specialCollidingBlocks;
            } else {
                ++this.specialCollidingBlocks;
            }
        }

        final boolean oldTicking = oldState.isRandomlyTicking();
        final boolean newTicking = newState.isRandomlyTicking();
        if (oldTicking != newTicking) {
            final ca.spottedleaf.moonrise.common.list.ShortList tickingBlocks = this.tickingBlocks;
            final short position = (short)(x | (z << 4) | (y << (4+4)));

            if (oldTicking) {
                tickingBlocks.remove(position);
            } else {
                tickingBlocks.add(position);
            }
        }
    }
    // Paper end - block counting

    public BlockState setBlockState(int x, int y, int z, BlockState state, boolean useLocks) {
        BlockState blockState;
        if (useLocks) {
            blockState = this.states.getAndSet(x, y, z, state);
        } else {
            blockState = this.states.getAndSetUnchecked(x, y, z, state);
        }

        FluidState fluidState = blockState.getFluidState();
        FluidState fluidState1 = state.getFluidState();
        if (!blockState.isAir()) {
            this.nonEmptyBlockCount--;
            if (blockState.isRandomlyTicking()) {
                this.tickingBlockCount--;
            }
        }

        if (!!fluidState.isRandomlyTicking()) { // Paper - block counting
            this.tickingFluidCount--;
        }

        if (!state.isAir()) {
            this.nonEmptyBlockCount++;
            if (state.isRandomlyTicking()) {
                this.tickingBlockCount++;
            }
        }

        if (!!fluidState1.isRandomlyTicking()) { // Paper - block counting
            this.tickingFluidCount++;
        }

        this.updateBlockCallback(x, y, z, state, blockState); // Paper - block counting

        return blockState;
    }

    public boolean hasOnlyAir() {
        return this.nonEmptyBlockCount == 0;
    }

    public boolean isRandomlyTicking() {
        return this.isRandomlyTickingBlocks() || this.isRandomlyTickingFluids();
    }

    public boolean isRandomlyTickingBlocks() {
        return this.tickingBlockCount > 0;
    }

    public boolean isRandomlyTickingFluids() {
        return this.tickingFluidCount > 0;
    }

    public void recalcBlockCounts() {
        // Paper start - block counting
        // reset, then recalculate
        this.nonEmptyBlockCount = (short)0;
        this.tickingBlockCount = (short)0;
        this.tickingFluidCount = (short)0;
        this.specialCollidingBlocks = (short)0;
        this.tickingBlocks.clear();

        if (this.maybeHas((final BlockState state) -> !state.isAir())) {
            final PalettedContainer.Data<BlockState> data = this.states.data;
            final Palette<BlockState> palette = data.palette();
            final int paletteSize = palette.getSize();
            final net.minecraft.util.BitStorage storage = data.storage();

            final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.shorts.ShortArrayList> counts;
            if (paletteSize == 1) {
                counts = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(1);
                counts.put(0, FULL_LIST);
            } else {
                counts = ((ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage)storage).moonrise$countEntries();
            }

            for (final java.util.Iterator<it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<it.unimi.dsi.fastutil.shorts.ShortArrayList>> iterator = counts.int2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<it.unimi.dsi.fastutil.shorts.ShortArrayList> entry = iterator.next();
                final int paletteIdx = entry.getIntKey();
                final it.unimi.dsi.fastutil.shorts.ShortArrayList coordinates = entry.getValue();
                final int paletteCount = coordinates.size();

                final BlockState state = palette.valueFor(paletteIdx);

                if (state.isAir()) {
                    continue;
                }

                if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(state)) {
                    this.specialCollidingBlocks += (short)paletteCount;
                }
                this.nonEmptyBlockCount += (short)paletteCount;
                if (state.isRandomlyTicking()) {
                    this.tickingBlockCount += (short)paletteCount;
                    final short[] raw = coordinates.elements();
                    final int rawLen = raw.length;

                    final ca.spottedleaf.moonrise.common.list.ShortList tickingBlocks = this.tickingBlocks;

                    tickingBlocks.setMinCapacity(Math.min((rawLen + tickingBlocks.size()) * 3 / 2, 16*16*16));

                    java.util.Objects.checkFromToIndex(0, paletteCount, raw.length);
                    for (int i = 0; i < paletteCount; ++i) {
                        tickingBlocks.add(raw[i]);
                    }
                }

                final FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty()) {
                    //this.nonEmptyBlockCount += count; // fix vanilla bug: make non-empty block count correct
                    if (fluid.isRandomlyTicking()) {
                        this.tickingFluidCount += (short)paletteCount;
                    }
                }
            }
        }
        // Paper end - block counting
    }

    public PalettedContainer<BlockState> getStates() {
        return this.states;
    }

    public PalettedContainerRO<Holder<Biome>> getBiomes() {
        return this.biomes;
    }

    public void read(FriendlyByteBuf buffer) {
        this.nonEmptyBlockCount = buffer.readShort();
        this.states.read(buffer);
        PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
        palettedContainer.read(buffer);
        this.biomes = palettedContainer;
        // Paper start - block counting
        this.isClient = true;
        // force has special colliding blocks to be true
        this.specialCollidingBlocks = this.nonEmptyBlockCount != (short)0 && this.maybeHas(ca.spottedleaf.moonrise.patches.collisions.CollisionUtil::isSpecialCollidingBlock) ? CLIENT_FORCED_SPECIAL_COLLIDING_BLOCKS : (short)0;
        // Paper end - block counting
    }

    public void readBiomes(FriendlyByteBuf buffer) {
        PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
        palettedContainer.read(buffer);
        this.biomes = palettedContainer;
    }

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse
    public void write(FriendlyByteBuf buffer) {
        this.write(buffer, null, 0);
    }
    public void write(FriendlyByteBuf buffer, io.papermc.paper.antixray.ChunkPacketInfo<BlockState> chunkPacketInfo, int chunkSectionIndex) {
        buffer.writeShort(this.nonEmptyBlockCount);
        this.states.write(buffer, chunkPacketInfo, chunkSectionIndex);
        this.biomes.write(buffer, null, chunkSectionIndex);
    // Paper end - Anti-Xray - Add chunk packet info
    }

    public int getSerializedSize() {
        return 2 + this.states.getSerializedSize() + this.biomes.getSerializedSize();
    }

    public boolean maybeHas(Predicate<BlockState> predicate) {
        return this.states.maybeHas(predicate);
    }

    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return this.biomes.get(x, y, z);
    }
    // CraftBukkit start
    public void setBiome(int x, int y, int z, Holder<Biome> biome) {
        this.biomes.set(x, y, z, biome);
    }
    // CraftBukkit end

    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler climateSampler, int x, int y, int z) {
        PalettedContainer<Holder<Biome>> palettedContainer = this.biomes.recreate();
        int i = 4;

        for (int i1 = 0; i1 < 4; i1++) {
            for (int i2 = 0; i2 < 4; i2++) {
                for (int i3 = 0; i3 < 4; i3++) {
                    palettedContainer.getAndSetUnchecked(i1, i2, i3, biomeResolver.getNoiseBiome(x + i1, y + i2, z + i3, climateSampler));
                }
            }
        }

        this.biomes = palettedContainer;
    }

    public LevelChunkSection copy() {
        return new LevelChunkSection(this);
    }
}
