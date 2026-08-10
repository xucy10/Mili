package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public record LootItemEntityPropertyCondition(Optional<EntityPredicate> predicate, LootContext.EntityTarget entityTarget) implements LootItemCondition {
    public static final MapCodec<LootItemEntityPropertyCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                EntityPredicate.CODEC.optionalFieldOf("predicate").forGetter(LootItemEntityPropertyCondition::predicate),
                LootContext.EntityTarget.CODEC.fieldOf("entity").forGetter(LootItemEntityPropertyCondition::entityTarget)
            )
            .apply(instance, LootItemEntityPropertyCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ENTITY_PROPERTIES;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN, this.entityTarget.contextParam());
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getOptionalParameter(this.entityTarget.contextParam());
        Vec3 vec3 = context.getOptionalParameter(LootContextParams.ORIGIN);
        return this.predicate.isEmpty() || this.predicate.get().matches(context.getLevel(), vec3, entity);
    }

    public static LootItemCondition.Builder entityPresent(LootContext.EntityTarget target) {
        return hasProperties(target, EntityPredicate.Builder.entity());
    }

    public static LootItemCondition.Builder hasProperties(LootContext.EntityTarget target, EntityPredicate.Builder predicateBuilder) {
        return () -> new LootItemEntityPropertyCondition(Optional.of(predicateBuilder.build()), target);
    }

    public static LootItemCondition.Builder hasProperties(LootContext.EntityTarget target, EntityPredicate entityPredicate) {
        return () -> new LootItemEntityPropertyCondition(Optional.of(entityPredicate), target);
    }
}
