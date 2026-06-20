package net.minecraft.world.level.redstone;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.jspecify.annotations.Nullable;

public class ExperimentalRedstoneWireEvaluator extends RedstoneWireEvaluator {
    private final Deque<BlockPos> wiresToTurnOff = new ArrayDeque<>();
    private final Deque<BlockPos> wiresToTurnOn = new ArrayDeque<>();
    private final Object2IntMap<BlockPos> updatedWires = new Object2IntLinkedOpenHashMap<>();

    public ExperimentalRedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean updateShape) {
        Orientation initialOrientation = getInitialOrientation(level, orientation);
        this.calculateCurrentChanges(level, pos, initialOrientation);
        ObjectIterator<Entry<BlockPos>> objectIterator = this.updatedWires.object2IntEntrySet().iterator();

        for (boolean flag = true; objectIterator.hasNext(); flag = false) {
            Entry<BlockPos> entry = objectIterator.next();
            BlockPos blockPos = entry.getKey();
            int intValue = entry.getIntValue();
            int i = unpackPower(intValue);
            BlockState blockState = level.getBlockState(blockPos);
            // CraftBukkit start
            int oldPower = blockState.getValue(RedStoneWireBlock.POWER); // Paper - Call BlockRedstoneEvent properly; get the previous power from the right state
            if (oldPower != i) {
                org.bukkit.event.block.BlockRedstoneEvent event = new org.bukkit.event.block.BlockRedstoneEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos), oldPower, i);
                level.getCraftServer().getPluginManager().callEvent(event);

                i = event.getNewCurrent();
            }
            if (blockState.is(this.wireBlock) && oldPower != i) {
                // CraftBukkit end
                int i1 = Block.UPDATE_CLIENTS;
                if (!updateShape || !flag) {
                    i1 |= Block.UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE;
                }

                level.setBlock(blockPos, blockState.setValue(RedStoneWireBlock.POWER, i), i1);
            } else {
                objectIterator.remove();
            }
        }

        this.causeNeighborUpdates(level);
    }

    private void causeNeighborUpdates(Level level) {
        this.updatedWires.forEach((blockPos, i) -> {
            Orientation orientation = unpackOrientation(i);
            BlockState blockState = level.getBlockState(blockPos);

            for (Direction direction : orientation.getDirections()) {
                if (isConnected(blockState, direction)) {
                    BlockPos blockPos1 = blockPos.relative(direction);
                    BlockState blockState1 = level.getBlockState(blockPos1);
                    Orientation orientation1 = orientation.withFrontPreserveUp(direction);
                    level.neighborChanged(blockState1, blockPos1, this.wireBlock, orientation1, false);
                    if (blockState1.isRedstoneConductor(level, blockPos1)) {
                        for (Direction direction1 : orientation1.getDirections()) {
                            if (direction1 != direction.getOpposite()) {
                                level.neighborChanged(blockPos1.relative(direction1), this.wireBlock, orientation1.withFrontPreserveUp(direction1));
                            }
                        }
                    }
                }
            }
        });
        if (level instanceof ServerLevel serverLevel && serverLevel.debugSynchronizers().hasAnySubscriberFor(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS)) {
            this.updatedWires
                .forEach(
                    (blockPos, i) -> serverLevel.debugSynchronizers()
                        .sendBlockValue(blockPos, DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, unpackOrientation(i))
                );
        }
    }

    private static boolean isConnected(BlockState state, Direction direction) {
        EnumProperty<RedstoneSide> enumProperty = RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction);
        return enumProperty == null ? direction == Direction.DOWN : state.getValue(enumProperty).isConnected();
    }

    private static Orientation getInitialOrientation(Level level, @Nullable Orientation orientation) {
        Orientation orientation1;
        if (orientation != null) {
            orientation1 = orientation;
        } else {
            orientation1 = Orientation.random(level.random);
        }

        return orientation1.withUp(Direction.UP).withSideBias(Orientation.SideBias.LEFT);
    }

    private void calculateCurrentChanges(Level level, BlockPos pos, Orientation orientation) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(this.wireBlock)) {
            this.setPower(pos, blockState.getValue(RedStoneWireBlock.POWER), orientation);
            this.wiresToTurnOff.add(pos);
        } else {
            this.propagateChangeToNeighbors(level, pos, 0, orientation, true);
        }

        while (!this.wiresToTurnOff.isEmpty()) {
            BlockPos blockPos = this.wiresToTurnOff.removeFirst();
            int _int = this.updatedWires.getInt(blockPos);
            Orientation orientation1 = unpackOrientation(_int);
            int i = unpackPower(_int);
            int blockSignal = this.getBlockSignal(level, blockPos);
            int incomingWireSignal = this.getIncomingWireSignal(level, blockPos);
            int max = Math.max(blockSignal, incomingWireSignal);
            int i1;
            if (max < i) {
                if (blockSignal > 0 && !this.wiresToTurnOn.contains(blockPos)) {
                    this.wiresToTurnOn.add(blockPos);
                }

                i1 = 0;
            } else {
                i1 = max;
            }

            if (i1 != i) {
                this.setPower(blockPos, i1, orientation1);
            }

            this.propagateChangeToNeighbors(level, blockPos, i1, orientation1, i > max);
        }

        while (!this.wiresToTurnOn.isEmpty()) {
            BlockPos blockPosx = this.wiresToTurnOn.removeFirst();
            int _intx = this.updatedWires.getInt(blockPosx);
            int i2 = unpackPower(_intx);
            int ix = this.getBlockSignal(level, blockPosx);
            int blockSignalx = this.getIncomingWireSignal(level, blockPosx);
            int incomingWireSignalx = Math.max(ix, blockSignalx);
            Orientation orientation2 = unpackOrientation(_intx);
            if (incomingWireSignalx > i2) {
                this.setPower(blockPosx, incomingWireSignalx, orientation2);
            } else if (incomingWireSignalx < i2) {
                throw new IllegalStateException("Turning off wire while trying to turn it on. Should not happen.");
            }

            this.propagateChangeToNeighbors(level, blockPosx, incomingWireSignalx, orientation2, false);
        }
    }

    private static int packOrientationAndPower(Orientation orientation, int power) {
        return orientation.getIndex() << 4 | power;
    }

    private static Orientation unpackOrientation(int data) {
        return Orientation.fromIndex(data >> 4);
    }

    private static int unpackPower(int data) {
        return data & 15;
    }

    private void setPower(BlockPos pos, int power, Orientation orientation) {
        this.updatedWires
            .compute(
                pos,
                (blockPos, integer) -> integer == null
                    ? packOrientationAndPower(orientation, power)
                    : packOrientationAndPower(unpackOrientation(integer), power)
            );
    }

    private void propagateChangeToNeighbors(Level level, BlockPos pos, int power, Orientation orientation, boolean canTurnOff) {
        for (Direction direction : orientation.getHorizontalDirections()) {
            BlockPos blockPos = pos.relative(direction);
            this.enqueueNeighborWire(level, blockPos, power, orientation.withFront(direction), canTurnOff);
        }

        for (Direction direction : orientation.getVerticalDirections()) {
            BlockPos blockPos = pos.relative(direction);
            boolean isRedstoneConductor = level.getBlockState(blockPos).isRedstoneConductor(level, blockPos);

            for (Direction direction1 : orientation.getHorizontalDirections()) {
                BlockPos blockPos1 = pos.relative(direction1);
                if (direction == Direction.UP && !isRedstoneConductor) {
                    BlockPos blockPos2 = blockPos.relative(direction1);
                    this.enqueueNeighborWire(level, blockPos2, power, orientation.withFront(direction1), canTurnOff);
                } else if (direction == Direction.DOWN && !level.getBlockState(blockPos1).isRedstoneConductor(level, blockPos1)) {
                    BlockPos blockPos2 = blockPos.relative(direction1);
                    this.enqueueNeighborWire(level, blockPos2, power, orientation.withFront(direction1), canTurnOff);
                }
            }
        }
    }

    private void enqueueNeighborWire(Level level, BlockPos pos, int power, Orientation orientation, boolean canTurnOff) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.is(this.wireBlock)) {
            int wireSignal = this.getWireSignal(pos, blockState);
            if (wireSignal < power - 1 && !this.wiresToTurnOn.contains(pos)) {
                this.wiresToTurnOn.add(pos);
                this.setPower(pos, wireSignal, orientation);
            }

            if (canTurnOff && wireSignal > power && !this.wiresToTurnOff.contains(pos)) {
                this.wiresToTurnOff.add(pos);
                this.setPower(pos, wireSignal, orientation);
            }
        }
    }

    @Override
    protected int getWireSignal(BlockPos pos, BlockState state) {
        int orDefault = this.updatedWires.getOrDefault(pos, -1);
        return orDefault != -1 ? unpackPower(orDefault) : super.getWireSignal(pos, state);
    }
}
