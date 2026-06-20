package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record UseRemainder(ItemStack convertInto) {
    public static final Codec<UseRemainder> CODEC = ItemStack.CODEC.xmap(UseRemainder::new, UseRemainder::convertInto);
    public static final StreamCodec<RegistryFriendlyByteBuf, UseRemainder> STREAM_CODEC = StreamCodec.composite(
        ItemStack.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs::increaseDepth), UseRemainder::convertInto, UseRemainder::new // Paper - Track codec depth
    );

    public ItemStack convertIntoRemainder(ItemStack stack, int count, boolean hasInfiniteMaterials, UseRemainder.OnExtraCreatedRemainder onExtraCreated) {
        if (hasInfiniteMaterials) {
            return stack;
        } else if (stack.getCount() >= count) {
            return stack;
        } else {
            ItemStack itemStack = this.convertInto.copy();
            if (stack.isEmpty()) {
                return itemStack;
            } else {
                onExtraCreated.apply(itemStack);
                return stack;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            UseRemainder useRemainder = (UseRemainder)other;
            return ItemStack.matches(this.convertInto, useRemainder.convertInto);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(this.convertInto);
    }

    @FunctionalInterface
    public interface OnExtraCreatedRemainder {
        void apply(ItemStack stack);
    }
}
