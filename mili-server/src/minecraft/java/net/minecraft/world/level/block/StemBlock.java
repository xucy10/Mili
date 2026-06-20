package net.minecraft.world.level.block;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StemBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<StemBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.BLOCK).fieldOf("fruit").forGetter(stemBlock -> stemBlock.fruit),
                ResourceKey.codec(Registries.BLOCK).fieldOf("attached_stem").forGetter(stemBlock -> stemBlock.attachedStem),
                ResourceKey.codec(Registries.ITEM).fieldOf("seed").forGetter(stemBlock -> stemBlock.seed),
                propertiesCodec()
            )
            .apply(instance, StemBlock::new)
    );
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    private static final VoxelShape[] SHAPES = Block.boxes(7, i -> Block.column(2.0, 0.0, 2 + i * 2));
    private final ResourceKey<Block> fruit;
    private final ResourceKey<Block> attachedStem;
    private final ResourceKey<Item> seed;

    @Override
    public MapCodec<StemBlock> codec() {
        return CODEC;
    }

    protected StemBlock(ResourceKey<Block> fruit, ResourceKey<Block> attachedStem, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
        super(properties);
        this.fruit = fruit;
        this.attachedStem = attachedStem;
        this.seed = seed;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            float growthSpeed = CropBlock.getGrowthSpeed(this, level, pos);
            if (random.nextFloat() < ((this == Blocks.PUMPKIN_STEM ? level.spigotConfig.pumpkinModifier : level.spigotConfig.melonModifier) / (100.0F * (Math.floor((25.0F / growthSpeed) + 1))))) { // Spigot - SPIGOT-7159: Better modifier resolution
                int ageValue = state.getValue(AGE);
                if (ageValue < 7) {
                    state = state.setValue(AGE, ageValue + 1);
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, state, Block.UPDATE_CLIENTS); // CraftBukkit
                } else {
                    Direction randomDirection = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos blockPos = pos.relative(randomDirection);
                    BlockState blockState = level.getBlockState(blockPos.below());
                    if (level.getBlockState(blockPos).isAir() && (blockState.is(Blocks.FARMLAND) || blockState.is(BlockTags.DIRT))) {
                        Registry<Block> registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
                        Optional<Block> optional = registry.getOptional(this.fruit);
                        Optional<Block> optional1 = registry.getOptional(this.attachedStem);
                        if (optional.isPresent() && optional1.isPresent()) {
                            // CraftBukkit start
                            if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, blockPos, optional.get().defaultBlockState(), Block.UPDATE_ALL)) {
                                return;
                            }
                            // CraftBukkit end
                            level.setBlockAndUpdate(pos, optional1.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, randomDirection));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(DataFixUtils.orElse(level.registryAccess().lookupOrThrow(Registries.ITEM).getOptional(this.seed), this));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) != 7;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int min = Math.min(7, state.getValue(AGE) + Mth.nextInt(level.random, 2, 5));
        BlockState blockState = state.setValue(AGE, min);
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(level, pos, blockState, Block.UPDATE_CLIENTS); // CraftBukkit
        if (min == 7) {
            blockState.randomTick(level, pos, level.random);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
