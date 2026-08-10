package net.minecraft.advancements.criterion;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;

public record DamageSourcePredicate(
    List<TagPredicate<DamageType>> tags, Optional<EntityPredicate> directEntity, Optional<EntityPredicate> sourceEntity, Optional<Boolean> isDirect
) {
    public static final Codec<DamageSourcePredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                TagPredicate.codec(Registries.DAMAGE_TYPE).listOf().optionalFieldOf("tags", List.of()).forGetter(DamageSourcePredicate::tags),
                EntityPredicate.CODEC.optionalFieldOf("direct_entity").forGetter(DamageSourcePredicate::directEntity),
                EntityPredicate.CODEC.optionalFieldOf("source_entity").forGetter(DamageSourcePredicate::sourceEntity),
                Codec.BOOL.optionalFieldOf("is_direct").forGetter(DamageSourcePredicate::isDirect)
            )
            .apply(instance, DamageSourcePredicate::new)
    );

    public boolean matches(ServerPlayer player, DamageSource damageSource) {
        return this.matches(player.level(), player.position(), damageSource);
    }

    public boolean matches(ServerLevel level, Vec3 position, DamageSource damageSource) {
        for (TagPredicate<DamageType> tagPredicate : this.tags) {
            if (!tagPredicate.matches(damageSource.typeHolder())) {
                return false;
            }
        }

        return (!this.directEntity.isPresent() || this.directEntity.get().matches(level, position, damageSource.getDirectEntity()))
            && (!this.sourceEntity.isPresent() || this.sourceEntity.get().matches(level, position, damageSource.getEntity()))
            && (!this.isDirect.isPresent() || this.isDirect.get() == damageSource.isDirect());
    }

    public static class Builder {
        private final ImmutableList.Builder<TagPredicate<DamageType>> tags = ImmutableList.builder();
        private Optional<EntityPredicate> directEntity = Optional.empty();
        private Optional<EntityPredicate> sourceEntity = Optional.empty();
        private Optional<Boolean> isDirect = Optional.empty();

        public static DamageSourcePredicate.Builder damageType() {
            return new DamageSourcePredicate.Builder();
        }

        public DamageSourcePredicate.Builder tag(TagPredicate<DamageType> tag) {
            this.tags.add(tag);
            return this;
        }

        public DamageSourcePredicate.Builder direct(EntityPredicate.Builder directEntity) {
            this.directEntity = Optional.of(directEntity.build());
            return this;
        }

        public DamageSourcePredicate.Builder source(EntityPredicate.Builder sourceEntity) {
            this.sourceEntity = Optional.of(sourceEntity.build());
            return this;
        }

        public DamageSourcePredicate.Builder isDirect(boolean isDirect) {
            this.isDirect = Optional.of(isDirect);
            return this;
        }

        public DamageSourcePredicate build() {
            return new DamageSourcePredicate(this.tags.build(), this.directEntity, this.sourceEntity, this.isDirect);
        }
    }
}
