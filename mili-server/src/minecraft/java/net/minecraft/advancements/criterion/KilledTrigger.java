package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;

public class KilledTrigger extends SimpleCriterionTrigger<KilledTrigger.TriggerInstance> {
    @Override
    public Codec<KilledTrigger.TriggerInstance> codec() {
        return KilledTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity entity, DamageSource damageSource) {
        LootContext lootContext = EntityPredicate.createContext(player, entity);
        this.trigger(player, instance -> instance.matches(player, lootContext, damageSource));
    }

    public record TriggerInstance(
        @Override Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> entityPredicate, Optional<DamageSourcePredicate> killingBlow
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<KilledTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KilledTrigger.TriggerInstance::player),
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(KilledTrigger.TriggerInstance::entityPredicate),
                    DamageSourcePredicate.CODEC.optionalFieldOf("killing_blow").forGetter(KilledTrigger.TriggerInstance::killingBlow)
                )
                .apply(instance, KilledTrigger.TriggerInstance::new)
        );

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(Optional<EntityPredicate> entityPredicate) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(EntityPredicate.Builder entityPredicate) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity() {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            Optional<EntityPredicate> entityPredicate, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            EntityPredicate.Builder entityPredicate, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            Optional<EntityPredicate> entityPredicate, DamageSourcePredicate.Builder killingBlow
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), Optional.of(killingBlow.build())));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            EntityPredicate.Builder entityPredicate, DamageSourcePredicate.Builder killingBlow
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), Optional.of(killingBlow.build()))
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntityNearSculkCatalyst() {
            return CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(Optional<EntityPredicate> entityPredicate) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(EntityPredicate.Builder entityPredicate) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer() {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            Optional<EntityPredicate> entityPredicate, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            EntityPredicate.Builder entityPredicate, Optional<DamageSourcePredicate> killingBlow
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), killingBlow));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            Optional<EntityPredicate> entityPredicate, DamageSourcePredicate.Builder killingBlow
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(entityPredicate), Optional.of(killingBlow.build())));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            EntityPredicate.Builder entityPredicate, DamageSourcePredicate.Builder killingBlow
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(entityPredicate)), Optional.of(killingBlow.build()))
                );
        }

        public boolean matches(ServerPlayer player, LootContext context, DamageSource damageSource) {
            return (!this.killingBlow.isPresent() || this.killingBlow.get().matches(player, damageSource))
                && (this.entityPredicate.isEmpty() || this.entityPredicate.get().matches(context));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entityPredicate, "entity");
        }
    }
}
