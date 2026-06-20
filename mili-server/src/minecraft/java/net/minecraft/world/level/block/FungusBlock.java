package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FungusBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<FungusBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(fungusBlock -> fungusBlock.feature),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("grows_on").forGetter(fungusBlock -> fungusBlock.requiredBlock),
                propertiesCodec()
            )
            .apply(instance, FungusBlock::new)
    );
    private static final double BONEMEAL_SUCCESS_PROBABILITY = 0.4;
    private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 9.0);
    private final Block requiredBlock;
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    @Override
    public MapCodec<FungusBlock> codec() {
        return CODEC;
    }

    protected FungusBlock(ResourceKey<ConfiguredFeature<?, ?>> feature, Block requiredBlock, BlockBehaviour.Properties properties) {
        super(properties);
        this.feature = feature;
        this.requiredBlock = requiredBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.NYLIUM) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SOUL_SOIL) || super.mayPlaceOn(state, level, pos);
    }

    private Optional<? extends Holder<ConfiguredFeature<?, ?>>> getFeature(LevelReader level) {
        return level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(this.feature);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockState blockState = level.getBlockState(pos.below());
        return blockState.is(this.requiredBlock);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.4;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.getFeature(level)
            // CraftBukkit start
            .map((value) -> {
                if (this == Blocks.WARPED_FUNGUS) {
                    SaplingBlock.treeTypeRT.set(org.bukkit.TreeType.WARPED_FUNGUS); // Folia - region threading
                } else if (this == Blocks.CRIMSON_FUNGUS) {
                    SaplingBlock.treeTypeRT.set(org.bukkit.TreeType.CRIMSON_FUNGUS); // Folia - region threading
                }
                return value;
            })
            .ifPresent(holder -> holder.value().place(level, level.getChunkSource().getGenerator(), random, pos));
        // CraftBukkit end
    }
}
