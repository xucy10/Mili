package net.minecraft.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class AirItem extends Item {
    public AirItem(Block block, Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return this.getName();
    }
}
