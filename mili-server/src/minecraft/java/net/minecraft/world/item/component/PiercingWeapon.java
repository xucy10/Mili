package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;

public record PiercingWeapon(boolean dealsKnockback, boolean dismounts, Optional<Holder<SoundEvent>> sound, Optional<Holder<SoundEvent>> hitSound) {
    public static final Codec<PiercingWeapon> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.BOOL.optionalFieldOf("deals_knockback", true).forGetter(PiercingWeapon::dealsKnockback),
                Codec.BOOL.optionalFieldOf("dismounts", false).forGetter(PiercingWeapon::dismounts),
                SoundEvent.CODEC.optionalFieldOf("sound").forGetter(PiercingWeapon::sound),
                SoundEvent.CODEC.optionalFieldOf("hit_sound").forGetter(PiercingWeapon::hitSound)
            )
            .apply(instance, PiercingWeapon::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PiercingWeapon> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        PiercingWeapon::dealsKnockback,
        ByteBufCodecs.BOOL,
        PiercingWeapon::dismounts,
        SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional),
        PiercingWeapon::sound,
        SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional),
        PiercingWeapon::hitSound,
        PiercingWeapon::new
    );

    public void makeSound(Entity entity) {
        this.sound
            .ifPresent(
                holder -> entity.level()
                    .playSound(entity, entity.getX(), entity.getY(), entity.getZ(), (Holder<SoundEvent>)holder, entity.getSoundSource(), 1.0F, 1.0F)
            );
    }

    public void makeHitSound(Entity entity) {
        this.hitSound
            .ifPresent(
                holder -> entity.level()
                    .playSound(null, entity.getX(), entity.getY(), entity.getZ(), (Holder<SoundEvent>)holder, entity.getSoundSource(), 1.0F, 1.0F)
            );
    }

    public static boolean canHitEntity(Entity entity, Entity target) {
        return !target.isInvulnerable()
            && target.isAlive()
            && (
                target instanceof Interaction
                    || target.canBeHitByProjectile()
                        && !(target instanceof Player player && entity instanceof Player player1 && !player1.canHarmPlayer(player))
                        && !entity.isPassengerOfSameVehicle(target)
            );
    }

    public void attack(LivingEntity entity, EquipmentSlot slot) {
        float f = (float)entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AttackRange attackRange = entity.entityAttackRange();
        boolean flag = false;

        for (EntityHitResult entityHitResult : ProjectileUtil.getHitEntitiesAlong(
                entity, attackRange, entity1 -> canHitEntity(entity, entity1), ClipContext.Block.COLLIDER
            )
            .<Collection<EntityHitResult>>map(blockHitResult -> List.of(), collection -> collection)) {
            flag |= entity.stabAttack(slot, entityHitResult.getEntity(), f, true, this.dealsKnockback, this.dismounts);
        }

        entity.onAttack();
        entity.lungeForwardMaybe();
        if (flag) {
            this.makeHitSound(entity);
        }

        this.makeSound(entity);
        entity.swing(InteractionHand.MAIN_HAND, false);
    }
}
