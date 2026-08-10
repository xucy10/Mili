package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public record EntityFlagsPredicate(
    Optional<Boolean> isOnGround,
    Optional<Boolean> isOnFire,
    Optional<Boolean> isCrouching,
    Optional<Boolean> isSprinting,
    Optional<Boolean> isSwimming,
    Optional<Boolean> isFlying,
    Optional<Boolean> isBaby,
    Optional<Boolean> isInWater,
    Optional<Boolean> isFallFlying
) {
    public static final Codec<EntityFlagsPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.BOOL.optionalFieldOf("is_on_ground").forGetter(EntityFlagsPredicate::isOnGround),
                Codec.BOOL.optionalFieldOf("is_on_fire").forGetter(EntityFlagsPredicate::isOnFire),
                Codec.BOOL.optionalFieldOf("is_sneaking").forGetter(EntityFlagsPredicate::isCrouching),
                Codec.BOOL.optionalFieldOf("is_sprinting").forGetter(EntityFlagsPredicate::isSprinting),
                Codec.BOOL.optionalFieldOf("is_swimming").forGetter(EntityFlagsPredicate::isSwimming),
                Codec.BOOL.optionalFieldOf("is_flying").forGetter(EntityFlagsPredicate::isFlying),
                Codec.BOOL.optionalFieldOf("is_baby").forGetter(EntityFlagsPredicate::isBaby),
                Codec.BOOL.optionalFieldOf("is_in_water").forGetter(EntityFlagsPredicate::isInWater),
                Codec.BOOL.optionalFieldOf("is_fall_flying").forGetter(EntityFlagsPredicate::isFallFlying)
            )
            .apply(instance, EntityFlagsPredicate::new)
    );

    public boolean matches(Entity entity) {
        if (this.isOnGround.isPresent() && entity.onGround() != this.isOnGround.get()) {
            return false;
        } else if (this.isOnFire.isPresent() && entity.isOnFire() != this.isOnFire.get()) {
            return false;
        } else if (this.isCrouching.isPresent() && entity.isCrouching() != this.isCrouching.get()) {
            return false;
        } else if (this.isSprinting.isPresent() && entity.isSprinting() != this.isSprinting.get()) {
            return false;
        } else if (this.isSwimming.isPresent() && entity.isSwimming() != this.isSwimming.get()) {
            return false;
        } else {
            if (this.isFlying.isPresent()) {
                boolean flag = entity instanceof LivingEntity livingEntity
                    && (livingEntity.isFallFlying() || livingEntity instanceof Player player && player.getAbilities().flying);
                if (flag != this.isFlying.get()) {
                    return false;
                }
            }

            return (!this.isInWater.isPresent() || entity.isInWater() == this.isInWater.get())
                && (
                    !(this.isFallFlying.isPresent() && entity instanceof LivingEntity livingEntity1x)
                        || livingEntity1x.isFallFlying() == this.isFallFlying.get()
                )
                && (!(this.isBaby.isPresent() && entity instanceof LivingEntity livingEntity1) || livingEntity1.isBaby() == this.isBaby.get());
        }
    }

    public static class Builder {
        private Optional<Boolean> isOnGround = Optional.empty();
        private Optional<Boolean> isOnFire = Optional.empty();
        private Optional<Boolean> isCrouching = Optional.empty();
        private Optional<Boolean> isSprinting = Optional.empty();
        private Optional<Boolean> isSwimming = Optional.empty();
        private Optional<Boolean> isFlying = Optional.empty();
        private Optional<Boolean> isBaby = Optional.empty();
        private Optional<Boolean> isInWater = Optional.empty();
        private Optional<Boolean> isFallFlying = Optional.empty();

        public static EntityFlagsPredicate.Builder flags() {
            return new EntityFlagsPredicate.Builder();
        }

        public EntityFlagsPredicate.Builder setOnGround(Boolean onGround) {
            this.isOnGround = Optional.of(onGround);
            return this;
        }

        public EntityFlagsPredicate.Builder setOnFire(Boolean onFire) {
            this.isOnFire = Optional.of(onFire);
            return this;
        }

        public EntityFlagsPredicate.Builder setCrouching(Boolean isCrouching) {
            this.isCrouching = Optional.of(isCrouching);
            return this;
        }

        public EntityFlagsPredicate.Builder setSprinting(Boolean isSprinting) {
            this.isSprinting = Optional.of(isSprinting);
            return this;
        }

        public EntityFlagsPredicate.Builder setSwimming(Boolean isSwimming) {
            this.isSwimming = Optional.of(isSwimming);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsFlying(Boolean isFlying) {
            this.isFlying = Optional.of(isFlying);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsBaby(Boolean isBaby) {
            this.isBaby = Optional.of(isBaby);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsInWater(Boolean isInWater) {
            this.isInWater = Optional.of(isInWater);
            return this;
        }

        public EntityFlagsPredicate.Builder setIsFallFlying(Boolean isFallFlying) {
            this.isFallFlying = Optional.of(isFallFlying);
            return this;
        }

        public EntityFlagsPredicate build() {
            return new EntityFlagsPredicate(
                this.isOnGround,
                this.isOnFire,
                this.isCrouching,
                this.isSprinting,
                this.isSwimming,
                this.isFlying,
                this.isBaby,
                this.isInWater,
                this.isFallFlying
            );
        }
    }
}
