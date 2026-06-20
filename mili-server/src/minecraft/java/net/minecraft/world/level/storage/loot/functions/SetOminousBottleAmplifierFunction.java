package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetOminousBottleAmplifierFunction extends LootItemConditionalFunction {
    static final MapCodec<SetOminousBottleAmplifierFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(
                NumberProviders.CODEC.fieldOf("amplifier").forGetter(setOminousBottleAmplifierFunction -> setOminousBottleAmplifierFunction.amplifierGenerator)
            )
            .apply(instance, SetOminousBottleAmplifierFunction::new)
    );
    private final NumberProvider amplifierGenerator;

    private SetOminousBottleAmplifierFunction(List<LootItemCondition> predicates, NumberProvider amplifierGenerator) {
        super(predicates);
        this.amplifierGenerator = amplifierGenerator;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.amplifierGenerator.getReferencedContextParams();
    }

    @Override
    public LootItemFunctionType<SetOminousBottleAmplifierFunction> getType() {
        return LootItemFunctions.SET_OMINOUS_BOTTLE_AMPLIFIER;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        int i = Mth.clamp(this.amplifierGenerator.getInt(context), 0, 4);
        stack.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new OminousBottleAmplifier(i));
        return stack;
    }

    public NumberProvider amplifier() {
        return this.amplifierGenerator;
    }

    public static LootItemConditionalFunction.Builder<?> setAmplifier(NumberProvider amplifier) {
        return simpleBuilder(list -> new SetOminousBottleAmplifierFunction(list, amplifier));
    }
}
