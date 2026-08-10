package net.minecraft.world.entity.vehicle.boat;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ChestBoat extends AbstractChestBoat {
    public ChestBoat(EntityType<? extends ChestBoat> type, Level level, Supplier<Item> dropItem) {
        super(type, level, dropItem);
    }

    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return dimensions.height() / 3.0F;
    }
}
