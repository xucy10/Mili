package net.minecraft.world.level.block.entity;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import java.util.Collections;
import java.util.SequencedSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public class FuelValues {
    private final Object2IntSortedMap<Item> values;

    FuelValues(Object2IntSortedMap<Item> values) {
        this.values = values;
    }

    public boolean isFuel(ItemStack stack) {
        return this.values.containsKey(stack.getItem());
    }

    public SequencedSet<Item> fuelItems() {
        return Collections.unmodifiableSequencedSet(this.values.keySet());
    }

    public int burnDuration(ItemStack stack) {
        return stack.isEmpty() ? 0 : this.values.getInt(stack.getItem());
    }

    public static FuelValues vanillaBurnTimes(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures) {
        return vanillaBurnTimes(registries, enabledFeatures, 200);
    }

    public static FuelValues vanillaBurnTimes(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures, int smeltingTime) {
        return new FuelValues.Builder(registries, enabledFeatures)
            .add(Items.LAVA_BUCKET, smeltingTime * 100)
            .add(Blocks.COAL_BLOCK, smeltingTime * 8 * 10)
            .add(Items.BLAZE_ROD, smeltingTime * 12)
            .add(Items.COAL, smeltingTime * 8)
            .add(Items.CHARCOAL, smeltingTime * 8)
            .add(ItemTags.LOGS, smeltingTime * 3 / 2)
            .add(ItemTags.BAMBOO_BLOCKS, smeltingTime * 3 / 2)
            .add(ItemTags.PLANKS, smeltingTime * 3 / 2)
            .add(Blocks.BAMBOO_MOSAIC, smeltingTime * 3 / 2)
            .add(ItemTags.WOODEN_STAIRS, smeltingTime * 3 / 2)
            .add(Blocks.BAMBOO_MOSAIC_STAIRS, smeltingTime * 3 / 2)
            .add(ItemTags.WOODEN_SLABS, smeltingTime * 3 / 4)
            .add(Blocks.BAMBOO_MOSAIC_SLAB, smeltingTime * 3 / 4)
            .add(ItemTags.WOODEN_TRAPDOORS, smeltingTime * 3 / 2)
            .add(ItemTags.WOODEN_PRESSURE_PLATES, smeltingTime * 3 / 2)
            .add(ItemTags.WOODEN_SHELVES, smeltingTime * 3 / 2)
            .add(ItemTags.WOODEN_FENCES, smeltingTime * 3 / 2)
            .add(ItemTags.FENCE_GATES, smeltingTime * 3 / 2)
            .add(Blocks.NOTE_BLOCK, smeltingTime * 3 / 2)
            .add(Blocks.BOOKSHELF, smeltingTime * 3 / 2)
            .add(Blocks.CHISELED_BOOKSHELF, smeltingTime * 3 / 2)
            .add(Blocks.LECTERN, smeltingTime * 3 / 2)
            .add(Blocks.JUKEBOX, smeltingTime * 3 / 2)
            .add(Blocks.CHEST, smeltingTime * 3 / 2)
            .add(Blocks.TRAPPED_CHEST, smeltingTime * 3 / 2)
            .add(Blocks.CRAFTING_TABLE, smeltingTime * 3 / 2)
            .add(Blocks.DAYLIGHT_DETECTOR, smeltingTime * 3 / 2)
            .add(ItemTags.BANNERS, smeltingTime * 3 / 2)
            .add(Items.BOW, smeltingTime * 3 / 2)
            .add(Items.FISHING_ROD, smeltingTime * 3 / 2)
            .add(Blocks.LADDER, smeltingTime * 3 / 2)
            .add(ItemTags.SIGNS, smeltingTime)
            .add(ItemTags.HANGING_SIGNS, smeltingTime * 4)
            .add(Items.WOODEN_SHOVEL, smeltingTime)
            .add(Items.WOODEN_SWORD, smeltingTime)
            .add(Items.WOODEN_SPEAR, smeltingTime)
            .add(Items.WOODEN_HOE, smeltingTime)
            .add(Items.WOODEN_AXE, smeltingTime)
            .add(Items.WOODEN_PICKAXE, smeltingTime)
            .add(ItemTags.WOODEN_DOORS, smeltingTime)
            .add(ItemTags.BOATS, smeltingTime * 6)
            .add(ItemTags.WOOL, smeltingTime / 2)
            .add(ItemTags.WOODEN_BUTTONS, smeltingTime / 2)
            .add(Items.STICK, smeltingTime / 2)
            .add(ItemTags.SAPLINGS, smeltingTime / 2)
            .add(Items.BOWL, smeltingTime / 2)
            .add(ItemTags.WOOL_CARPETS, 1 + smeltingTime / 3)
            .add(Blocks.DRIED_KELP_BLOCK, 1 + smeltingTime * 20)
            .add(Items.CROSSBOW, smeltingTime * 3 / 2)
            .add(Blocks.BAMBOO, smeltingTime / 4)
            .add(Blocks.DEAD_BUSH, smeltingTime / 2)
            .add(Blocks.SHORT_DRY_GRASS, smeltingTime / 2)
            .add(Blocks.TALL_DRY_GRASS, smeltingTime / 2)
            .add(Blocks.SCAFFOLDING, smeltingTime / 4)
            .add(Blocks.LOOM, smeltingTime * 3 / 2)
            .add(Blocks.BARREL, smeltingTime * 3 / 2)
            .add(Blocks.CARTOGRAPHY_TABLE, smeltingTime * 3 / 2)
            .add(Blocks.FLETCHING_TABLE, smeltingTime * 3 / 2)
            .add(Blocks.SMITHING_TABLE, smeltingTime * 3 / 2)
            .add(Blocks.COMPOSTER, smeltingTime * 3 / 2)
            .add(Blocks.AZALEA, smeltingTime / 2)
            .add(Blocks.FLOWERING_AZALEA, smeltingTime / 2)
            .add(Blocks.MANGROVE_ROOTS, smeltingTime * 3 / 2)
            .add(Blocks.LEAF_LITTER, smeltingTime / 2)
            .remove(ItemTags.NON_FLAMMABLE_WOOD)
            .build();
    }

    public static class Builder {
        private final HolderLookup<Item> items;
        private final FeatureFlagSet enabledFeatures;
        private final Object2IntSortedMap<Item> values = new Object2IntLinkedOpenHashMap<>();

        public Builder(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures) {
            this.items = registries.lookupOrThrow(Registries.ITEM);
            this.enabledFeatures = enabledFeatures;
        }

        public FuelValues build() {
            return new FuelValues(this.values);
        }

        public FuelValues.Builder remove(TagKey<Item> tag) {
            this.values.keySet().removeIf(item -> item.builtInRegistryHolder().is(tag));
            return this;
        }

        public FuelValues.Builder add(TagKey<Item> tag, int value) {
            this.items.get(tag).ifPresent(named -> {
                for (Holder<Item> holder : named) {
                    this.putInternal(value, holder.value());
                }
            });
            return this;
        }

        public FuelValues.Builder add(ItemLike item, int value) {
            Item item1 = item.asItem();
            this.putInternal(value, item1);
            return this;
        }

        private void putInternal(int value, Item item) {
            if (item.isEnabled(this.enabledFeatures)) {
                this.values.put(item, value);
            }
        }
    }
}
