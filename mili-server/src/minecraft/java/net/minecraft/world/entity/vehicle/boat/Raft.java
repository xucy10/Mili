package net.minecraft.world.entity.vehicle.boat;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class Raft extends AbstractBoat {
    public Raft(EntityType<? extends Raft> type, Level level, Supplier<Item> dropItem) {
        super(type, level, dropItem);
    }

    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return dimensions.height() * 0.8888889F;
    }
}
