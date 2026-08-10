package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.AbstractDoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

public class OffsetDoubleList extends AbstractDoubleList {
    public final DoubleList delegate; // Paper - optimise collisions - public
    public final double offset; // Paper - optimise collisions - public

    public OffsetDoubleList(DoubleList delegate, double offset) {
        this.delegate = delegate;
        this.offset = offset;
    }

    @Override
    public double getDouble(int value) {
        return this.delegate.getDouble(value) + this.offset;
    }

    @Override
    public int size() {
        return this.delegate.size();
    }
}
