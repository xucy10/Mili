package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements StackedContents.IngredientInfo<io.papermc.paper.inventory.recipe.ItemOrExact>, Predicate<ItemStack> { // Paper - Improve exact choice recipe ingredients
    public static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC = ByteBufCodecs.holderSet(Registries.ITEM)
        .map(Ingredient::new, ingredient -> ingredient.values);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC = ByteBufCodecs.holderSet(Registries.ITEM)
        .map(
            items -> items.size() == 0 ? Optional.empty() : Optional.of(new Ingredient((HolderSet<Item>)items)),
            optional -> optional.map(ingredient -> ingredient.values).orElse(HolderSet.direct())
        );
    public static final Codec<HolderSet<Item>> NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, Item.CODEC, false);
    public static final Codec<Ingredient> CODEC = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC)
        .xmap(Ingredient::new, ingredient -> ingredient.values);
    public final HolderSet<Item> values;
    // CraftBukkit start
    @javax.annotation.Nullable
    private java.util.Set<ItemStack> itemStacks; // Paper - Improve exact choice recipe ingredients

    public boolean isExact() {
        return this.itemStacks != null;
    }

    @javax.annotation.Nullable
    public java.util.Set<ItemStack> itemStacks() { // Paper - Improve exact choice recipe ingredients
        return this.itemStacks;
    }

    public static Ingredient ofStacks(java.util.List<ItemStack> stacks) {
        Ingredient recipe = Ingredient.of(stacks.stream().map(ItemStack::getItem));
        // Paper start - Improve exact choice recipe ingredients
        recipe.itemStacks = net.minecraft.world.item.ItemStackLinkedSet.createTypeAndComponentsSet();
        recipe.itemStacks.addAll(stacks);
        recipe.itemStacks = java.util.Collections.unmodifiableSet(recipe.itemStacks);
        // Paper end - Improve exact choice recipe ingredients
        return recipe;
    }
    // CraftBukkit end

    private Ingredient(HolderSet<Item> values) {
        values.unwrap().ifRight(list -> {
            if (list.isEmpty()) {
                throw new UnsupportedOperationException("Ingredients can't be empty");
            } else if (list.contains(Items.AIR.builtInRegistryHolder())) {
                throw new UnsupportedOperationException("Ingredient can't contain air");
            }
        });
        this.values = values;
    }

    public static boolean testOptionalIngredient(Optional<Ingredient> ingredient, ItemStack stack) {
        return ingredient.<Boolean>map(ingredient1 -> ingredient1.test(stack)).orElseGet(stack::isEmpty);
    }

    @Deprecated
    public Stream<Holder<Item>> items() {
        return this.values.stream();
    }

    public boolean isEmpty() {
        return this.values.size() == 0;
    }

    @Override
    public boolean test(ItemStack stack) {
        // CraftBukkit start
        if (this.isExact()) {
            return this.itemStacks.contains(stack); // Paper - Improve exact choice recipe ingredients (hashing FTW!)
        }
        // CraftBukkit end
        return stack.is(this.values);
    }

    // Paper start - Improve exact choice recipe ingredients
    @Override
    public boolean acceptsItem(final io.papermc.paper.inventory.recipe.ItemOrExact itemOrExact) {
        return switch (itemOrExact) {
            case io.papermc.paper.inventory.recipe.ItemOrExact.Item(final Holder<Item> item) ->
                !this.isExact() && this.values.contains(item);
            case io.papermc.paper.inventory.recipe.ItemOrExact.Exact(final ItemStack exact) ->
                this.isExact() && this.itemStacks.contains(exact);
        };
        // Paper end - Improve exact choice recipe ingredients
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ingredient ingredient && Objects.equals(this.values, ingredient.values) && Objects.equals(this.itemStacks, ingredient.itemStacks); // CraftBukkit
    }

    public static Ingredient of(ItemLike item) {
        return new Ingredient(HolderSet.direct(item.asItem().builtInRegistryHolder()));
    }

    public static Ingredient of(ItemLike... items) {
        return of(Arrays.stream(items));
    }

    public static Ingredient of(Stream<? extends ItemLike> items) {
        return new Ingredient(HolderSet.direct(items.map(item -> item.asItem().builtInRegistryHolder()).toList()));
    }

    public static Ingredient of(HolderSet<Item> items) {
        return new Ingredient(items);
    }

    public SlotDisplay display() {
        // Paper start - show exact ingredients in recipe book
        if (this.isExact()) {
            return new SlotDisplay.Composite(this.itemStacks().stream().<SlotDisplay>map(SlotDisplay.ItemStackSlotDisplay::new).toList());
        }
        // Paper end - show exact ingredients in recipe book
        return (SlotDisplay)this.values
            .unwrap()
            .map(SlotDisplay.TagSlotDisplay::new, list -> new SlotDisplay.Composite(list.stream().map(Ingredient::displayForSingleItem).toList()));
    }

    public static SlotDisplay optionalIngredientToDisplay(Optional<Ingredient> ingredient) {
        return ingredient.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE);
    }

    private static SlotDisplay displayForSingleItem(Holder<Item> item) {
        SlotDisplay slotDisplay = new SlotDisplay.ItemSlotDisplay(item);
        ItemStack craftingRemainder = item.value().getCraftingRemainder();
        if (!craftingRemainder.isEmpty()) {
            SlotDisplay slotDisplay1 = new SlotDisplay.ItemStackSlotDisplay(craftingRemainder);
            return new SlotDisplay.WithRemainder(slotDisplay, slotDisplay1);
        } else {
            return slotDisplay;
        }
    }
}
