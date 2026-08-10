package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.criterion.DamageSourcePredicate;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public record DamageSourceCondition(Optional<DamageSourcePredicate> predicate) implements LootItemCondition {
    public static final MapCodec<DamageSourceCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(DamageSourcePredicate.CODEC.optionalFieldOf("predicate").forGetter(DamageSourceCondition::predicate))
            .apply(instance, DamageSourceCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.DAMAGE_SOURCE_PROPERTIES;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN, LootContextParams.DAMAGE_SOURCE);
    }

    @Override
    public boolean test(LootContext context) {
        DamageSource damageSource = context.getOptionalParameter(LootContextParams.DAMAGE_SOURCE);
        Vec3 vec3 = context.getOptionalParameter(LootContextParams.ORIGIN);
        return vec3 != null && damageSource != null && (this.predicate.isEmpty() || this.predicate.get().matches(context.getLevel(), vec3, damageSource));
    }

    public static LootItemCondition.Builder hasDamageSource(DamageSourcePredicate.Builder builder) {
        return () -> new DamageSourceCondition(Optional.of(builder.build()));
    }
}
