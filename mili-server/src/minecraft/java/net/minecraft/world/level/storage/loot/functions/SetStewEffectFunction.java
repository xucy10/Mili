package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetStewEffectFunction extends LootItemConditionalFunction {
    private static final Codec<List<SetStewEffectFunction.EffectEntry>> EFFECTS_LIST = SetStewEffectFunction.EffectEntry.CODEC.listOf().validate(list -> {
        Set<Holder<MobEffect>> set = new ObjectOpenHashSet<>();

        for (SetStewEffectFunction.EffectEntry effectEntry : list) {
            if (!set.add(effectEntry.effect())) {
                return DataResult.error(() -> "Encountered duplicate mob effect: '" + effectEntry.effect() + "'");
            }
        }

        return DataResult.success(list);
    });
    public static final MapCodec<SetStewEffectFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
            .and(EFFECTS_LIST.optionalFieldOf("effects", List.of()).forGetter(setStewEffectFunction -> setStewEffectFunction.effects))
            .apply(instance, SetStewEffectFunction::new)
    );
    private final List<SetStewEffectFunction.EffectEntry> effects;

    SetStewEffectFunction(List<LootItemCondition> predicates, List<SetStewEffectFunction.EffectEntry> effects) {
        super(predicates);
        this.effects = effects;
    }

    @Override
    public LootItemFunctionType<SetStewEffectFunction> getType() {
        return LootItemFunctions.SET_STEW_EFFECT;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.effects
            .stream()
            .flatMap(effectEntry -> effectEntry.duration().getReferencedContextParams().stream())
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.is(Items.SUSPICIOUS_STEW) && !this.effects.isEmpty()) {
            SetStewEffectFunction.EffectEntry effectEntry = Util.getRandom(this.effects, context.getRandom());
            Holder<MobEffect> holder = effectEntry.effect();
            int _int = effectEntry.duration().getInt(context);
            if (!holder.value().isInstantenous()) {
                _int *= 20;
            }

            SuspiciousStewEffects.Entry entry = new SuspiciousStewEffects.Entry(holder, _int);
            stack.update(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY, entry, SuspiciousStewEffects::withEffectAdded);
            return stack;
        } else {
            return stack;
        }
    }

    public static SetStewEffectFunction.Builder stewEffect() {
        return new SetStewEffectFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetStewEffectFunction.Builder> {
        private final ImmutableList.Builder<SetStewEffectFunction.EffectEntry> effects = ImmutableList.builder();

        @Override
        protected SetStewEffectFunction.Builder getThis() {
            return this;
        }

        public SetStewEffectFunction.Builder withEffect(Holder<MobEffect> effect, NumberProvider amplifier) {
            this.effects.add(new SetStewEffectFunction.EffectEntry(effect, amplifier));
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetStewEffectFunction(this.getConditions(), this.effects.build());
        }
    }

    record EffectEntry(Holder<MobEffect> effect, NumberProvider duration) {
        public static final Codec<SetStewEffectFunction.EffectEntry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    MobEffect.CODEC.fieldOf("type").forGetter(SetStewEffectFunction.EffectEntry::effect),
                    NumberProviders.CODEC.fieldOf("duration").forGetter(SetStewEffectFunction.EffectEntry::duration)
                )
                .apply(instance, SetStewEffectFunction.EffectEntry::new)
        );
    }
}
