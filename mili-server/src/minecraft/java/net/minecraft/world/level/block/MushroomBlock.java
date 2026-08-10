package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

public class MushroomBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<MushroomBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(mushroomBlock -> mushroomBlock.feature), propertiesCodec()
            )
            .apply(instance, MushroomBlock::new)
    );
    private static final VoxelShape SHAPE = Block.column(6.0, 0.0, 6.0);
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    @Override
    public MapCodec<MushroomBlock> codec() {
        return CODEC;
    }

    public MushroomBlock(ResourceKey<ConfiguredFeature<?, ?>> feature, BlockBehaviour.Properties properties) {
        super(properties);
        this.feature = feature;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < (level.spigotConfig.mushroomModifier / (100.0F * 25))) { // Spigot - SPIGOT-7159: Better modifier resolution
            int i = 5;
            int i1 = 4;

            for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
                if (level.getBlockState(blockPos).is(this)) {
                    if (--i <= 0) {
                        return;
                    }
                }
            }

            BlockPos blockPos1 = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            final BlockPos sourcePos = pos; // Paper - Use correct source for mushroom block spread event

            for (int i2 = 0; i2 < 4; i2++) {
                if (level.isEmptyBlock(blockPos1) && state.canSurvive(level, blockPos1)) {
                    pos = blockPos1;
                }

                blockPos1 = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            }

            if (level.isEmptyBlock(blockPos1) && state.canSurvive(level, blockPos1)) {
                org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, sourcePos, blockPos1, state, Block.UPDATE_CLIENTS); // CraftBukkit // Paper - Use correct source for mushroom block spread event
            }
        }
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isSolidRender();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(BlockTags.MUSHROOM_GROW_BLOCK) || level.getRawBrightness(pos, 0) < 13 && this.mayPlaceOn(blockState, level, blockPos);
    }

    public boolean growMushroom(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        Optional<? extends Holder<ConfiguredFeature<?, ?>>> optional = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(this.feature);
        if (optional.isEmpty()) {
            return false;
        } else {
            level.removeBlock(pos, false);
            SaplingBlock.treeTypeRT.set((this == Blocks.BROWN_MUSHROOM) ? org.bukkit.TreeType.BROWN_MUSHROOM : org.bukkit.TreeType.RED_MUSHROOM); // CraftBukkit // Folia - region threading
            if (optional.get().value().place(level, level.getChunkSource().getGenerator(), random, pos)) {
                return true;
            } else {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                return false;
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.4;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.growMushroom(level, pos, state, random);
    }
}
