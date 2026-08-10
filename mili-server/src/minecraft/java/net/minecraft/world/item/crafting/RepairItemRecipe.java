package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class RepairItemRecipe extends CustomRecipe {
    public RepairItemRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static @Nullable Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        } else {
            ItemStack itemStack = null;

            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (!item.isEmpty()) {
                    if (itemStack != null) {
                        return canCombine(itemStack, item) ? Pair.of(itemStack, item) : null;
                    }

                    itemStack = item;
                }
            }

            return null;
        }
    }

    private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
        return stack2.is(stack1.getItem())
            && stack1.getCount() == 1
            && stack2.getCount() == 1
            && stack1.has(DataComponents.MAX_DAMAGE)
            && stack2.has(DataComponents.MAX_DAMAGE)
            && stack1.has(DataComponents.DAMAGE)
            && stack2.has(DataComponents.DAMAGE);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return getItemsToCombine(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> itemsToCombine = getItemsToCombine(input);
        if (itemsToCombine == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = itemsToCombine.getFirst();
            ItemStack itemStack1 = itemsToCombine.getSecond();
            int max = Math.max(itemStack.getMaxDamage(), itemStack1.getMaxDamage());
            int i = itemStack.getMaxDamage() - itemStack.getDamageValue();
            int i1 = itemStack1.getMaxDamage() - itemStack1.getDamageValue();
            int i2 = i + i1 + max * 5 / 100;
            ItemStack itemStack2 = new ItemStack(itemStack.getItem());
            itemStack2.set(DataComponents.MAX_DAMAGE, max);
            itemStack2.setDamageValue(Math.max(max - i2, 0));
            ItemEnchantments enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(itemStack);
            ItemEnchantments enchantmentsForCrafting1 = EnchantmentHelper.getEnchantmentsForCrafting(itemStack1);
            EnchantmentHelper.updateEnchantments(
                itemStack2,
                enchantments -> registries.lookupOrThrow(Registries.ENCHANTMENT)
                    .listElements()
                    .filter(enchantment -> enchantment.is(EnchantmentTags.CURSE))
                    .forEach(enchantment -> {
                        int max1 = Math.max(enchantmentsForCrafting.getLevel(enchantment), enchantmentsForCrafting1.getLevel(enchantment));
                        if (max1 > 0) {
                            enchantments.upgrade(enchantment, max1);
                        }
                    })
            );
            return itemStack2;
        }
    }

    @Override
    public RecipeSerializer<RepairItemRecipe> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}
