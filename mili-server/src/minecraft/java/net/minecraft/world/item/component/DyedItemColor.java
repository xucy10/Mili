package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public record DyedItemColor(int rgb) implements TooltipProvider {
    public static final Codec<DyedItemColor> CODEC = ExtraCodecs.RGB_COLOR_CODEC.xmap(DyedItemColor::new, DyedItemColor::rgb);
    public static final StreamCodec<ByteBuf, DyedItemColor> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.INT, DyedItemColor::rgb, DyedItemColor::new);
    public static final int LEATHER_COLOR = -6265536;

    public static int getOrDefault(ItemStack stack, int defaultValue) {
        DyedItemColor dyedItemColor = stack.get(DataComponents.DYED_COLOR);
        return dyedItemColor != null ? ARGB.opaque(dyedItemColor.rgb()) : defaultValue;
    }

    public static ItemStack applyDyes(ItemStack stack, List<DyeItem> dyes) {
        if (!stack.is(ItemTags.DYEABLE)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = stack.copyWithCount(1);
            int i = 0;
            int i1 = 0;
            int i2 = 0;
            int i3 = 0;
            int i4 = 0;
            DyedItemColor dyedItemColor = itemStack.get(DataComponents.DYED_COLOR);
            if (dyedItemColor != null) {
                int i5 = ARGB.red(dyedItemColor.rgb());
                int i6 = ARGB.green(dyedItemColor.rgb());
                int i7 = ARGB.blue(dyedItemColor.rgb());
                i3 += Math.max(i5, Math.max(i6, i7));
                i += i5;
                i1 += i6;
                i2 += i7;
                i4++;
            }

            for (DyeItem dyeItem : dyes) {
                int i7 = dyeItem.getDyeColor().getTextureDiffuseColor();
                int i8 = ARGB.red(i7);
                int i9 = ARGB.green(i7);
                int i10 = ARGB.blue(i7);
                i3 += Math.max(i8, Math.max(i9, i10));
                i += i8;
                i1 += i9;
                i2 += i10;
                i4++;
            }

            int i5 = i / i4;
            int i6 = i1 / i4;
            int i7 = i2 / i4;
            float f = (float)i3 / i4;
            float f1 = Math.max(i5, Math.max(i6, i7));
            i5 = (int)(i5 * f / f1);
            i6 = (int)(i6 * f / f1);
            i7 = (int)(i7 * f / f1);
            int i10 = ARGB.color(0, i5, i6, i7);
            itemStack.set(DataComponents.DYED_COLOR, new DyedItemColor(i10));
            return itemStack;
        }
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        if (flag.isAdvanced()) {
            tooltipAdder.accept(Component.translatable("item.color", String.format(Locale.ROOT, "#%06X", this.rgb)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltipAdder.accept(Component.translatable("item.dyed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
