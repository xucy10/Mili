package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Function;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public abstract class EnchantmentsPredicate implements SingleComponentItemPredicate<ItemEnchantments> {
    private final List<EnchantmentPredicate> enchantments;

    protected EnchantmentsPredicate(List<EnchantmentPredicate> enchantments) {
        this.enchantments = enchantments;
    }

    public static <T extends EnchantmentsPredicate> Codec<T> codec(Function<List<EnchantmentPredicate>, T> factory) {
        return EnchantmentPredicate.CODEC.listOf().xmap(factory, EnchantmentsPredicate::enchantments);
    }

    protected List<EnchantmentPredicate> enchantments() {
        return this.enchantments;
    }

    @Override
    public boolean matches(ItemEnchantments value) {
        for (EnchantmentPredicate enchantmentPredicate : this.enchantments) {
            if (!enchantmentPredicate.containedIn(value)) {
                return false;
            }
        }

        return true;
    }

    public static EnchantmentsPredicate.Enchantments enchantments(List<EnchantmentPredicate> enchantments) {
        return new EnchantmentsPredicate.Enchantments(enchantments);
    }

    public static EnchantmentsPredicate.StoredEnchantments storedEnchantments(List<EnchantmentPredicate> storeEnchantments) {
        return new EnchantmentsPredicate.StoredEnchantments(storeEnchantments);
    }

    public static class Enchantments extends EnchantmentsPredicate {
        public static final Codec<EnchantmentsPredicate.Enchantments> CODEC = codec(EnchantmentsPredicate.Enchantments::new);

        protected Enchantments(List<EnchantmentPredicate> enchantments) {
            super(enchantments);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.ENCHANTMENTS;
        }
    }

    public static class StoredEnchantments extends EnchantmentsPredicate {
        public static final Codec<EnchantmentsPredicate.StoredEnchantments> CODEC = codec(EnchantmentsPredicate.StoredEnchantments::new);

        protected StoredEnchantments(List<EnchantmentPredicate> enchantments) {
            super(enchantments);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.STORED_ENCHANTMENTS;
        }
    }
}
