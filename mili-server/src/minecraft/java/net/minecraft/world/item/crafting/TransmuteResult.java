package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public record TransmuteResult(Holder<Item> item, int count, DataComponentPatch components) {
    private static final Codec<TransmuteResult> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Item.CODEC.fieldOf("id").forGetter(TransmuteResult::item),
                ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(TransmuteResult::count),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(TransmuteResult::components)
            )
            .apply(instance, TransmuteResult::new)
    );
    public static final Codec<TransmuteResult> CODEC = Codec.<TransmuteResult, Holder<Item>>withAlternative(
            FULL_CODEC, Item.CODEC, holder -> new TransmuteResult(holder.value())
        )
        .validate(TransmuteResult::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteResult> STREAM_CODEC = StreamCodec.composite(
        Item.STREAM_CODEC,
        TransmuteResult::item,
        ByteBufCodecs.VAR_INT,
        TransmuteResult::count,
        DataComponentPatch.STREAM_CODEC,
        TransmuteResult::components,
        TransmuteResult::new
    );

    public TransmuteResult(Item item) {
        this(item.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY);
    }

    private static DataResult<TransmuteResult> validate(TransmuteResult result) {
        return ItemStack.validateStrict(new ItemStack(result.item, result.count, result.components)).map(stack -> result);
    }

    public ItemStack apply(ItemStack stack) {
    // Paper start - Option to prevent data components copy
        return apply(stack, true);
    }
    public ItemStack apply(ItemStack stack, boolean applyComponents) {
    // Paper end - Option to prevent data components copy
        ItemStack itemStack = stack.transmuteCopy(this.item.value(), this.count);
        if (applyComponents) itemStack.applyComponents(this.components); // Paper - Option to prevent data components copy
        return itemStack;
    }

    public boolean isResultUnchanged(ItemStack stack) {
        ItemStack itemStack = this.apply(stack);
        return itemStack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, itemStack);
    }

    public SlotDisplay display() {
        return new SlotDisplay.ItemStackSlotDisplay(new ItemStack(this.item, this.count, this.components));
    }
}
