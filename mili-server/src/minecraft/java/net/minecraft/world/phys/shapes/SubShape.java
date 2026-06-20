package net.minecraft.world.phys.shapes;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class SubShape extends DiscreteVoxelShape {
    private final DiscreteVoxelShape parent;
    private final int startX;
    private final int startY;
    private final int startZ;
    private final int endX;
    private final int endY;
    private final int endZ;

    protected SubShape(DiscreteVoxelShape parent, int startX, int startY, int startZ, int endX, int endY, int endZ) {
        super(endX - startX, endY - startY, endZ - startZ);
        this.parent = parent;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.parent.isFull(this.startX + x, this.startY + y, this.startZ + z);
    }

    @Override
    public void fill(int x, int y, int z) {
        this.parent.fill(this.startX + x, this.startY + y, this.startZ + z);
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return this.clampToShape(axis, this.parent.firstFull(axis));
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return this.clampToShape(axis, this.parent.lastFull(axis));
    }

    private int clampToShape(Direction.Axis axis, int value) {
        int i = axis.choose(this.startX, this.startY, this.startZ);
        int i1 = axis.choose(this.endX, this.endY, this.endZ);
        return Mth.clamp(value, i, i1) - i;
    }
}
