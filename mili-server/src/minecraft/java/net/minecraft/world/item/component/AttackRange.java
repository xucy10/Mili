package net.minecraft.world.item.component;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public record AttackRange(float minRange, float maxRange, float minCreativeRange, float maxCreativeRange, float hitboxMargin, float mobFactor) {
    public static final AttackRange CODEC_DEFAULT = new AttackRange(0.0F, 3.0F, 0.0F, 5.0F, 0.3F, 1.0F); // Paper - add back defaults instance
    public static final Codec<AttackRange> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("min_reach", 0.0F).forGetter(AttackRange::minRange), // Paper - diff on change: used in CODEC_DEFAULT above
                ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("max_reach", 3.0F).forGetter(AttackRange::maxRange), // Paper - diff on change: used in CODEC_DEFAULT above
                ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("min_creative_reach", 0.0F).forGetter(AttackRange::minCreativeRange), // Paper - diff on change: used in CODEC_DEFAULT above
                ExtraCodecs.floatRange(0.0F, 64.0F).optionalFieldOf("max_creative_reach", 5.0F).forGetter(AttackRange::maxCreativeRange), // Paper - diff on change: used in CODEC_DEFAULT above
                ExtraCodecs.floatRange(0.0F, 1.0F).optionalFieldOf("hitbox_margin", 0.3F).forGetter(AttackRange::hitboxMargin), // Paper - diff on change: used in CODEC_DEFAULT above
                Codec.floatRange(0.0F, 2.0F).optionalFieldOf("mob_factor", 1.0F).forGetter(AttackRange::mobFactor) // Paper - diff on change: used in CODEC_DEFAULT above
            )
            .apply(instance, AttackRange::new)
    );
    public static final StreamCodec<ByteBuf, AttackRange> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        AttackRange::minRange,
        ByteBufCodecs.FLOAT,
        AttackRange::maxRange,
        ByteBufCodecs.FLOAT,
        AttackRange::minCreativeRange,
        ByteBufCodecs.FLOAT,
        AttackRange::maxCreativeRange,
        ByteBufCodecs.FLOAT,
        AttackRange::hitboxMargin,
        ByteBufCodecs.FLOAT,
        AttackRange::mobFactor,
        AttackRange::new
    );

    public static AttackRange defaultFor(LivingEntity livingEntity) {
        return new AttackRange(
            0.0F,
            (float)livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
            0.0F,
            (float)livingEntity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
            0.0F,
            1.0F
        );
    }

    public HitResult getClosesetHit(Entity attacker, float partialTick, Predicate<Entity> filter) {
        Either<BlockHitResult, Collection<EntityHitResult>> hitEntitiesAlong = ProjectileUtil.getHitEntitiesAlong(
            attacker, this, filter, ClipContext.Block.OUTLINE
        );
        if (hitEntitiesAlong.left().isPresent()) {
            return hitEntitiesAlong.left().get();
        } else {
            Collection<EntityHitResult> collection = hitEntitiesAlong.right().get();
            EntityHitResult entityHitResult = null;
            Vec3 eyePosition = attacker.getEyePosition(partialTick);
            double d = Double.MAX_VALUE;

            for (EntityHitResult entityHitResult1 : collection) {
                double d1 = eyePosition.distanceToSqr(entityHitResult1.getLocation());
                if (d1 < d) {
                    d = d1;
                    entityHitResult = entityHitResult1;
                }
            }

            if (entityHitResult != null) {
                return entityHitResult;
            } else {
                Vec3 headLookAngle = attacker.getHeadLookAngle();
                Vec3 vec3 = attacker.getEyePosition(partialTick).add(headLookAngle);
                return BlockHitResult.miss(vec3, Direction.getApproximateNearest(headLookAngle), BlockPos.containing(vec3));
            }
        }
    }

    public float effectiveMinRange(Entity entity) {
        if (entity instanceof Player player) {
            if (player.isSpectator()) {
                return 0.0F;
            } else {
                return player.isCreative() ? this.minCreativeRange : this.minRange;
            }
        } else {
            return this.minRange * this.mobFactor;
        }
    }

    public float effectiveMaxRange(Entity entity) {
        if (entity instanceof Player player) {
            return player.isCreative() ? this.maxCreativeRange : this.maxRange;
        } else {
            return this.maxRange * this.mobFactor;
        }
    }

    public boolean isInRange(LivingEntity attacker, Vec3 location) {
        return this.isInRange(attacker, location::distanceToSqr, 0.0);
    }

    public boolean isInRange(LivingEntity attacker, AABB boundingBox, double extraBuffer) {
        return this.isInRange(attacker, boundingBox::distanceToSqr, extraBuffer);
    }

    private boolean isInRange(LivingEntity attacker, ToDoubleFunction<Vec3> distanceFunction, double extraBuffer) {
        double squareRoot = Math.sqrt(distanceFunction.applyAsDouble(attacker.getEyePosition()));
        double d = this.effectiveMinRange(attacker) - this.hitboxMargin - extraBuffer;
        double d1 = this.effectiveMaxRange(attacker) + this.hitboxMargin + extraBuffer;
        return squareRoot >= d && squareRoot <= d1;
    }
}
