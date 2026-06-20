package net.minecraft.world.item.crafting;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RecipePropertySet {
    public static final ResourceKey<? extends Registry<RecipePropertySet>> TYPE_KEY = ResourceKey.createRegistryKey(
        Identifier.withDefaultNamespace("recipe_property_set")
    );
    public static final ResourceKey<RecipePropertySet> SMITHING_BASE = registerVanilla("smithing_base");
    public static final ResourceKey<RecipePropertySet> SMITHING_TEMPLATE = registerVanilla("smithing_template");
    public static final ResourceKey<RecipePropertySet> SMITHING_ADDITION = registerVanilla("smithing_addition");
    public static final ResourceKey<RecipePropertySet> FURNACE_INPUT = registerVanilla("furnace_input");
    public static final ResourceKey<RecipePropertySet> BLAST_FURNACE_INPUT = registerVanilla("blast_furnace_input");
    public static final ResourceKey<RecipePropertySet> SMOKER_INPUT = registerVanilla("smoker_input");
    public static final ResourceKey<RecipePropertySet> CAMPFIRE_INPUT = registerVanilla("campfire_input");
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipePropertySet> STREAM_CODEC = Item.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(list -> new RecipePropertySet(Set.copyOf(list)), recipePropertySet -> List.copyOf(recipePropertySet.items));
    public static final RecipePropertySet EMPTY = new RecipePropertySet(Set.of());
    private final Set<Holder<Item>> items;

    private RecipePropertySet(Set<Holder<Item>> items) {
        this.items = items;
    }

    private static ResourceKey<RecipePropertySet> registerVanilla(String name) {
        return ResourceKey.create(TYPE_KEY, Identifier.withDefaultNamespace(name));
    }

    public boolean test(ItemStack stack) {
        return this.items.contains(stack.getItemHolder());
    }

    static RecipePropertySet create(Collection<Ingredient> ingredients) {
        Set<Holder<Item>> set = ingredients.stream().flatMap(Ingredient::items).collect(Collectors.toUnmodifiableSet());
        return new RecipePropertySet(set);
    }
}
