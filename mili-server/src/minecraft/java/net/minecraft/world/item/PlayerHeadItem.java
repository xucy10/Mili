package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;

public class PlayerHeadItem extends StandingAndWallBlockItem {
    public PlayerHeadItem(Block block, Block wallBlock, Item.Properties properties) {
        super(block, wallBlock, Direction.DOWN, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResolvableProfile resolvableProfile = stack.get(DataComponents.PROFILE);
        return (Component)(resolvableProfile != null && resolvableProfile.name().isPresent()
            ? Component.translatable(this.descriptionId + ".named", resolvableProfile.name().get())
            : super.getName(stack));
    }
}
