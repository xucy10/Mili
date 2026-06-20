package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ApplyBonusCount extends LootItemConditionalFunction {
    private static final Map<Identifier, ApplyBonusCount.FormulaType> FORMULAS = Stream.of(
            ApplyBonusCount.BinomialWithBonusCount.TYPE, ApplyBonusCount.OreDrops.TYPE, ApplyBonusCount.UniformBonusCount.TYPE
        )
        .collect(Collectors.toMap(ApplyBonusCount.FormulaType::id, Function.identity()));
    private static final Codec<ApplyBonusCount.FormulaType> FORMULA_TYPE_CODEC = Identifier.CODEC.comapFlatMap(identifier -> {
        ApplyBonusCount.FormulaType formulaType = FORMULAS.get(identifier);
        return formulaType != null ? DataResult.success(formulaType) : DataResult.error(() -> "No formula type with id: '" + identifier + "'");
    }, ApplyBonusCount.FormulaType::id);
    private static final MapCodec<ApplyBonusCount.Formula> FORMULA_CODEC = ExtraCodecs.dispatchOptionalValue(
        "formula", "parameters", FORMULA_TYPE_CODEC, ApplyBonusCount.Formula::getType, ApplyBonusCount.FormulaType::codec
    );
    public static final MapCodec<ApplyBonusCount> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                instance.group(
                    Enchantment.CODEC.fieldOf("enchantment").forGetter(applyBonusCount -> applyBonusCount.enchantment),
                    FORMULA_CODEC.forGetter(applyBonusCount -> applyBonusCount.formula)
                )
            )
            .apply(instance, ApplyBonusCount::new)
    );
    private final Holder<Enchantment> enchantment;
    private final ApplyBonusCount.Formula formula;

    private ApplyBonusCount(List<LootItemCondition> predicates, Holder<Enchantment> enchantment, ApplyBonusCount.Formula formula) {
        super(predicates);
        this.enchantment = enchantment;
        this.formula = formula;
    }

    @Override
    public LootItemFunctionType<ApplyBonusCount> getType() {
        return LootItemFunctions.APPLY_BONUS;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.TOOL);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        ItemStack itemStack = context.getOptionalParameter(LootContextParams.TOOL);
        if (itemStack != null) {
            int itemEnchantmentLevel = EnchantmentHelper.getItemEnchantmentLevel(this.enchantment, itemStack);
            int i = this.formula.calculateNewCount(context.getRandom(), stack.getCount(), itemEnchantmentLevel);
            stack.setCount(i);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> addBonusBinomialDistributionCount(Holder<Enchantment> enchantment, float probability, int extraRounds) {
        return simpleBuilder(list -> new ApplyBonusCount(list, enchantment, new ApplyBonusCount.BinomialWithBonusCount(extraRounds, probability)));
    }

    public static LootItemConditionalFunction.Builder<?> addOreBonusCount(Holder<Enchantment> enchantment) {
        return simpleBuilder(list -> new ApplyBonusCount(list, enchantment, ApplyBonusCount.OreDrops.INSTANCE));
    }

    public static LootItemConditionalFunction.Builder<?> addUniformBonusCount(Holder<Enchantment> enchantment) {
        return simpleBuilder(list -> new ApplyBonusCount(list, enchantment, new ApplyBonusCount.UniformBonusCount(1)));
    }

    public static LootItemConditionalFunction.Builder<?> addUniformBonusCount(Holder<Enchantment> enchantment, int bonusMultiplier) {
        return simpleBuilder(list -> new ApplyBonusCount(list, enchantment, new ApplyBonusCount.UniformBonusCount(bonusMultiplier)));
    }

    record BinomialWithBonusCount(int extraRounds, float probability) implements ApplyBonusCount.Formula {
        private static final Codec<ApplyBonusCount.BinomialWithBonusCount> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("extra").forGetter(ApplyBonusCount.BinomialWithBonusCount::extraRounds),
                    Codec.FLOAT.fieldOf("probability").forGetter(ApplyBonusCount.BinomialWithBonusCount::probability)
                )
                .apply(instance, ApplyBonusCount.BinomialWithBonusCount::new)
        );
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(
            Identifier.withDefaultNamespace("binomial_with_bonus_count"), CODEC
        );

        @Override
        public int calculateNewCount(RandomSource random, int originalCount, int enchantmentLevel) {
            for (int i = 0; i < enchantmentLevel + this.extraRounds; i++) {
                if (random.nextFloat() < this.probability) {
                    originalCount++;
                }
            }

            return originalCount;
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return TYPE;
        }
    }

    interface Formula {
        int calculateNewCount(RandomSource random, int originalCount, int enchantmentLevel);

        ApplyBonusCount.FormulaType getType();
    }

    record FormulaType(Identifier id, Codec<? extends ApplyBonusCount.Formula> codec) {
    }

    record OreDrops() implements ApplyBonusCount.Formula {
        public static final ApplyBonusCount.OreDrops INSTANCE = new ApplyBonusCount.OreDrops();
        public static final Codec<ApplyBonusCount.OreDrops> CODEC = MapCodec.unitCodec(INSTANCE);
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(Identifier.withDefaultNamespace("ore_drops"), CODEC);

        @Override
        public int calculateNewCount(RandomSource random, int originalCount, int enchantmentLevel) {
            if (enchantmentLevel > 0) {
                int i = random.nextInt(enchantmentLevel + 2) - 1;
                if (i < 0) {
                    i = 0;
                }

                return originalCount * (i + 1);
            } else {
                return originalCount;
            }
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return TYPE;
        }
    }

    record UniformBonusCount(int bonusMultiplier) implements ApplyBonusCount.Formula {
        public static final Codec<ApplyBonusCount.UniformBonusCount> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(Codec.INT.fieldOf("bonusMultiplier").forGetter(ApplyBonusCount.UniformBonusCount::bonusMultiplier))
                .apply(instance, ApplyBonusCount.UniformBonusCount::new)
        );
        public static final ApplyBonusCount.FormulaType TYPE = new ApplyBonusCount.FormulaType(Identifier.withDefaultNamespace("uniform_bonus_count"), CODEC);

        @Override
        public int calculateNewCount(RandomSource random, int originalCount, int enchantmentLevel) {
            return originalCount + random.nextInt(this.bonusMultiplier * enchantmentLevel + 1);
        }

        @Override
        public ApplyBonusCount.FormulaType getType() {
            return TYPE;
        }
    }
}
