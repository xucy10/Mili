package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemInput {
    private static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG = new Dynamic2CommandExceptionType(
        (item, quantity) -> Component.translatableEscape("arguments.item.overstacked", item, quantity)
    );
    private final Holder<Item> item;
    private final DataComponentPatch components;

    public ItemInput(Holder<Item> item, DataComponentPatch components) {
        this.item = item;
        this.components = components;
    }

    public Item getItem() {
        return this.item.value();
    }

    public ItemStack createItemStack(int count, boolean allowOversizedStacks) throws CommandSyntaxException {
        ItemStack itemStack = new ItemStack(this.item, count);
        itemStack.applyComponents(this.components);
        if (allowOversizedStacks && count > itemStack.getMaxStackSize()) {
            throw ERROR_STACK_TOO_BIG.create(this.getItemName(), itemStack.getMaxStackSize());
        } else {
            return itemStack;
        }
    }

    public String serialize(HolderLookup.Provider levelRegistry) {
        StringBuilder stringBuilder = new StringBuilder(this.getItemName());
        String string = this.serializeComponents(levelRegistry);
        if (!string.isEmpty()) {
            stringBuilder.append('[');
            stringBuilder.append(string);
            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    private String serializeComponents(HolderLookup.Provider levelRegistries) {
        DynamicOps<Tag> dynamicOps = levelRegistries.createSerializationContext(NbtOps.INSTANCE);
        return this.components.entrySet().stream().flatMap(entry -> {
            DataComponentType<?> dataComponentType = entry.getKey();
            Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
            if (key == null) {
                return Stream.empty();
            } else {
                Optional<?> optional = entry.getValue();
                if (optional.isPresent()) {
                    TypedDataComponent<?> typedDataComponent = TypedDataComponent.createUnchecked(dataComponentType, optional.get());
                    return typedDataComponent.encodeValue(dynamicOps).result().stream().map(tag -> key.toString() + "=" + tag);
                } else {
                    return Stream.of("!" + key.toString());
                }
            }
        }).collect(Collectors.joining(String.valueOf(',')));
    }

    private String getItemName() {
        return this.item.unwrapKey().<Object>map(ResourceKey::identifier).orElseGet(() -> "unknown[" + this.item + "]").toString();
    }
}
