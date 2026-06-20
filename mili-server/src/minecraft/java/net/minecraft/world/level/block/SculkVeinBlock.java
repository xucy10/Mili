package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class SculkVeinBlock extends MultifaceSpreadeableBlock implements SculkBehaviour {
    public static final MapCodec<SculkVeinBlock> CODEC = simpleCodec(SculkVeinBlock::new);
    private final MultifaceSpreader veinSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
    private final MultifaceSpreader sameSpaceSpreader = new MultifaceSpreader(
        new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType.SAME_POSITION)
    );

    @Override
    public MapCodec<SculkVeinBlock> codec() {
        return CODEC;
    }

    public SculkVeinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.veinSpreader;
    }

    public MultifaceSpreader getSameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    public static boolean regrow(LevelAccessor level, BlockPos pos, BlockState state, Collection<Direction> directions) {
        boolean flag = false;
        BlockState blockState = Blocks.SCULK_VEIN.defaultBlockState();

        for (Direction direction : directions) {
            if (canAttachTo(level, pos, direction)) {
                blockState = blockState.setValue(getFaceProperty(direction), true);
                flag = true;
            }
        }

        if (!flag) {
            return false;
        } else {
            if (!state.getFluidState().isEmpty()) {
                blockState = blockState.setValue(MultifaceBlock.WATERLOGGED, true);
            }

            level.setBlock(pos, blockState, Block.UPDATE_ALL);
            return true;
        }
    }

    @Override
    public void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
        if (state.is(this)) {
            for (Direction direction : DIRECTIONS) {
                BooleanProperty faceProperty = getFaceProperty(direction);
                if (state.getValue(faceProperty) && level.getBlockState(pos.relative(direction)).is(Blocks.SCULK)) {
                    state = state.setValue(faceProperty, false);
                }
            }

            if (!hasAnyFace(state)) {
                FluidState fluidState = level.getFluidState(pos);
                state = (fluidState.isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState();
            }

            level.setBlock(pos, state, Block.UPDATE_ALL);
            SculkBehaviour.super.onDischarged(level, state, pos, random);
        }
    }

    @Override
    public int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    ) {
        if (shouldConvertBlocks && this.attemptPlaceSculk(spreader, level, cursor.getPos(), random, pos)) { // CraftBukkit - add source block
            return cursor.getCharge() - 1;
        } else {
            return random.nextInt(spreader.chargeDecayRate()) == 0 ? Mth.floor(cursor.getCharge() * 0.5F) : cursor.getCharge();
        }
    }

    private boolean attemptPlaceSculk(SculkSpreader spreader, LevelAccessor level, BlockPos pos, RandomSource random, BlockPos sourceBlock) { // CraftBukkit
        BlockState blockState = level.getBlockState(pos);
        TagKey<Block> tagKey = spreader.replaceableBlocks();

        for (Direction direction : Direction.allShuffled(random)) {
            if (hasFace(blockState, direction)) {
                BlockPos blockPos = pos.relative(direction);
                BlockState blockState1 = level.getBlockState(blockPos);
                if (blockState1.is(tagKey)) {
                    BlockState blockState2 = Blocks.SCULK.defaultBlockState();
                    // CraftBukkit start - Call BlockSpreadEvent
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(level, sourceBlock, blockPos, blockState2, Block.UPDATE_ALL)) {
                        return false;
                    }
                    // CraftBukkit end
                    Block.pushEntitiesUp(blockState1, blockState2, level, blockPos);
                    level.playSound(null, blockPos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    this.veinSpreader.spreadAll(blockState2, level, blockPos, spreader.isWorldGeneration());
                    Direction opposite = direction.getOpposite();

                    for (Direction direction1 : DIRECTIONS) {
                        if (direction1 != opposite) {
                            BlockPos blockPos1 = blockPos.relative(direction1);
                            BlockState blockState3 = level.getBlockState(blockPos1);
                            if (blockState3.is(this)) {
                                this.onDischarged(level, blockState3, blockPos1, random);
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasSubstrateAccess(LevelAccessor level, BlockState state, BlockPos pos) {
        if (!state.is(Blocks.SCULK_VEIN)) {
            return false;
        } else {
            for (Direction direction : DIRECTIONS) {
                if (hasFace(state, direction) && level.getBlockState(pos.relative(direction)).is(BlockTags.SCULK_REPLACEABLE)) {
                    return true;
                }
            }

            return false;
        }
    }

    class SculkVeinSpreaderConfig extends MultifaceSpreader.DefaultSpreaderConfig {
        private final MultifaceSpreader.SpreadType[] spreadTypes;

        public SculkVeinSpreaderConfig(final MultifaceSpreader.SpreadType... spreadTypes) {
            super(SculkVeinBlock.this);
            this.spreadTypes = spreadTypes;
        }

        @Override
        public boolean stateCanBeReplaced(BlockGetter level, BlockPos pos, BlockPos spreadPos, Direction direction, BlockState state) {
            BlockState blockState = level.getBlockState(spreadPos.relative(direction));
            if (!blockState.is(Blocks.SCULK) && !blockState.is(Blocks.SCULK_CATALYST) && !blockState.is(Blocks.MOVING_PISTON)) {
                if (pos.distManhattan(spreadPos) == 2) {
                    BlockPos blockPos = pos.relative(direction.getOpposite());
                    if (level.getBlockState(blockPos).isFaceSturdy(level, blockPos, direction)) {
                        return false;
                    }
                }

                FluidState fluidState = state.getFluidState();
                return (fluidState.isEmpty() || fluidState.is(Fluids.WATER))
                    && !state.is(BlockTags.FIRE)
                    && (state.canBeReplaced() || super.stateCanBeReplaced(level, pos, spreadPos, direction, state));
            } else {
                return false;
            }
        }

        @Override
        public MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return this.spreadTypes;
        }

        @Override
        public boolean isOtherBlockValidAsSource(BlockState otherBlock) {
            return !otherBlock.is(Blocks.SCULK_VEIN);
        }
    }
}
