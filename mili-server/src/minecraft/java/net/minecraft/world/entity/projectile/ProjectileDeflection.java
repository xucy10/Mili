package net.minecraft.world.entity.projectile;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ProjectileDeflection {
    ProjectileDeflection NONE = (projectile, entity, random) -> {};
    ProjectileDeflection REVERSE = (projectile, entity, random) -> {
        float f = 170.0F + random.nextFloat() * 20.0F;
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.5));
        projectile.setYRot(projectile.getYRot() + f);
        projectile.yRotO += f;
        projectile.needsSync = true;
    };
    ProjectileDeflection AIM_DEFLECT = (projectile, entity, random) -> {
        if (entity != null) {
            Vec3 lookAngle = entity.getLookAngle();
            projectile.setDeltaMovement(lookAngle);
            projectile.needsSync = true;
        }
    };
    ProjectileDeflection MOMENTUM_DEFLECT = (projectile, entity, random) -> {
        if (entity != null) {
            Vec3 vec3 = entity.getDeltaMovement().normalize();
            projectile.setDeltaMovement(vec3);
            projectile.needsSync = true;
        }
    };

    void deflect(Projectile projectile, @Nullable Entity entity, RandomSource random);
}
