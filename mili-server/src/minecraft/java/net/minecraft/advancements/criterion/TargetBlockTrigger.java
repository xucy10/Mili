package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

public class TargetBlockTrigger extends SimpleCriterionTrigger<TargetBlockTrigger.TriggerInstance> {
    @Override
    public Codec<TargetBlockTrigger.TriggerInstance> codec() {
        return TargetBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity projectile, Vec3 vector, int signalStrength) {
        LootContext lootContext = EntityPredicate.createContext(player, projectile);
        this.trigger(player, instance -> instance.matches(lootContext, vector, signalStrength));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, MinMaxBounds.Ints signalStrength, Optional<ContextAwarePredicate> projectile
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TargetBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TargetBlockTrigger.TriggerInstance::player),
                    MinMaxBounds.Ints.CODEC
                        .optionalFieldOf("signal_strength", MinMaxBounds.Ints.ANY)
                        .forGetter(TargetBlockTrigger.TriggerInstance::signalStrength),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("projectile").forGetter(TargetBlockTrigger.TriggerInstance::projectile)
                )
                .apply(instance, TargetBlockTrigger.TriggerInstance::new)
        );

        public static Criterion<TargetBlockTrigger.TriggerInstance> targetHit(MinMaxBounds.Ints signalStrength, Optional<ContextAwarePredicate> projectile) {
            return CriteriaTriggers.TARGET_BLOCK_HIT.createCriterion(new TargetBlockTrigger.TriggerInstance(Optional.empty(), signalStrength, projectile));
        }

        public boolean matches(LootContext context, Vec3 vector, int signalStrength) {
            return this.signalStrength.matches(signalStrength) && (!this.projectile.isPresent() || this.projectile.get().matches(context));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.projectile, "projectile");
        }
    }
}
