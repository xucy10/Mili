package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

public class SonicBoom extends Behavior<Warden> {
    private static final int DISTANCE_XZ = 15;
    private static final int DISTANCE_Y = 20;
    private static final double KNOCKBACK_VERTICAL = 0.5;
    private static final double KNOCKBACK_HORIZONTAL = 2.5;
    public static final int COOLDOWN = 40;
    private static final int TICKS_BEFORE_PLAYING_SOUND = Mth.ceil(34.0);
    private static final int DURATION = Mth.ceil(60.0F);

    public SonicBoom() {
        super(
            ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.SONIC_BOOM_COOLDOWN,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN,
                MemoryStatus.REGISTERED,
                MemoryModuleType.SONIC_BOOM_SOUND_DELAY,
                MemoryStatus.REGISTERED
            ),
            DURATION
        );
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Warden owner) {
        return owner.closerThan(owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get(), 15.0, 20.0);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Warden entity, long gameTime) {
        return true;
    }

    @Override
    protected void start(ServerLevel level, Warden entity, long gameTime) {
        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, DURATION);
        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_DELAY, Unit.INSTANCE, TICKS_BEFORE_PLAYING_SOUND);
        level.broadcastEntityEvent(entity, EntityEvent.SONIC_CHARGE);
        entity.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
    }

    @Override
    protected void tick(ServerLevel level, Warden owner, long gameTime) {
        owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(livingEntity -> owner.getLookControl().setLookAt(livingEntity.position()));
        if (!owner.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY)
            && !owner.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN)) {
            owner.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, Unit.INSTANCE, DURATION - TICKS_BEFORE_PLAYING_SOUND);
            owner.getBrain()
                .getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(owner::canTargetEntity)
                .filter(livingEntity -> owner.closerThan(livingEntity, 15.0, 20.0))
                .ifPresent(livingEntity -> {
                    Vec3 vec3 = owner.position().add(owner.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, owner.getYRot()));
                    Vec3 vec31 = livingEntity.getEyePosition().subtract(vec3);
                    Vec3 vec32 = vec31.normalize();
                    int i = Mth.floor(vec31.length()) + 7;

                    for (int i1 = 1; i1 < i; i1++) {
                        Vec3 vec33 = vec3.add(vec32.scale(i1));
                        level.sendParticles(ParticleTypes.SONIC_BOOM, vec33.x, vec33.y, vec33.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }

                    owner.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
                    if (livingEntity.hurtServer(level, level.damageSources().sonicBoom(owner), 10.0F)) {
                        double d = 0.5 * (1.0 - livingEntity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                        double d1 = 2.5 * (1.0 - livingEntity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                        livingEntity.push(vec32.x() * d1, vec32.y() * d, vec32.z() * d1, owner); // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                    }
                });
        }
    }

    @Override
    protected void stop(ServerLevel level, Warden entity, long gameTime) {
        setCooldown(entity, 40);
    }

    public static void setCooldown(LivingEntity entity, int cooldown) {
        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, cooldown);
    }
}
