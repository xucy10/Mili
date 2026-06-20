package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class EnchantRandomlyFunction extends LootItemConditionalFunction {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<EnchantRandomlyFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                        .optionalFieldOf("options")
                        .forGetter(enchantRandomlyFunction -> enchantRandomlyFunction.options),
                    Codec.BOOL.optionalFieldOf("only_compatible", true).forGetter(enchantRandomlyFunction -> enchantRandomlyFunction.onlyCompatible)
                )
            )
            .apply(instance, EnchantRandomlyFunction::new)
    );
    private final Optional<HolderSet<Enchantment>> options;
    private final boolean onlyCompatible;

    EnchantRandomlyFunction(List<LootItemCondition> predicates, Optional<HolderSet<Enchantment>> options, boolean onlyCompatible) {
        super(predicates);
        this.options = options;
        this.onlyCompatible = onlyCompatible;
    }

    @Override
    public LootItemFunctionType<EnchantRandomlyFunction> getType() {
        return LootItemFunctions.ENCHANT_RANDOMLY;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        RandomSource random = context.getRandom();
        boolean isBook = stack.is(Items.BOOK);
        boolean flag = !isBook && this.onlyCompatible;
        Stream<Holder<Enchantment>> stream = this.options
            .map(HolderSet::stream)
            .orElseGet(() -> context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().map(Function.identity()))
            .filter(holder -> !flag || holder.value().canEnchant(stack));
        List<Holder<Enchantment>> list = stream.toList();
        Optional<Holder<Enchantment>> randomSafe = Util.getRandomSafe(list, random);
        if (randomSafe.isEmpty()) {
            LOGGER.warn("Couldn't find a compatible enchantment for {}", stack);
            return stack;
        } else {
            return enchantItem(stack, randomSafe.get(), random);
        }
    }

    private static ItemStack enchantItem(ItemStack stack, Holder<Enchantment> enchantment, RandomSource random) {
        int randomInt = Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
        if (stack.is(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        stack.enchant(enchantment, randomInt);
        return stack;
    }

    public static EnchantRandomlyFunction.Builder randomEnchantment() {
        return new EnchantRandomlyFunction.Builder();
    }

    public static EnchantRandomlyFunction.Builder randomApplicableEnchantment(HolderLookup.Provider registries) {
        return randomEnchantment().withOneOf(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT));
    }

    public static class Builder extends LootItemConditionalFunction.Builder<EnchantRandomlyFunction.Builder> {
        private Optional<HolderSet<Enchantment>> options = Optional.empty();
        private boolean onlyCompatible = true;

        @Override
        protected EnchantRandomlyFunction.Builder getThis() {
            return this;
        }

        public EnchantRandomlyFunction.Builder withEnchantment(Holder<Enchantment> enchantment) {
            this.options = Optional.of(HolderSet.direct(enchantment));
            return this;
        }

        public EnchantRandomlyFunction.Builder withOneOf(HolderSet<Enchantment> enchantments) {
            this.options = Optional.of(enchantments);
            return this;
        }

        public EnchantRandomlyFunction.Builder allowingIncompatibleEnchantments() {
            this.onlyCompatible = false;
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new EnchantRandomlyFunction(this.getConditions(), this.options, this.onlyCompatible);
        }
    }
}
