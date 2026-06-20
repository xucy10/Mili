package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public class EntityHurtPlayerTrigger extends SimpleCriterionTrigger<EntityHurtPlayerTrigger.TriggerInstance> {
    @Override
    public Codec<EntityHurtPlayerTrigger.TriggerInstance> codec() {
        return EntityHurtPlayerTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, DamageSource damageSource, float dealtDamage, float takenDamage, boolean blocked) {
        this.trigger(player, instance -> instance.matches(player, damageSource, dealtDamage, takenDamage, blocked));
    }

    public record TriggerInstance(@Override Optional<ContextAwarePredicate> player, Optional<DamagePredicate> damage)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<EntityHurtPlayerTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EntityHurtPlayerTrigger.TriggerInstance::player),
                    DamagePredicate.CODEC.optionalFieldOf("damage").forGetter(EntityHurtPlayerTrigger.TriggerInstance::damage)
                )
                .apply(instance, EntityHurtPlayerTrigger.TriggerInstance::new)
        );

        public static Criterion<EntityHurtPlayerTrigger.TriggerInstance> entityHurtPlayer() {
            return CriteriaTriggers.ENTITY_HURT_PLAYER.createCriterion(new EntityHurtPlayerTrigger.TriggerInstance(Optional.empty(), Optional.empty()));
        }

        public static Criterion<EntityHurtPlayerTrigger.TriggerInstance> entityHurtPlayer(DamagePredicate damage) {
            return CriteriaTriggers.ENTITY_HURT_PLAYER.createCriterion(new EntityHurtPlayerTrigger.TriggerInstance(Optional.empty(), Optional.of(damage)));
        }

        public static Criterion<EntityHurtPlayerTrigger.TriggerInstance> entityHurtPlayer(DamagePredicate.Builder damage) {
            return CriteriaTriggers.ENTITY_HURT_PLAYER
                .createCriterion(new EntityHurtPlayerTrigger.TriggerInstance(Optional.empty(), Optional.of(damage.build())));
        }

        public boolean matches(ServerPlayer player, DamageSource damageSource, float dealtDamage, float takenDamage, boolean blocked) {
            return !this.damage.isPresent() || this.damage.get().matches(player, damageSource, dealtDamage, takenDamage, blocked);
        }
    }
}
