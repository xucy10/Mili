package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public record PotDecorations(Optional<Item> back, Optional<Item> left, Optional<Item> right, Optional<Item> front) implements TooltipProvider {
    public static final PotDecorations EMPTY = new PotDecorations(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    public static final Codec<PotDecorations> CODEC = BuiltInRegistries.ITEM
        .byNameCodec()
        .sizeLimitedListOf(4)
        .xmap(PotDecorations::new, PotDecorations::ordered);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotDecorations> STREAM_CODEC = ByteBufCodecs.registry(Registries.ITEM)
        .apply(ByteBufCodecs.list(4))
        .map(PotDecorations::new, PotDecorations::ordered);

    private PotDecorations(List<Item> decorations) {
        this(getItem(decorations, 0), getItem(decorations, 1), getItem(decorations, 2), getItem(decorations, 3));
    }

    public PotDecorations(Item back, Item left, Item right, Item front) {
        this(List.of(back, left, right, front));
    }

    private static Optional<Item> getItem(List<Item> decorations, int index) {
        if (index >= decorations.size()) {
            return Optional.empty();
        } else {
            Item item = decorations.get(index);
            return item == Items.BRICK ? Optional.empty() : Optional.of(item);
        }
    }

    public List<Item> ordered() {
        return Stream.of(this.back, this.left, this.right, this.front).map(optional -> optional.orElse(Items.BRICK)).toList();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        if (!this.equals(EMPTY)) {
            tooltipAdder.accept(CommonComponents.EMPTY);
            addSideDetailsToTooltip(tooltipAdder, this.front);
            addSideDetailsToTooltip(tooltipAdder, this.left);
            addSideDetailsToTooltip(tooltipAdder, this.right);
            addSideDetailsToTooltip(tooltipAdder, this.back);
        }
    }

    private static void addSideDetailsToTooltip(Consumer<Component> tooltipAdder, Optional<Item> item) {
        tooltipAdder.accept(new ItemStack(item.orElse(Items.BRICK), 1).getHoverName().plainCopy().withStyle(ChatFormatting.GRAY));
    }
}
