package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

public class RailState {
    private final Level level;
    private final BlockPos pos;
    private final BaseRailBlock block;
    private BlockState state;
    private final boolean isStraight;
    private final List<BlockPos> connections = Lists.newArrayList();

    // Paper start - Fix some rails connecting improperly
    public boolean isValid() {
        return this.level.getBlockState(this.pos).getBlock() == this.state.getBlock();
    }
    // Paper end - Fix some rails connecting improperly

    public RailState(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.block = (BaseRailBlock)state.getBlock();
        RailShape railShape = state.getValue(this.block.getShapeProperty());
        this.isStraight = this.block.isStraight();
        this.updateConnections(railShape);
    }

    public List<BlockPos> getConnections() {
        return this.connections;
    }

    private void updateConnections(RailShape shape) {
        this.connections.clear();
        switch (shape) {
            case NORTH_SOUTH:
                this.connections.add(this.pos.north());
                this.connections.add(this.pos.south());
                break;
            case EAST_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.east());
                break;
            case ASCENDING_EAST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.east().above());
                break;
            case ASCENDING_WEST:
                this.connections.add(this.pos.west().above());
                this.connections.add(this.pos.east());
                break;
            case ASCENDING_NORTH:
                this.connections.add(this.pos.north().above());
                this.connections.add(this.pos.south());
                break;
            case ASCENDING_SOUTH:
                this.connections.add(this.pos.north());
                this.connections.add(this.pos.south().above());
                break;
            case SOUTH_EAST:
                this.connections.add(this.pos.east());
                this.connections.add(this.pos.south());
                break;
            case SOUTH_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.south());
                break;
            case NORTH_WEST:
                this.connections.add(this.pos.west());
                this.connections.add(this.pos.north());
                break;
            case NORTH_EAST:
                this.connections.add(this.pos.east());
                this.connections.add(this.pos.north());
        }
    }

    private void removeSoftConnections() {
        for (int i = 0; i < this.connections.size(); i++) {
            RailState rail = this.getRail(this.connections.get(i));
            if (rail != null && rail.connectsTo(this)) {
                this.connections.set(i, rail.pos);
            } else {
                this.connections.remove(i--);
            }
        }
    }

    private boolean hasRail(BlockPos pos) {
        return BaseRailBlock.isRail(this.level, pos) || BaseRailBlock.isRail(this.level, pos.above()) || BaseRailBlock.isRail(this.level, pos.below());
    }

    private @Nullable RailState getRail(BlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        if (BaseRailBlock.isRail(blockState)) {
            return new RailState(this.level, pos, blockState);
        } else {
            BlockPos blockPos = pos.above();
            blockState = this.level.getBlockState(blockPos);
            if (BaseRailBlock.isRail(blockState)) {
                return new RailState(this.level, blockPos, blockState);
            } else {
                blockPos = pos.below();
                blockState = this.level.getBlockState(blockPos);
                return BaseRailBlock.isRail(blockState) ? new RailState(this.level, blockPos, blockState) : null;
            }
        }
    }

    private boolean connectsTo(RailState state) {
        return this.hasConnection(state.pos);
    }

    private boolean hasConnection(BlockPos pos) {
        for (int i = 0; i < this.connections.size(); i++) {
            BlockPos blockPos = this.connections.get(i);
            if (blockPos.getX() == pos.getX() && blockPos.getZ() == pos.getZ()) {
                return true;
            }
        }

        return false;
    }

    protected int countPotentialConnections() {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.hasRail(this.pos.relative(direction))) {
                i++;
            }
        }

        return i;
    }

    private boolean canConnectTo(RailState state) {
        return this.connectsTo(state) || this.connections.size() != 2;
    }

    private void connectTo(RailState state) {
        // Paper start - Fix some rails connecting improperly
        if (!this.isValid() || !state.isValid()) {
            return;
        }
        // Paper end - Fix some rails connecting improperly
        this.connections.add(state.pos);
        BlockPos blockPos = this.pos.north();
        BlockPos blockPos1 = this.pos.south();
        BlockPos blockPos2 = this.pos.west();
        BlockPos blockPos3 = this.pos.east();
        boolean hasConnection = this.hasConnection(blockPos);
        boolean hasConnection1 = this.hasConnection(blockPos1);
        boolean hasConnection2 = this.hasConnection(blockPos2);
        boolean hasConnection3 = this.hasConnection(blockPos3);
        RailShape railShape = null;
        if (hasConnection || hasConnection1) {
            railShape = RailShape.NORTH_SOUTH;
        }

        if (hasConnection2 || hasConnection3) {
            railShape = RailShape.EAST_WEST;
        }

        if (!this.isStraight) {
            if (hasConnection1 && hasConnection3 && !hasConnection && !hasConnection2) {
                railShape = RailShape.SOUTH_EAST;
            }

            if (hasConnection1 && hasConnection2 && !hasConnection && !hasConnection3) {
                railShape = RailShape.SOUTH_WEST;
            }

            if (hasConnection && hasConnection2 && !hasConnection1 && !hasConnection3) {
                railShape = RailShape.NORTH_WEST;
            }

            if (hasConnection && hasConnection3 && !hasConnection1 && !hasConnection2) {
                railShape = RailShape.NORTH_EAST;
            }
        }

        if (railShape == RailShape.NORTH_SOUTH) {
            if (BaseRailBlock.isRail(this.level, blockPos.above())) {
                railShape = RailShape.ASCENDING_NORTH;
            }

            if (BaseRailBlock.isRail(this.level, blockPos1.above())) {
                railShape = RailShape.ASCENDING_SOUTH;
            }
        }

        if (railShape == RailShape.EAST_WEST) {
            if (BaseRailBlock.isRail(this.level, blockPos3.above())) {
                railShape = RailShape.ASCENDING_EAST;
            }

            if (BaseRailBlock.isRail(this.level, blockPos2.above())) {
                railShape = RailShape.ASCENDING_WEST;
            }
        }

        if (railShape == null) {
            railShape = RailShape.NORTH_SOUTH;
        }

        this.state = this.state.setValue(this.block.getShapeProperty(), railShape);
        this.level.setBlock(this.pos, this.state, Block.UPDATE_ALL);
    }

    private boolean hasNeighborRail(BlockPos pos) {
        RailState rail = this.getRail(pos);
        if (rail == null) {
            return false;
        } else {
            rail.removeSoftConnections();
            return rail.canConnectTo(this);
        }
    }

    public RailState place(boolean powered, boolean alwaysPlace, RailShape shape) {
        BlockPos blockPos = this.pos.north();
        BlockPos blockPos1 = this.pos.south();
        BlockPos blockPos2 = this.pos.west();
        BlockPos blockPos3 = this.pos.east();
        boolean hasNeighborRail = this.hasNeighborRail(blockPos);
        boolean hasNeighborRail1 = this.hasNeighborRail(blockPos1);
        boolean hasNeighborRail2 = this.hasNeighborRail(blockPos2);
        boolean hasNeighborRail3 = this.hasNeighborRail(blockPos3);
        RailShape railShape = null;
        boolean flag = hasNeighborRail || hasNeighborRail1;
        boolean flag1 = hasNeighborRail2 || hasNeighborRail3;
        if (flag && !flag1) {
            railShape = RailShape.NORTH_SOUTH;
        }

        if (flag1 && !flag) {
            railShape = RailShape.EAST_WEST;
        }

        boolean flag2 = hasNeighborRail1 && hasNeighborRail3;
        boolean flag3 = hasNeighborRail1 && hasNeighborRail2;
        boolean flag4 = hasNeighborRail && hasNeighborRail3;
        boolean flag5 = hasNeighborRail && hasNeighborRail2;
        if (!this.isStraight) {
            if (flag2 && !hasNeighborRail && !hasNeighborRail2) {
                railShape = RailShape.SOUTH_EAST;
            }

            if (flag3 && !hasNeighborRail && !hasNeighborRail3) {
                railShape = RailShape.SOUTH_WEST;
            }

            if (flag5 && !hasNeighborRail1 && !hasNeighborRail3) {
                railShape = RailShape.NORTH_WEST;
            }

            if (flag4 && !hasNeighborRail1 && !hasNeighborRail2) {
                railShape = RailShape.NORTH_EAST;
            }
        }

        if (railShape == null) {
            if (flag && flag1) {
                railShape = shape;
            } else if (flag) {
                railShape = RailShape.NORTH_SOUTH;
            } else if (flag1) {
                railShape = RailShape.EAST_WEST;
            }

            if (!this.isStraight) {
                if (powered) {
                    if (flag2) {
                        railShape = RailShape.SOUTH_EAST;
                    }

                    if (flag3) {
                        railShape = RailShape.SOUTH_WEST;
                    }

                    if (flag4) {
                        railShape = RailShape.NORTH_EAST;
                    }

                    if (flag5) {
                        railShape = RailShape.NORTH_WEST;
                    }
                } else {
                    if (flag5) {
                        railShape = RailShape.NORTH_WEST;
                    }

                    if (flag4) {
                        railShape = RailShape.NORTH_EAST;
                    }

                    if (flag3) {
                        railShape = RailShape.SOUTH_WEST;
                    }

                    if (flag2) {
                        railShape = RailShape.SOUTH_EAST;
                    }
                }
            }
        }

        if (railShape == RailShape.NORTH_SOUTH) {
            if (BaseRailBlock.isRail(this.level, blockPos.above())) {
                railShape = RailShape.ASCENDING_NORTH;
            }

            if (BaseRailBlock.isRail(this.level, blockPos1.above())) {
                railShape = RailShape.ASCENDING_SOUTH;
            }
        }

        if (railShape == RailShape.EAST_WEST) {
            if (BaseRailBlock.isRail(this.level, blockPos3.above())) {
                railShape = RailShape.ASCENDING_EAST;
            }

            if (BaseRailBlock.isRail(this.level, blockPos2.above())) {
                railShape = RailShape.ASCENDING_WEST;
            }
        }

        if (railShape == null) {
            railShape = shape;
        }

        this.updateConnections(railShape);
        this.state = this.state.setValue(this.block.getShapeProperty(), railShape);
        if (alwaysPlace || this.level.getBlockState(this.pos) != this.state) {
            this.level.setBlock(this.pos, this.state, Block.UPDATE_ALL);
            // Paper start - Fix some rails connecting improperly
            if (!this.isValid()) {
                return this;
            }
            // Paper end - Fix some rails connecting improperly

            for (int i = 0; i < this.connections.size(); i++) {
                RailState rail = this.getRail(this.connections.get(i));
                if (rail != null && rail.isValid()) { // Paper - Fix some rails connecting improperly
                    rail.removeSoftConnections();
                    if (rail.canConnectTo(this)) {
                        rail.connectTo(this);
                    }
                }
            }
        }

        return this;
    }

    public BlockState getState() {
        return this.level.getBlockState(this.pos); // Paper - Fix some rails connecting improperly
    }
}
