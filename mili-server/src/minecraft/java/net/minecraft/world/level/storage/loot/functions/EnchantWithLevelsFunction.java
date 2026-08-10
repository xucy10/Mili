package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class EnchantWithLevelsFunction extends LootItemConditionalFunction {
    public static final MapCodec<EnchantWithLevelsFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    NumberProviders.CODEC.fieldOf("levels").forGetter(function -> function.levels),
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("options").forGetter(function -> function.options)
                )
            )
            .apply(instance, EnchantWithLevelsFunction::new)
    );
    private final NumberProvider levels;
    private final Optional<HolderSet<Enchantment>> options;

    EnchantWithLevelsFunction(List<LootItemCondition> predicates, NumberProvider levels, Optional<HolderSet<Enchantment>> options) {
        super(predicates);
        this.levels = levels;
        this.options = options;
    }

    @Override
    public LootItemFunctionType<EnchantWithLevelsFunction> getType() {
        return LootItemFunctions.ENCHANT_WITH_LEVELS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.levels.getReferencedContextParams();
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        RandomSource random = context.getRandom();
        RegistryAccess registryAccess = context.getLevel().registryAccess();
        return EnchantmentHelper.enchantItem(random, stack, this.levels.getInt(context), registryAccess, this.options);
    }

    public static EnchantWithLevelsFunction.Builder enchantWithLevels(HolderLookup.Provider registries, NumberProvider levels) {
        return new EnchantWithLevelsFunction.Builder(levels)
            .fromOptions(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantWithLevelsFunction.Builder> {
        private final NumberProvider levels;
        private Optional<HolderSet<Enchantment>> options = Optional.empty();

        public Builder(NumberProvider levels) {
            this.levels = levels;
        }

        @Override
        protected EnchantWithLevelsFunction.Builder getThis() {
            return this;
        }

        public EnchantWithLevelsFunction.Builder fromOptions(HolderSet<Enchantment> options) {
            this.options = Optional.of(options);
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantWithLevelsFunction(this.getConditions(), this.levels, this.options);
        }
    }
}
