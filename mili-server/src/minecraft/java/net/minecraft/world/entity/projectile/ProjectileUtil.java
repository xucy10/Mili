package net.minecraft.world.entity.projectile;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ProjectileUtil {
    public static final float DEFAULT_ENTITY_HIT_RESULT_MARGIN = 0.3F;

    public static HitResult getHitResultOnMoveVector(Entity projectile, Predicate<Entity> filter) {
        Vec3 deltaMovement = projectile.getDeltaMovement();
        Level level = projectile.level();
        Vec3 vec3 = projectile.position();
        return getHitResult(vec3, projectile, filter, deltaMovement, level, computeMargin(projectile), ClipContext.Block.COLLIDER);
    }

    public static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
        Entity attacker, AttackRange attackRange, Predicate<Entity> filter, ClipContext.Block clipContext
    ) {
        Vec3 headLookAngle = attacker.getHeadLookAngle();
        Vec3 eyePosition = attacker.getEyePosition();
        Vec3 vec3 = eyePosition.add(headLookAngle.scale(attackRange.effectiveMinRange(attacker)));
        double d = attacker.getKnownMovement().dot(headLookAngle);
        Vec3 vec31 = eyePosition.add(headLookAngle.scale(attackRange.effectiveMaxRange(attacker) + Math.max(0.0, d)));
        return getHitEntitiesAlong(attacker, eyePosition, vec3, filter, vec31, attackRange.hitboxMargin(), clipContext);
    }

    public static HitResult getHitResultOnMoveVector(Entity projectile, Predicate<Entity> filter, ClipContext.Block clipContext) {
        Vec3 deltaMovement = projectile.getDeltaMovement();
        Level level = projectile.level();
        Vec3 vec3 = projectile.position();
        return getHitResult(vec3, projectile, filter, deltaMovement, level, computeMargin(projectile), clipContext);
    }

    public static HitResult getHitResultOnViewVector(Entity source, Predicate<Entity> filter, double scale) {
        Vec3 vec3 = source.getViewVector(0.0F).scale(scale);
        Level level = source.level();
        Vec3 eyePosition = source.getEyePosition();
        return getHitResult(eyePosition, source, filter, vec3, level, 0.0F, ClipContext.Block.COLLIDER);
    }

    private static HitResult getHitResult(
        Vec3 pos, Entity source, Predicate<Entity> filter, Vec3 deltaMovement, Level level, float margin, ClipContext.Block clipContext
    ) {
        Vec3 vec3 = pos.add(deltaMovement);
        HitResult hitResult = level.clipIncludingBorder(new ClipContext(pos, vec3, clipContext, ClipContext.Fluid.NONE, source));
        if (hitResult.getType() != HitResult.Type.MISS) {
            vec3 = hitResult.getLocation();
        }

        HitResult entityHitResult = getEntityHitResult(
            level, source, pos, vec3, source.getBoundingBox().expandTowards(deltaMovement).inflate(1.0), filter, margin
        );
        if (entityHitResult != null) {
            hitResult = entityHitResult;
        }

        return hitResult;
    }

    private static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
        Entity source, Vec3 startVec, Vec3 minReachVec, Predicate<Entity> filter, Vec3 maxReachVec, float margin, ClipContext.Block clipContext
    ) {
        Level level = source.level();
        BlockHitResult blockHitResult = level.clipIncludingBorder(new ClipContext(startVec, maxReachVec, clipContext, ClipContext.Fluid.NONE, source));
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            maxReachVec = blockHitResult.getLocation();
            if (startVec.distanceToSqr(maxReachVec) < startVec.distanceToSqr(minReachVec)) {
                return Either.left(blockHitResult);
            }
        }

        AABB aabb = AABB.ofSize(minReachVec, margin, margin, margin).expandTowards(maxReachVec.subtract(minReachVec)).inflate(1.0);
        Collection<EntityHitResult> manyEntityHitResult = getManyEntityHitResult(
            level, source, minReachVec, maxReachVec, aabb, filter, margin, clipContext, true
        );
        return !manyEntityHitResult.isEmpty() ? Either.right(manyEntityHitResult) : Either.left(blockHitResult);
    }

    public static @Nullable EntityHitResult getEntityHitResult(
        Entity source, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, double distance
    ) {
        Level level = source.level();
        double d = distance;
        Entity entity = null;
        Vec3 vec3 = null;

        for (Entity entity1 : level.getEntities(source, boundingBox, filter)) {
            AABB aabb = entity1.getBoundingBox().inflate(entity1.getPickRadius());
            Optional<Vec3> optional = aabb.clip(startVec, endVec);
            if (aabb.contains(startVec)) {
                if (d >= 0.0) {
                    entity = entity1;
                    vec3 = optional.orElse(startVec);
                    d = 0.0;
                }
            } else if (optional.isPresent()) {
                Vec3 vec31 = optional.get();
                double d1 = startVec.distanceToSqr(vec31);
                if (d1 < d || d == 0.0) {
                    if (entity1.getRootVehicle() == source.getRootVehicle()) {
                        if (d == 0.0) {
                            entity = entity1;
                            vec3 = vec31;
                        }
                    } else {
                        entity = entity1;
                        vec3 = vec31;
                        d = d1;
                    }
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity, vec3);
    }

    public static @Nullable EntityHitResult getEntityHitResult(
        Level level, Projectile source, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter
    ) {
        return getEntityHitResult(level, source, startVec, endVec, boundingBox, filter, computeMargin(source));
    }

    public static float computeMargin(Entity entity) {
        return Math.max(0.0F, Math.min(0.3F, (entity.tickCount - 2) / 20.0F));
    }

    public static @Nullable EntityHitResult getEntityHitResult(
        Level level, Entity source, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, float margin
    ) {
        double d = Double.MAX_VALUE;
        Optional<Vec3> optional = Optional.empty();
        Entity entity = null;

        for (Entity entity1 : level.getEntities(source, boundingBox, filter)) {
            AABB aabb = entity1.getBoundingBox().inflate(margin);
            Optional<Vec3> optional1 = aabb.clip(startVec, endVec);
            if (optional1.isPresent()) {
                double d1 = startVec.distanceToSqr(optional1.get());
                if (d1 < d) {
                    entity = entity1;
                    d = d1;
                    optional = optional1;
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity, optional.get());
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(
        Level level, Entity source, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, boolean includeFromEntity
    ) {
        return getManyEntityHitResult(
            level, source, startVec, endVec, boundingBox, filter, computeMargin(source), ClipContext.Block.COLLIDER, includeFromEntity
        );
    }

    public static Collection<EntityHitResult> getManyEntityHitResult(
        Level level,
        Entity source,
        Vec3 startVec,
        Vec3 endVec,
        AABB boundingBox,
        Predicate<Entity> filter,
        float margin,
        ClipContext.Block clipType,
        boolean includeFromEntity
    ) {
        List<EntityHitResult> list = new ArrayList<>();

        for (Entity entity : level.getEntities(source, boundingBox, filter)) {
            AABB boundingBox1 = entity.getBoundingBox();
            if (includeFromEntity && boundingBox1.contains(startVec)) {
                list.add(new EntityHitResult(entity, startVec));
            } else {
                Optional<Vec3> optional = boundingBox1.clip(startVec, endVec);
                if (optional.isPresent()) {
                    list.add(new EntityHitResult(entity, optional.get()));
                } else if (!(margin <= 0.0)) {
                    Optional<Vec3> optional1 = boundingBox1.inflate(margin).clip(startVec, endVec);
                    if (!optional1.isEmpty()) {
                        Vec3 vec3 = optional1.get();
                        Vec3 center = boundingBox1.getCenter();
                        BlockHitResult blockHitResult = level.clipIncludingBorder(new ClipContext(vec3, center, clipType, ClipContext.Fluid.NONE, source));
                        if (blockHitResult.getType() != HitResult.Type.MISS) {
                            center = blockHitResult.getLocation();
                        }

                        Optional<Vec3> optional2 = entity.getBoundingBox().clip(vec3, center);
                        if (optional2.isPresent()) {
                            list.add(new EntityHitResult(entity, optional2.get()));
                        }
                    }
                }
            }
        }

        return list;
    }

    public static void rotateTowardsMovement(Entity projectile, float rotationSpeed) {
        Vec3 deltaMovement = projectile.getDeltaMovement();
        if (deltaMovement.lengthSqr() != 0.0) {
            double d = deltaMovement.horizontalDistance();
            projectile.setYRot((float)(Mth.atan2(deltaMovement.z, deltaMovement.x) * 180.0F / (float)Math.PI) + 90.0F);
            projectile.setXRot((float)(Mth.atan2(d, deltaMovement.y) * 180.0F / (float)Math.PI) - 90.0F);

            while (projectile.getXRot() - projectile.xRotO < -180.0F) {
                projectile.xRotO -= 360.0F;
            }

            while (projectile.getXRot() - projectile.xRotO >= 180.0F) {
                projectile.xRotO += 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO < -180.0F) {
                projectile.yRotO -= 360.0F;
            }

            while (projectile.getYRot() - projectile.yRotO >= 180.0F) {
                projectile.yRotO += 360.0F;
            }

            projectile.setXRot(Mth.lerp(rotationSpeed, projectile.xRotO, projectile.getXRot()));
            projectile.setYRot(Mth.lerp(rotationSpeed, projectile.yRotO, projectile.getYRot()));
        }
    }

    public static InteractionHand getWeaponHoldingHand(LivingEntity shooter, Item weapon) {
        return shooter.getMainHandItem().is(weapon) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static AbstractArrow getMobArrow(LivingEntity shooter, ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        ArrowItem arrowItem = (ArrowItem)(arrow.getItem() instanceof ArrowItem ? arrow.getItem() : Items.ARROW);
        AbstractArrow abstractArrow = arrowItem.createArrow(shooter.level(), arrow, shooter, weapon);
        abstractArrow.setBaseDamageFromMob(velocity);
        return abstractArrow;
    }
}
