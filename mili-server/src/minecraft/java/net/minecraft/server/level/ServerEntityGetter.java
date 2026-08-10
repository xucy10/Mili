package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public interface ServerEntityGetter extends EntityGetter {
    ServerLevel getLevel();

    default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity source) {
        return this.getNearestEntity(this.getLocalPlayers(), targetingConditions, source, source.getX(), source.getY(), source.getZ()); // Folia - region threading
    }

    default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, LivingEntity source, double x, double y, double z) {
        return this.getNearestEntity(this.getLocalPlayers(), targetingConditions, source, x, y, z); // Folia - region threading
    }

    default @Nullable Player getNearestPlayer(TargetingConditions targetingConditions, double x, double y, double z) {
        return this.getNearestEntity(this.getLocalPlayers(), targetingConditions, null, x, y, z); // Folia - region threading
    }

    default <T extends LivingEntity> @Nullable T getNearestEntity(
        Class<? extends T> entityClass, TargetingConditions targetingConditions, @Nullable LivingEntity source, double x, double y, double z, AABB area
    ) {
        return this.getNearestEntity(this.getEntitiesOfClass(entityClass, area, entity -> true), targetingConditions, source, x, y, z);
    }

    default @Nullable LivingEntity getNearestEntity(
        TagKey<EntityType<?>> types, TargetingConditions targetingConditions, @Nullable LivingEntity source, double x, double y, double z, AABB area
    ) {
        double d = Double.MAX_VALUE;
        LivingEntity livingEntity = null;

        for (LivingEntity livingEntity1 : this.getEntitiesOfClass(LivingEntity.class, area, livingEntity2 -> livingEntity2.getType().is(types))) {
            if (targetingConditions.test(this.getLevel(), source, livingEntity1)) {
                double d1 = livingEntity1.distanceToSqr(x, y, z);
                if (d1 < d) {
                    d = d1;
                    livingEntity = livingEntity1;
                }
            }
        }

        return livingEntity;
    }

    default <T extends LivingEntity> @Nullable T getNearestEntity(
        List<? extends T> entities, TargetingConditions targetingConditions, @Nullable LivingEntity source, double x, double y, double z
    ) {
        double d = -1.0;
        T livingEntity = null;

        for (T livingEntity1 : entities) {
            if (targetingConditions.test(this.getLevel(), source, livingEntity1)) {
                double d1 = livingEntity1.distanceToSqr(x, y, z);
                if (d == -1.0 || d1 < d) {
                    d = d1;
                    livingEntity = livingEntity1;
                }
            }
        }

        return livingEntity;
    }

    default List<Player> getNearbyPlayers(TargetingConditions targetingConditions, LivingEntity source, AABB area) {
        List<Player> list = new ArrayList<>();

        for (Player player : this.getLocalPlayers()) { // Folia - region threading
            if (area.contains(player.getX(), player.getY(), player.getZ()) && targetingConditions.test(this.getLevel(), source, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> entityClass, TargetingConditions targetingConditions, LivingEntity source, AABB area) {
        List<T> entitiesOfClass = this.getEntitiesOfClass(entityClass, area, entity -> true);
        List<T> list = new ArrayList<>();

        for (T livingEntity : entitiesOfClass) {
            if (targetingConditions.test(this.getLevel(), source, livingEntity)) {
                list.add(livingEntity);
            }
        }

        return list;
    }
}
