package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public class BonemealableFeaturePlacerBlock extends Block implements BonemealableBlock {
    public static final MapCodec<BonemealableFeaturePlacerBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(block -> block.feature), propertiesCodec())
            .apply(instance, BonemealableFeaturePlacerBlock::new)
    );
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    @Override
    public MapCodec<BonemealableFeaturePlacerBlock> codec() {
        return CODEC;
    }

    public BonemealableFeaturePlacerBlock(ResourceKey<ConfiguredFeature<?, ?>> feature, BlockBehaviour.Properties properties) {
        super(properties);
        this.feature = feature;
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
        level.registryAccess()
            .lookup(Registries.CONFIGURED_FEATURE)
            .flatMap(registry -> registry.get(this.feature))
            .ifPresent(reference -> reference.value().place(level, level.getChunkSource().getGenerator(), random, pos.above()));
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
