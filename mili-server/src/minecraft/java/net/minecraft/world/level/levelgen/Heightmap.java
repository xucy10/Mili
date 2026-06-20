package net.minecraft.world.level.levelgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public class Heightmap {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Predicate<BlockState> NOT_AIR = state -> !state.isAir();
    static final Predicate<BlockState> MATERIAL_MOTION_BLOCKING = BlockBehaviour.BlockStateBase::blocksMotion;
    private final BitStorage data;
    private final Predicate<BlockState> isOpaque;
    private final ChunkAccess chunk;

    public Heightmap(ChunkAccess chunk, Heightmap.Types type) {
        this.isOpaque = type.isOpaque();
        this.chunk = chunk;
        int i = Mth.ceillog2(chunk.getHeight() + 1);
        this.data = new SimpleBitStorage(i, 256);
    }

    public static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (!types.isEmpty()) {
            int size = types.size();
            ObjectList<Heightmap> list = new ObjectArrayList<>(size);
            ObjectListIterator<Heightmap> objectListIterator = list.iterator();
            int i = chunk.getHighestSectionPosition() + 16;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i1 = 0; i1 < 16; i1++) {
                for (int i2 = 0; i2 < 16; i2++) {
                    for (Heightmap.Types types1 : types) {
                        list.add(chunk.getOrCreateHeightmapUnprimed(types1));
                    }

                    for (int i3 = i - 1; i3 >= chunk.getMinY(); i3--) {
                        mutableBlockPos.set(i1, i3, i2);
                        BlockState blockState = chunk.getBlockState(mutableBlockPos);
                        if (!blockState.is(Blocks.AIR)) {
                            while (objectListIterator.hasNext()) {
                                Heightmap heightmap = objectListIterator.next();
                                if (heightmap.isOpaque.test(blockState)) {
                                    heightmap.setHeight(i1, i2, i3 + 1);
                                    objectListIterator.remove();
                                }
                            }

                            if (list.isEmpty()) {
                                break;
                            }

                            objectListIterator.back(size);
                        }
                    }
                }
            }
        }
    }

    public boolean update(int x, int y, int z, BlockState state) {
        int firstAvailable = this.getFirstAvailable(x, z);
        if (y <= firstAvailable - 2) {
            return false;
        } else {
            if (this.isOpaque.test(state)) {
                if (y >= firstAvailable) {
                    this.setHeight(x, z, y + 1);
                    return true;
                }
            } else if (firstAvailable - 1 == y) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int i = y - 1; i >= this.chunk.getMinY(); i--) {
                    mutableBlockPos.set(x, i, z);
                    if (this.isOpaque.test(this.chunk.getBlockState(mutableBlockPos))) {
                        this.setHeight(x, z, i + 1);
                        return true;
                    }
                }

                this.setHeight(x, z, this.chunk.getMinY());
                return true;
            }

            return false;
        }
    }

    public int getFirstAvailable(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z));
    }

    public int getHighestTaken(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z)) - 1;
    }

    private int getFirstAvailable(int index) {
        return this.data.get(index) + this.chunk.getMinY();
    }

    private void setHeight(int x, int z, int value) {
        this.data.set(getIndex(x, z), value - this.chunk.getMinY());
    }

    // Mili start - lithium: combined_heightmap_update
    public final Predicate<BlockState> isOpaque() {
        return this.isOpaque;
    }
    // Mili end - lithium: combined_heightmap_update

    public void setRawData(ChunkAccess chunk, Heightmap.Types type, long[] data) {
        long[] raw = this.data.getRaw();
        if (raw.length == data.length) {
            System.arraycopy(data, 0, raw, 0, data.length);
        } else {
            if (!me.earthme.luminol.config.modules.misc.DisableWarningConfig.disableHeightmapWarning)
                LOGGER.warn("Ignoring heightmap data for chunk {}, size does not match; expected: {}, got: {}", chunk.getPos(), raw.length, data.length);
            primeHeightmaps(chunk, EnumSet.of(type));
        }
    }

    public long[] getRawData() {
        return this.data.getRaw();
    }

    private static int getIndex(int x, int z) {
        return x + z * 16;
    }

    public static enum Types implements StringRepresentable {
        WORLD_SURFACE_WG(0, "WORLD_SURFACE_WG", Heightmap.Usage.WORLDGEN, Heightmap.NOT_AIR),
        WORLD_SURFACE(1, "WORLD_SURFACE", Heightmap.Usage.CLIENT, Heightmap.NOT_AIR),
        OCEAN_FLOOR_WG(2, "OCEAN_FLOOR_WG", Heightmap.Usage.WORLDGEN, Heightmap.MATERIAL_MOTION_BLOCKING),
        OCEAN_FLOOR(3, "OCEAN_FLOOR", Heightmap.Usage.LIVE_WORLD, Heightmap.MATERIAL_MOTION_BLOCKING),
        MOTION_BLOCKING(4, "MOTION_BLOCKING", Heightmap.Usage.CLIENT, state -> state.blocksMotion() || !state.getFluidState().isEmpty()),
        MOTION_BLOCKING_NO_LEAVES(
            5,
            "MOTION_BLOCKING_NO_LEAVES",
            Heightmap.Usage.CLIENT,
            state -> (state.blocksMotion() || !state.getFluidState().isEmpty()) && !(state.getBlock() instanceof LeavesBlock)
        );

        public static final Codec<Heightmap.Types> CODEC = StringRepresentable.fromEnum(Heightmap.Types::values);
        private static final IntFunction<Heightmap.Types> BY_ID = ByIdMap.continuous(types -> types.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Heightmap.Types> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, types -> types.id);
        private final int id;
        private final String serializationKey;
        private final Heightmap.Usage usage;
        private final Predicate<BlockState> isOpaque;

        private Types(final int id, final String serializationKey, final Heightmap.Usage usage, final Predicate<BlockState> isOpaque) {
            this.id = id;
            this.serializationKey = serializationKey;
            this.usage = usage;
            this.isOpaque = isOpaque;
        }

        public String getSerializationKey() {
            return this.serializationKey;
        }

        public boolean sendToClient() {
            return this.usage == Heightmap.Usage.CLIENT;
        }

        public boolean keepAfterWorldgen() {
            return this.usage != Heightmap.Usage.WORLDGEN;
        }

        public Predicate<BlockState> isOpaque() {
            return this.isOpaque;
        }

        @Override
        public String getSerializedName() {
            return this.serializationKey;
        }
    }

    public static enum Usage {
        WORLDGEN,
        LIVE_WORLD,
        CLIENT;
    }
}
