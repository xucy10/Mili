package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

public abstract class WorldCarver<C extends CarverConfiguration> {
    public static final WorldCarver<CaveCarverConfiguration> CAVE = register("cave", new CaveWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CaveCarverConfiguration> NETHER_CAVE = register("nether_cave", new NetherWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CanyonCarverConfiguration> CANYON = register("canyon", new CanyonWorldCarver(CanyonCarverConfiguration.CODEC));
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected static final FluidState WATER = Fluids.WATER.defaultFluidState();
    protected static final FluidState LAVA = Fluids.LAVA.defaultFluidState();
    protected Set<Fluid> liquids = ImmutableSet.of(Fluids.WATER);
    private final MapCodec<ConfiguredWorldCarver<C>> configuredCodec;

    private static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(String key, F carver) {
        return Registry.register(BuiltInRegistries.CARVER, key, carver);
    }

    public WorldCarver(Codec<C> codec) {
        this.configuredCodec = codec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config);
    }

    public ConfiguredWorldCarver<C> configured(C config) {
        return new ConfiguredWorldCarver<>(this, config);
    }

    public MapCodec<ConfiguredWorldCarver<C>> configuredCodec() {
        return this.configuredCodec;
    }

    public int getRange() {
        return 4;
    }

    protected boolean carveEllipsoid(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadius,
        double verticalRadius,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        ChunkPos pos = chunk.getPos();
        double d = pos.getMiddleBlockX();
        double d1 = pos.getMiddleBlockZ();
        double d2 = 16.0 + horizontalRadius * 2.0;
        if (!(Math.abs(x - d) > d2) && !(Math.abs(z - d1) > d2)) {
            int minBlockX = pos.getMinBlockX();
            int minBlockZ = pos.getMinBlockZ();
            int max = Math.max(Mth.floor(x - horizontalRadius) - minBlockX - 1, 0);
            int min = Math.min(Mth.floor(x + horizontalRadius) - minBlockX, 15);
            int max1 = Math.max(Mth.floor(y - verticalRadius) - 1, context.getMinGenY() + 1);
            int i = chunk.isUpgrading() ? 0 : 7;
            int min1 = Math.min(Mth.floor(y + verticalRadius) + 1, context.getMinGenY() + context.getGenDepth() - 1 - i);
            int max2 = Math.max(Mth.floor(z - horizontalRadius) - minBlockZ - 1, 0);
            int min2 = Math.min(Mth.floor(z + horizontalRadius) - minBlockZ, 15);
            boolean flag = false;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

            for (int i1 = max; i1 <= min; i1++) {
                int blockX = pos.getBlockX(i1);
                double d3 = (blockX + 0.5 - x) / horizontalRadius;

                for (int i2 = max2; i2 <= min2; i2++) {
                    int blockZ = pos.getBlockZ(i2);
                    double d4 = (blockZ + 0.5 - z) / horizontalRadius;
                    if (!(d3 * d3 + d4 * d4 >= 1.0)) {
                        MutableBoolean mutableBoolean = new MutableBoolean(false);

                        for (int i3 = min1; i3 > max1; i3--) {
                            double d5 = (i3 - 0.5 - y) / verticalRadius;
                            if (!skipChecker.shouldSkip(context, d3, d5, d4, i3) && (!carvingMask.get(i1, i3, i2) || isDebugEnabled(config))) {
                                carvingMask.set(i1, i3, i2);
                                mutableBlockPos.set(blockX, i3, blockZ);
                                flag |= this.carveBlock(
                                    context, config, chunk, biomeAccessor, carvingMask, mutableBlockPos, mutableBlockPos1, aquifer, mutableBoolean
                                );
                            }
                        }
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    protected boolean carveBlock(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeGetter,
        CarvingMask carvingMask,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos checkPos,
        Aquifer aquifer,
        MutableBoolean reachedSurface
    ) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(Blocks.GRASS_BLOCK) || blockState.is(Blocks.MYCELIUM)) {
            reachedSurface.setTrue();
        }

        if (!this.canReplaceBlock(config, blockState) && !isDebugEnabled(config)) {
            return false;
        } else {
            BlockState carveState = this.getCarveState(context, config, pos, aquifer);
            if (carveState == null) {
                return false;
            } else {
                chunk.setBlockState(pos, carveState);
                if (aquifer.shouldScheduleFluidUpdate() && !carveState.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(pos);
                }

                if (reachedSurface.isTrue()) {
                    checkPos.setWithOffset(pos, Direction.DOWN);
                    if (chunk.getBlockState(checkPos).is(Blocks.DIRT)) {
                        context.topMaterial(biomeGetter, chunk, checkPos, !carveState.getFluidState().isEmpty()).ifPresent(blockState1 -> {
                            chunk.setBlockState(checkPos, blockState1);
                            if (!blockState1.getFluidState().isEmpty()) {
                                chunk.markPosForPostprocessing(checkPos);
                            }
                        });
                    }
                }

                return true;
            }
        }
    }

    private @Nullable BlockState getCarveState(CarvingContext context, C config, BlockPos pos, Aquifer aquifer) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) {
            return LAVA.createLegacyBlock();
        } else {
            BlockState blockState = aquifer.computeSubstance(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()), 0.0);
            if (blockState == null) {
                return isDebugEnabled(config) ? config.debugSettings.getBarrierState() : null;
            } else {
                return isDebugEnabled(config) ? getDebugState(config, blockState) : blockState;
            }
        }
    }

    private static BlockState getDebugState(CarverConfiguration config, BlockState state) {
        if (state.is(Blocks.AIR)) {
            return config.debugSettings.getAirState();
        } else if (state.is(Blocks.WATER)) {
            BlockState waterState = config.debugSettings.getWaterState();
            return waterState.hasProperty(BlockStateProperties.WATERLOGGED) ? waterState.setValue(BlockStateProperties.WATERLOGGED, true) : waterState;
        } else {
            return state.is(Blocks.LAVA) ? config.debugSettings.getLavaState() : state;
        }
    }

    public abstract boolean carve(
        CarvingContext context,
        C config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    );

    public abstract boolean isStartChunk(C config, RandomSource random);

    protected boolean canReplaceBlock(C config, BlockState state) {
        return state.is(config.replaceable);
    }

    protected static boolean canReach(ChunkPos chunkPos, double x, double z, int branchIndex, int branchCount, float width) {
        double d = chunkPos.getMiddleBlockX();
        double d1 = chunkPos.getMiddleBlockZ();
        double d2 = x - d;
        double d3 = z - d1;
        double d4 = branchCount - branchIndex;
        double d5 = width + 2.0F + 16.0F;
        return d2 * d2 + d3 * d3 - d4 * d4 <= d5 * d5;
    }

    private static boolean isDebugEnabled(CarverConfiguration config) {
        return SharedConstants.DEBUG_CARVERS || config.debugSettings.isDebugMode();
    }

    public interface CarveSkipChecker {
        boolean shouldSkip(CarvingContext context, double relativeX, double relativeY, double relativeZ, int y);
    }
}
