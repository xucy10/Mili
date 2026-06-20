package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

public record DamagePredicate(
    MinMaxBounds.Doubles dealtDamage,
    MinMaxBounds.Doubles takenDamage,
    Optional<EntityPredicate> sourceEntity,
    Optional<Boolean> blocked,
    Optional<DamageSourcePredicate> type
) {
    public static final Codec<DamagePredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("dealt", MinMaxBounds.Doubles.ANY).forGetter(DamagePredicate::dealtDamage),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("taken", MinMaxBounds.Doubles.ANY).forGetter(DamagePredicate::takenDamage),
                EntityPredicate.CODEC.optionalFieldOf("source_entity").forGetter(DamagePredicate::sourceEntity),
                Codec.BOOL.optionalFieldOf("blocked").forGetter(DamagePredicate::blocked),
                DamageSourcePredicate.CODEC.optionalFieldOf("type").forGetter(DamagePredicate::type)
            )
            .apply(instance, DamagePredicate::new)
    );

    public boolean matches(ServerPlayer player, DamageSource damageSource, float dealtDamage, float takenDamage, boolean blocked) {
        return this.dealtDamage.matches(dealtDamage)
            && this.takenDamage.matches(takenDamage)
            && (!this.sourceEntity.isPresent() || this.sourceEntity.get().matches(player, damageSource.getEntity()))
            && (!this.blocked.isPresent() || this.blocked.get() == blocked)
            && (!this.type.isPresent() || this.type.get().matches(player, damageSource));
    }

    public static class Builder {
        private MinMaxBounds.Doubles dealtDamage = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles takenDamage = MinMaxBounds.Doubles.ANY;
        private Optional<EntityPredicate> sourceEntity = Optional.empty();
        private Optional<Boolean> blocked = Optional.empty();
        private Optional<DamageSourcePredicate> type = Optional.empty();

        public static DamagePredicate.Builder damageInstance() {
            return new DamagePredicate.Builder();
        }

        public DamagePredicate.Builder dealtDamage(MinMaxBounds.Doubles dealtDamage) {
            this.dealtDamage = dealtDamage;
            return this;
        }

        public DamagePredicate.Builder takenDamage(MinMaxBounds.Doubles takenDamage) {
            this.takenDamage = takenDamage;
            return this;
        }

        public DamagePredicate.Builder sourceEntity(EntityPredicate sourceEntity) {
            this.sourceEntity = Optional.of(sourceEntity);
            return this;
        }

        public DamagePredicate.Builder blocked(Boolean blocked) {
            this.blocked = Optional.of(blocked);
            return this;
        }

        public DamagePredicate.Builder type(DamageSourcePredicate type) {
            this.type = Optional.of(type);
            return this;
        }

        public DamagePredicate.Builder type(DamageSourcePredicate.Builder typeBuilder) {
            this.type = Optional.of(typeBuilder.build());
            return this;
        }

        public DamagePredicate build() {
            return new DamagePredicate(this.dealtDamage, this.takenDamage, this.sourceEntity, this.blocked, this.type);
        }
    }
}
