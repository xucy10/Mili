package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record ApplyEntityImpulse(Vec3 direction, Vec3 coordinateScale, LevelBasedValue magnitude) implements EnchantmentEntityEffect {
    public static final MapCodec<ApplyEntityImpulse> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Vec3.CODEC.fieldOf("direction").forGetter(ApplyEntityImpulse::direction),
                Vec3.CODEC.fieldOf("coordinate_scale").forGetter(ApplyEntityImpulse::coordinateScale),
                LevelBasedValue.CODEC.fieldOf("magnitude").forGetter(ApplyEntityImpulse::magnitude)
            )
            .apply(instance, ApplyEntityImpulse::new)
    );
    private static final int POST_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 10;

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        Vec3 lookAngle = entity.getLookAngle();
        Vec3 vec3 = lookAngle.addLocalCoordinates(this.direction).multiply(this.coordinateScale).scale(this.magnitude.calculate(enchantmentLevel));
        entity.addDeltaMovement(vec3);
        entity.hurtMarked = true;
        entity.needsSync = true;
        if (entity instanceof Player player) {
            player.applyPostImpulseGraceTime(10);
        }
    }

    @Override
    public MapCodec<ApplyEntityImpulse> codec() {
        return CODEC;
    }
}
