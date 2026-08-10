package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class CubeVoxelShape extends VoxelShape {
    protected CubeVoxelShape(DiscreteVoxelShape shape) {
        super(shape);
        ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); // Paper - optimise collisions
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return new CubePointRange(this.shape.getSize(axis));
    }

    @Override
    protected int findIndex(Direction.Axis axis, double position) {
        int size = this.shape.getSize(axis);
        return Mth.floor(Mth.clamp(position * size, -1.0, (double)size));
    }
}
