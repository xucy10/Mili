package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;

public class SliceShape extends VoxelShape {
    private final VoxelShape delegate;
    private final Direction.Axis axis;
    private static final DoubleList SLICE_COORDS = new CubePointRange(1);

    public SliceShape(VoxelShape delegate, Direction.Axis axis, int index) {
        super(makeSlice(delegate.shape, axis, index));
        this.delegate = delegate;
        this.axis = axis;
        ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); // Paper - optimise collisions
    }

    private static DiscreteVoxelShape makeSlice(DiscreteVoxelShape shape, Direction.Axis axis, int index) {
        return new SubShape(
            shape,
            axis.choose(index, 0, 0),
            axis.choose(0, index, 0),
            axis.choose(0, 0, index),
            axis.choose(index + 1, shape.xSize, shape.xSize),
            axis.choose(shape.ySize, index + 1, shape.ySize),
            axis.choose(shape.zSize, shape.zSize, index + 1)
        );
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return axis == this.axis ? SLICE_COORDS : this.delegate.getCoords(axis);
    }
}
