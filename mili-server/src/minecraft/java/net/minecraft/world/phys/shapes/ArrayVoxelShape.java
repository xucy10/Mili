package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;

public class ArrayVoxelShape extends VoxelShape {
    private final DoubleList xs;
    private final DoubleList ys;
    private final DoubleList zs;

    protected ArrayVoxelShape(DiscreteVoxelShape shape, double[] xs, double[] ys, double[] zs) {
        this(
            shape,
            DoubleArrayList.wrap(Arrays.copyOf(xs, shape.getXSize() + 1)),
            DoubleArrayList.wrap(Arrays.copyOf(ys, shape.getYSize() + 1)),
            DoubleArrayList.wrap(Arrays.copyOf(zs, shape.getZSize() + 1))
        );
    }

    public ArrayVoxelShape(DiscreteVoxelShape shape, DoubleList xs, DoubleList ys, DoubleList zs) { // Paper - optimise collisions - public
        super(shape);
        int i = shape.getXSize() + 1;
        int i1 = shape.getYSize() + 1;
        int i2 = shape.getZSize() + 1;
        if (i == xs.size() && i1 == ys.size() && i2 == zs.size()) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
        } else {
            throw (IllegalArgumentException)Util.pauseInIde(
                new IllegalArgumentException("Lengths of point arrays must be consistent with the size of the VoxelShape.")
            );
        }
        ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); // Paper - optimise collisions
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> this.xs;
            case Y -> this.ys;
            case Z -> this.zs;
        };
    }
}
