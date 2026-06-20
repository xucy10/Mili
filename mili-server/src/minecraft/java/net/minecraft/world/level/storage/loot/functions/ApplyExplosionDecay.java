package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ApplyExplosionDecay extends LootItemConditionalFunction {
    public static final MapCodec<ApplyExplosionDecay> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance).apply(instance, ApplyExplosionDecay::new)
    );

    private ApplyExplosionDecay(List<LootItemCondition> predicates) {
        super(predicates);
    }

    @Override
    public LootItemFunctionType<ApplyExplosionDecay> getType() {
        return LootItemFunctions.EXPLOSION_DECAY;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Float _float = context.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (_float != null) {
            RandomSource random = context.getRandom();
            float f = 1.0F / _float;
            int count = stack.getCount();
            int i = 0;

            for (int i1 = 0; i1 < count; i1++) {
                if (random.nextFloat() <= f) {
                    i++;
                }
            }

            stack.setCount(i);
        }

        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> explosionDecay() {
        return simpleBuilder(ApplyExplosionDecay::new);
    }
}
