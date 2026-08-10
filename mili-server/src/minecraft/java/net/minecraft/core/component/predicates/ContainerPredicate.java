package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.criterion.CollectionPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public record ContainerPredicate(Optional<CollectionPredicate<ItemStack, ItemPredicate>> items) implements SingleComponentItemPredicate<ItemContainerContents> {
    public static final Codec<ContainerPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                CollectionPredicate.<ItemStack, ItemPredicate>codec(ItemPredicate.CODEC).optionalFieldOf("items").forGetter(ContainerPredicate::items)
            )
            .apply(instance, ContainerPredicate::new)
    );

    @Override
    public DataComponentType<ItemContainerContents> componentType() {
        return DataComponents.CONTAINER;
    }

    @Override
    public boolean matches(ItemContainerContents value) {
        return !this.items.isPresent() || this.items.get().test(value.nonEmptyItems());
    }
}
