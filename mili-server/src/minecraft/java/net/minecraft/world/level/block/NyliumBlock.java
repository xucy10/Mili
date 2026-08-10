package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.lighting.LightEngine;

public class NyliumBlock extends Block implements BonemealableBlock {
    public static final MapCodec<NyliumBlock> CODEC = simpleCodec(NyliumBlock::new);

    @Override
    public MapCodec<NyliumBlock> codec() {
        return CODEC;
    }

    protected NyliumBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean canBeNylium(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.above();
        BlockState blockState = level.getBlockState(blockPos);
        int lightBlockInto = LightEngine.getLightBlockInto(state, blockState, Direction.UP, blockState.getLightBlock());
        return lightBlockInto < 15;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canBeNylium(state, level, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, Blocks.NETHERRACK.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState blockState = level.getBlockState(pos);
        BlockPos blockPos = pos.above();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Registry<ConfiguredFeature<?, ?>> registry = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        if (blockState.is(Blocks.CRIMSON_NYLIUM)) {
            this.place(registry, NetherFeatures.CRIMSON_FOREST_VEGETATION_BONEMEAL, level, generator, random, blockPos);
        } else if (blockState.is(Blocks.WARPED_NYLIUM)) {
            this.place(registry, NetherFeatures.WARPED_FOREST_VEGETATION_BONEMEAL, level, generator, random, blockPos);
            this.place(registry, NetherFeatures.NETHER_SPROUTS_BONEMEAL, level, generator, random, blockPos);
            if (random.nextInt(8) == 0) {
                this.place(registry, NetherFeatures.TWISTING_VINES_BONEMEAL, level, generator, random, blockPos);
            }
        }
    }

    private void place(
        Registry<ConfiguredFeature<?, ?>> featureRegistry,
        ResourceKey<ConfiguredFeature<?, ?>> featureKey,
        ServerLevel level,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BlockPos pos
    ) {
        featureRegistry.get(featureKey).ifPresent(configuredFeature -> configuredFeature.value().place(level, chunkGenerator, random, pos));
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
