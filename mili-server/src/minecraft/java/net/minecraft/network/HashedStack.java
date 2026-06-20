package net.minecraft.network;

import com.mojang.datafixers.DataFixUtils;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface HashedStack {
    HashedStack EMPTY = new HashedStack() {
        @Override
        public String toString() {
            return "<empty>";
        }

        @Override
        public boolean matches(ItemStack stack, HashedPatchMap.HashGenerator hashGenerator) {
            return stack.isEmpty();
        }
    };
    StreamCodec<RegistryFriendlyByteBuf, HashedStack> STREAM_CODEC = ByteBufCodecs.optional(HashedStack.ActualItem.STREAM_CODEC)
        .map(
            optional -> DataFixUtils.orElse((Optional<? extends HashedStack>)optional, EMPTY),
            hashedStack -> hashedStack instanceof HashedStack.ActualItem actualItem ? Optional.of(actualItem) : Optional.empty()
        );

    boolean matches(ItemStack stack, HashedPatchMap.HashGenerator hashGenerator);

    static HashedStack create(ItemStack stack, HashedPatchMap.HashGenerator hashGenerator) {
        return (HashedStack)(stack.isEmpty()
            ? EMPTY
            : new HashedStack.ActualItem(stack.getItemHolder(), stack.getCount(), HashedPatchMap.create(stack.getComponentsPatch(), hashGenerator)));
    }

    public record ActualItem(Holder<Item> item, int count, HashedPatchMap components) implements HashedStack {
        public static final StreamCodec<RegistryFriendlyByteBuf, HashedStack.ActualItem> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ITEM),
            HashedStack.ActualItem::item,
            ByteBufCodecs.VAR_INT,
            HashedStack.ActualItem::count,
            HashedPatchMap.STREAM_CODEC,
            HashedStack.ActualItem::components,
            HashedStack.ActualItem::new
        );

        @Override
        public boolean matches(ItemStack stack, HashedPatchMap.HashGenerator hashGenerator) {
            return this.count == stack.getCount()
                && this.item.equals(stack.getItemHolder())
                && this.components.matches(stack.getComponentsPatch(), hashGenerator);
        }
    }
}
