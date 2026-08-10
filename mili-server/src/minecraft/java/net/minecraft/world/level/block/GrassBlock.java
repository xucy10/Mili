package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class GrassBlock extends SpreadingSnowyDirtBlock implements BonemealableBlock {
    public static final MapCodec<GrassBlock> CODEC = simpleCodec(GrassBlock::new);

    @Override
    public MapCodec<GrassBlock> codec() {
        return CODEC;
    }

    public GrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
        BlockPos blockPos = pos.above();
        BlockState blockState = Blocks.SHORT_GRASS.defaultBlockState();
        Optional<Holder.Reference<PlacedFeature>> optional = level.registryAccess()
            .lookupOrThrow(Registries.PLACED_FEATURE)
            .get(VegetationPlacements.GRASS_BONEMEAL);

        label51:
        for (int i = 0; i < 128; i++) {
            BlockPos blockPos1 = blockPos;

            for (int i1 = 0; i1 < i / 16; i1++) {
                blockPos1 = blockPos1.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (!level.getBlockState(blockPos1.below()).is(this) || level.getBlockState(blockPos1).isCollisionShapeFullBlock(level, blockPos1)) {
                    continue label51;
                }
            }

            BlockState blockState1 = level.getBlockState(blockPos1);
            if (blockState1.is(blockState.getBlock()) && random.nextInt(10) == 0) {
                BonemealableBlock bonemealableBlock = (BonemealableBlock)blockState.getBlock();
                if (bonemealableBlock.isValidBonemealTarget(level, blockPos1, blockState1)) {
                    bonemealableBlock.performBonemeal(level, random, blockPos1, blockState1);
                }
            }

            if (blockState1.isAir()) {
                Holder<PlacedFeature> holder;
                if (random.nextInt(8) == 0) {
                    List<ConfiguredFeature<?, ?>> flowerFeatures = level.getBiome(blockPos1).value().getGenerationSettings().getFlowerFeatures();
                    if (flowerFeatures.isEmpty()) {
                        continue;
                    }

                    int randomInt = random.nextInt(flowerFeatures.size());
                    holder = ((RandomPatchConfiguration)flowerFeatures.get(randomInt).config()).feature();
                } else {
                    if (!optional.isPresent()) {
                        continue;
                    }

                    holder = optional.get();
                }

                holder.value().place(level, level.getChunkSource().getGenerator(), random, blockPos1);
            }
        }
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
