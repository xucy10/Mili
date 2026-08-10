package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

public class PistonStructureResolver {
    public static final int MAX_PUSH_DEPTH = 12;
    private final Level level;
    private final BlockPos pistonPos;
    private final boolean extending;
    private final BlockPos startPos;
    private final Direction pushDirection;
    private final List<BlockPos> toPush = Lists.newArrayList();
    private final List<BlockPos> toDestroy = Lists.newArrayList();
    private final Direction pistonDirection;

    public PistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = pistonDirection.getOpposite();
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    public boolean resolve() {
        this.toPush.clear();
        this.toDestroy.clear();
        BlockState blockState = this.level.getBlockState(this.startPos);
        if (!PistonBaseBlock.isPushable(blockState, this.level, this.startPos, this.pushDirection, false, this.pistonDirection)) {
            if (this.extending && blockState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(this.startPos);
                return true;
            } else {
                return false;
            }
        } else if (!this.addBlockLine(this.startPos, this.pushDirection)) {
            return false;
        } else {
            for (int i = 0; i < this.toPush.size(); i++) {
                BlockPos blockPos = this.toPush.get(i);
                if (isSticky(this.level.getBlockState(blockPos)) && !this.addBranchingBlocks(blockPos)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static boolean isSticky(BlockState state) {
        return state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
    }

    private static boolean canStickToEachOther(BlockState state1, BlockState state2) {
        return (!state1.is(Blocks.HONEY_BLOCK) || !state2.is(Blocks.SLIME_BLOCK))
            && (!state1.is(Blocks.SLIME_BLOCK) || !state2.is(Blocks.HONEY_BLOCK))
            && (isSticky(state1) || isSticky(state2));
    }

    private boolean addBlockLine(BlockPos originPos, Direction direction) {
        BlockState blockState = this.level.getBlockState(originPos);
        if (blockState.isAir()) {
            return true;
        } else if (!PistonBaseBlock.isPushable(blockState, this.level, originPos, this.pushDirection, false, direction)) {
            return true;
        } else if (originPos.equals(this.pistonPos)) {
            return true;
        } else if (this.toPush.contains(originPos)) {
            return true;
        } else {
            int i = 1;
            if (i + this.toPush.size() > 12) {
                return false;
            } else {
                while (isSticky(blockState)) {
                    BlockPos blockPos = originPos.relative(this.pushDirection.getOpposite(), i);
                    BlockState blockState1 = blockState;
                    blockState = this.level.getBlockState(blockPos);
                    if (blockState.isAir()
                        || !canStickToEachOther(blockState1, blockState)
                        || !PistonBaseBlock.isPushable(blockState, this.level, blockPos, this.pushDirection, false, this.pushDirection.getOpposite())
                        || blockPos.equals(this.pistonPos)) {
                        break;
                    }

                    if (++i + this.toPush.size() > 12) {
                        return false;
                    }
                }

                int i1 = 0;

                for (int i2 = i - 1; i2 >= 0; i2--) {
                    this.toPush.add(originPos.relative(this.pushDirection.getOpposite(), i2));
                    i1++;
                }

                int i2 = 1;

                while (true) {
                    BlockPos blockPos1 = originPos.relative(this.pushDirection, i2);
                    int index = this.toPush.indexOf(blockPos1);
                    if (index > -1) {
                        this.reorderListAtCollision(i1, index);

                        for (int i3 = 0; i3 <= index + i1; i3++) {
                            BlockPos blockPos2 = this.toPush.get(i3);
                            if (isSticky(this.level.getBlockState(blockPos2)) && !this.addBranchingBlocks(blockPos2)) {
                                return false;
                            }
                        }

                        return true;
                    }

                    blockState = this.level.getBlockState(blockPos1);
                    if (blockState.isAir()) {
                        return true;
                    }

                    if (!PistonBaseBlock.isPushable(blockState, this.level, blockPos1, this.pushDirection, true, this.pushDirection)
                        || blockPos1.equals(this.pistonPos)) {
                        return false;
                    }

                    if (blockState.getPistonPushReaction() == PushReaction.DESTROY) {
                        this.toDestroy.add(blockPos1);
                        return true;
                    }

                    if (this.toPush.size() >= 12) {
                        return false;
                    }

                    this.toPush.add(blockPos1);
                    i1++;
                    i2++;
                }
            }
        }
    }

    private void reorderListAtCollision(int offsets, int index) {
        List<BlockPos> list = Lists.newArrayList();
        List<BlockPos> list1 = Lists.newArrayList();
        List<BlockPos> list2 = Lists.newArrayList();
        list.addAll(this.toPush.subList(0, index));
        list1.addAll(this.toPush.subList(this.toPush.size() - offsets, this.toPush.size()));
        list2.addAll(this.toPush.subList(index, this.toPush.size() - offsets));
        this.toPush.clear();
        this.toPush.addAll(list);
        this.toPush.addAll(list1);
        this.toPush.addAll(list2);
    }

    private boolean addBranchingBlocks(BlockPos fromPos) {
        BlockState blockState = this.level.getBlockState(fromPos);

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != this.pushDirection.getAxis()) {
                BlockPos blockPos = fromPos.relative(direction);
                BlockState blockState1 = this.level.getBlockState(blockPos);
                if (canStickToEachOther(blockState1, blockState) && !this.addBlockLine(blockPos, direction)) {
                    return false;
                }
            }
        }

        return true;
    }

    public Direction getPushDirection() {
        return this.pushDirection;
    }

    public List<BlockPos> getToPush() {
        return this.toPush;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }
}
