package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PistonBaseBlock extends DirectionalBlock {
    public static final MapCodec<PistonBaseBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.fieldOf("sticky").forGetter(pistonBaseBlock -> pistonBaseBlock.isSticky), propertiesCodec())
            .apply(instance, PistonBaseBlock::new)
    );
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    public static final int TRIGGER_EXTEND = 0;
    public static final int TRIGGER_CONTRACT = 1;
    public static final int TRIGGER_DROP = 2;
    public static final int PLATFORM_THICKNESS = 4;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.boxZ(16.0, 4.0, 16.0));
    private final boolean isSticky;

    @Override
    public MapCodec<PistonBaseBlock> codec() {
        return CODEC;
    }

    public PistonBaseBlock(boolean isSticky, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTENDED, false));
        this.isSticky = isSticky;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(EXTENDED) ? SHAPES.get(state.getValue(FACING)) : Shapes.block();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide()) {
            this.checkIfExtend(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            this.checkIfExtend(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) == null) {
                this.checkIfExtend(level, pos, state);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite()).setValue(EXTENDED, false);
    }

    private void checkIfExtend(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        boolean neighborSignal = this.getNeighborSignal(level, pos, direction);
        if (neighborSignal && !state.getValue(EXTENDED)) {
            if (new PistonStructureResolver(level, pos, direction, true).resolve()) {
                level.blockEvent(pos, this, 0, direction.get3DDataValue());
            }
        } else if (!neighborSignal && state.getValue(EXTENDED)) {
            BlockPos blockPos = pos.relative(direction, 2);
            BlockState blockState = level.getBlockState(blockPos);
            int i = 1;
            if (blockState.is(Blocks.MOVING_PISTON)
                && blockState.getValue(FACING) == direction
                && level.getBlockEntity(blockPos) instanceof PistonMovingBlockEntity pistonMovingBlockEntity
                && pistonMovingBlockEntity.isExtending()
                && (
                    pistonMovingBlockEntity.getProgress(0.0F) < 0.5F
                        || level.getRedstoneGameTime() == pistonMovingBlockEntity.getLastTicked() // Folia - region threading
                        || ((ServerLevel)level).isHandlingTick()
                )) {
                i = 2;
            }

            level.blockEvent(pos, this, i, direction.get3DDataValue());
        }
    }

    private boolean getNeighborSignal(SignalGetter level, BlockPos pos, Direction direction) {
        for (Direction direction1 : Direction.values()) {
            if (direction1 != direction && level.hasSignal(pos.relative(direction1), direction1)) {
                return true;
            }
        }

        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        } else {
            BlockPos blockPos = pos.above();

            for (Direction direction2 : Direction.values()) {
                if (direction2 != Direction.DOWN && level.hasSignal(blockPos.relative(direction2), direction2)) {
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction direction = state.getValue(FACING);
        // Paper start - Protect Bedrock and End Portal/Frames from being destroyed; prevent retracting when we're facing the wrong way (we were replaced before retraction could occur)
        Direction directionQueuedAs = Direction.from3DDataValue(param & 7); // Paper - copied from below
        if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits && direction != directionQueuedAs) {
            return false;
        }
        // Paper end - Protect Bedrock and End Portal/Frames from being destroyed
        BlockState blockState = state.setValue(EXTENDED, true);
        if (!level.isClientSide()) {
            boolean neighborSignal = this.getNeighborSignal(level, pos, direction);
            if (neighborSignal && (id == 1 || id == 2)) {
                level.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
                return false;
            }

            if (!neighborSignal && id == 0) {
                return false;
            }
        }

        if (id == 0) {
            if (!this.moveBlocks(level, pos, direction, true)) {
                return false;
            }

            level.setBlock(pos, blockState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.25F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(blockState));
        } else if (id == 1 || id == 2) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof PistonMovingBlockEntity) {
                ((PistonMovingBlockEntity)blockEntity).finalTick();
            }

            BlockState blockState1 = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, direction)
                .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
            // Paper start - Fix sticky pistons and BlockPistonRetractEvent; Move empty piston retract call to fix multiple event fires
            if (!this.isSticky) {
                if (!new org.bukkit.event.block.BlockPistonRetractEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), java.util.Collections.emptyList(), org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(direction)).callEvent()) {
                    return false;
                }
            }
            // Paper end - Fix sticky pistons and BlockPistonRetractEvent
            level.setBlock(pos, blockState1, Block.UPDATE_NONE | Block.UPDATE_KNOWN_SHAPE);
            level.setBlockEntity(
                MovingPistonBlock.newMovingBlockEntity(
                    pos, blockState1, this.defaultBlockState().setValue(FACING, Direction.from3DDataValue(param & 7)), direction, false, true // Paper - Protect Bedrock and End Portal/Frames from being destroyed; diff on change
                )
            );
            level.updateNeighborsAt(pos, blockState1.getBlock());
            blockState1.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
            if (this.isSticky) {
                BlockPos blockPos = pos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
                BlockState blockState2 = level.getBlockState(blockPos);
                boolean flag = false;
                if (blockState2.is(Blocks.MOVING_PISTON)
                    && level.getBlockEntity(blockPos) instanceof PistonMovingBlockEntity pistonMovingBlockEntity
                    && pistonMovingBlockEntity.getDirection() == direction
                    && pistonMovingBlockEntity.isExtending()) {
                    pistonMovingBlockEntity.finalTick();
                    flag = true;
                }

                if (!flag) {
                    if (id != 1
                        || blockState2.isAir()
                        || !isPushable(blockState2, level, blockPos, direction.getOpposite(), false, direction)
                        || blockState2.getPistonPushReaction() != PushReaction.NORMAL
                            && !blockState2.is(Blocks.PISTON)
                            && !blockState2.is(Blocks.STICKY_PISTON)) {
                        // Paper start - Fix sticky pistons and BlockPistonRetractEvent; fire BlockPistonRetractEvent for sticky pistons retracting nothing (air)
                        if (id == TRIGGER_CONTRACT && blockState1.isAir()) {
                            if (!new org.bukkit.event.block.BlockPistonRetractEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), java.util.Collections.emptyList(), org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(direction)).callEvent()) {
                                return false;
                            }
                        }
                        // Paper end - Fix sticky pistons and BlockPistonRetractEvent
                        level.removeBlock(pos.relative(direction), false);
                    } else {
                        this.moveBlocks(level, pos, direction, false);
                    }
                }
            } else {
                // Paper start - Protect Bedrock and End Portal/Frames from being destroyed; fix headless pistons breaking blocks
                BlockPos headPos = pos.relative(direction);
                if (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPermanentBlockBreakExploits || level.getBlockState(headPos) == Blocks.PISTON_HEAD.defaultBlockState().setValue(FACING, direction)) { // double check to make sure we're not a headless piston
                    level.removeBlock(headPos, false);
                } else {
                    ((ServerLevel) level).getChunkSource().blockChanged(headPos); // ... fix client desync
                }
                // Paper end - Protect Bedrock and End Portal/Frames from being destroyed
            }

            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.15F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(blockState1));
        }

        return true;
    }

    public static boolean isPushable(BlockState state, Level level, BlockPos pos, Direction movementDirection, boolean allowDestroy, Direction pistonFacing) {
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.getWorldBorder().isWithinBounds(pos) || !level.getWorldBorder().isWithinBounds(pos.relative(movementDirection))) { // Paper - Fix piston world border check
            return false;
        } else if (state.isAir()) {
            return true;
        } else if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR) || state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return false;
        } else if (movementDirection == Direction.DOWN && pos.getY() == level.getMinY()) {
            return false;
        } else if (movementDirection == Direction.UP && pos.getY() == level.getMaxY()) {
            return false;
        } else {
            if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
                if (state.getDestroySpeed(level, pos) == -1.0F) {
                    return false;
                }

                switch (state.getPistonPushReaction()) {
                    case BLOCK:
                        return false;
                    case DESTROY:
                        return allowDestroy;
                    case PUSH_ONLY:
                        return movementDirection == pistonFacing;
                }
            } else if (state.getValue(EXTENDED)) {
                return false;
            }

            return !state.hasBlockEntity();
        }
    }

    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending) {
        BlockPos blockPos = pos.relative(facing);
        if (!extending && level.getBlockState(blockPos).is(Blocks.PISTON_HEAD)) {
            level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE | Block.UPDATE_KNOWN_SHAPE);
        }

        PistonStructureResolver pistonStructureResolver = new PistonStructureResolver(level, pos, facing, extending);
        if (!pistonStructureResolver.resolve()) {
            return false;
        } else {
            Map<BlockPos, BlockState> map = Maps.newHashMap();
            List<BlockPos> toPush = pistonStructureResolver.getToPush();
            List<BlockState> list = Lists.newArrayList();

            for (BlockPos blockPos1 : toPush) {
                BlockState blockState = level.getBlockState(blockPos1);
                list.add(blockState);
                map.put(blockPos1, blockState);
            }

            List<BlockPos> toDestroy = pistonStructureResolver.getToDestroy();
            BlockState[] blockStates = new BlockState[toPush.size() + toDestroy.size()];
            Direction direction = extending ? facing : facing.getOpposite();
            int i = 0;
            // CraftBukkit start
            final org.bukkit.block.Block bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);

            final List<BlockPos> moved = pistonStructureResolver.getToPush();
            final List<BlockPos> broken = pistonStructureResolver.getToDestroy();

            List<org.bukkit.block.Block> blocks = new java.util.AbstractList<>() {

                @Override
                public int size() {
                    return moved.size() + broken.size();
                }

                @Override
                public org.bukkit.block.Block get(int index) {
                    if (index >= this.size() || index < 0) {
                        throw new ArrayIndexOutOfBoundsException(index);
                    }

                    BlockPos pos = index < moved.size() ? moved.get(index) : broken.get(index - moved.size());
                    return org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                }
            };

            final org.bukkit.event.block.BlockPistonEvent event;
            if (extending) {
                event = new org.bukkit.event.block.BlockPistonExtendEvent(bukkitBlock, blocks, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(direction));
            } else {
                event = new org.bukkit.event.block.BlockPistonRetractEvent(bukkitBlock, blocks, org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(direction));
            }
            if (!event.callEvent()) {
                for (BlockPos brokenPos : broken) {
                    level.sendBlockUpdated(brokenPos, Blocks.AIR.defaultBlockState(), level.getBlockState(brokenPos), Block.UPDATE_ALL);
                }
                for (BlockPos movedPos : moved) {
                    level.sendBlockUpdated(movedPos, Blocks.AIR.defaultBlockState(), level.getBlockState(movedPos), Block.UPDATE_ALL);
                    movedPos = movedPos.relative(direction);
                    level.sendBlockUpdated(movedPos, Blocks.AIR.defaultBlockState(), level.getBlockState(movedPos), Block.UPDATE_ALL);
                }
                return false;
            }
            // CraftBukkit end

            for (int i1 = toDestroy.size() - 1; i1 >= 0; i1--) {
                BlockPos blockPos2 = toDestroy.get(i1);
                BlockState blockState1 = level.getBlockState(blockPos2);
                BlockEntity blockEntity = blockState1.hasBlockEntity() ? level.getBlockEntity(blockPos2) : null;
                dropResources(blockState1, level, blockPos2, blockEntity, pos); // Paper - Add BlockBreakBlockEvent
                if (!blockState1.is(BlockTags.FIRE) && level.isClientSide()) {
                    level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos2, getId(blockState1));
                }

                level.setBlock(blockPos2, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockPos2, GameEvent.Context.of(blockState1));
                blockStates[i++] = blockState1;
            }

            for (int i1 = toPush.size() - 1; i1 >= 0; i1--) {
                // Paper start - fix a variety of piston desync dupes
                boolean allowDesync = !fun.bm.mili.carpet.config.modules.GeneralCompatConfig.tntDupingFix;
                BlockPos blockPos2;
                BlockPos oldPos = blockPos2 = toPush.get(i1);
                BlockState blockState1 = allowDesync ? level.getBlockState(oldPos) : null;
                // Paper end - fix a variety of piston desync dupes
                blockPos2 = blockPos2.relative(direction);
                map.remove(blockPos2);
                BlockState blockState2 = Blocks.MOVING_PISTON.defaultBlockState().setValue(FACING, facing);
                level.setBlock(blockPos2, blockState2, Block.UPDATE_NONE | Block.UPDATE_MOVE_BY_PISTON);
                // Paper start - fix a variety of piston desync dupes
                if (!allowDesync) {
                    blockState1 = level.getBlockState(oldPos);
                    map.replace(oldPos, blockState1);
                }
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockPos2, blockState2, allowDesync ? list.get(i1) : blockState1, facing, extending, false));
                if (!allowDesync) {
                    level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SKIP_ON_PLACE); // set air to prevent later physics updates from seeing this block
                }
                // Paper end - fix a variety of piston desync dupes
                blockStates[i++] = blockState1;
            }

            if (extending) {
                PistonType pistonType = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
                BlockState blockState3 = Blocks.PISTON_HEAD
                    .defaultBlockState()
                    .setValue(PistonHeadBlock.FACING, facing)
                    .setValue(PistonHeadBlock.TYPE, pistonType);
                BlockState blockState1 = Blocks.MOVING_PISTON
                    .defaultBlockState()
                    .setValue(MovingPistonBlock.FACING, facing)
                    .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
                map.remove(blockPos);
                level.setBlock(blockPos, blockState1, Block.UPDATE_NONE | Block.UPDATE_MOVE_BY_PISTON);
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockPos, blockState1, blockState3, facing, true, true));
            }

            BlockState blockState4 = Blocks.AIR.defaultBlockState();

            for (BlockPos blockPos3 : map.keySet()) {
                level.setBlock(blockPos3, blockState4, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }

            for (Entry<BlockPos, BlockState> entry : map.entrySet()) {
                BlockPos blockPos4 = entry.getKey();
                BlockState blockState5 = entry.getValue();
                blockState5.updateIndirectNeighbourShapes(level, blockPos4, Block.UPDATE_CLIENTS);
                blockState4.updateNeighbourShapes(level, blockPos4, Block.UPDATE_CLIENTS);
                blockState4.updateIndirectNeighbourShapes(level, blockPos4, Block.UPDATE_CLIENTS);
            }

            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, pistonStructureResolver.getPushDirection(), null);
            i = 0;

            for (int i2 = toDestroy.size() - 1; i2 >= 0; i2--) {
                BlockState blockState2 = blockStates[i++];
                BlockPos blockPos5 = toDestroy.get(i2);
                if (level instanceof ServerLevel serverLevel) {
                    blockState2.affectNeighborsAfterRemoval(serverLevel, blockPos5, false);
                }

                blockState2.updateIndirectNeighbourShapes(level, blockPos5, Block.UPDATE_CLIENTS);
                level.updateNeighborsAt(blockPos5, blockState2.getBlock(), orientation);
            }

            for (int i2 = toPush.size() - 1; i2 >= 0; i2--) {
                level.updateNeighborsAt(toPush.get(i2), blockStates[i++].getBlock(), orientation);
            }

            if (extending) {
                level.updateNeighborsAt(blockPos, Blocks.PISTON_HEAD, orientation);
            }

            return true;
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTENDED);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(EXTENDED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
