package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public record InvertedLootItemCondition(LootItemCondition term) implements LootItemCondition {
    public static final MapCodec<InvertedLootItemCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LootItemCondition.DIRECT_CODEC.fieldOf("term").forGetter(InvertedLootItemCondition::term))
            .apply(instance, InvertedLootItemCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.INVERTED;
    }

    @Override
    public boolean test(LootContext context) {
        return !this.term.test(context);
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return this.term.getReferencedContextParams();
    }

    @Override
    public void validate(ValidationContext context) {
        LootItemCondition.super.validate(context);
        this.term.validate(context);
    }

    public static LootItemCondition.Builder invert(LootItemCondition.Builder toInvert) {
        InvertedLootItemCondition invertedLootItemCondition = new InvertedLootItemCondition(toInvert.build());
        return () -> invertedLootItemCondition;
    }
}
