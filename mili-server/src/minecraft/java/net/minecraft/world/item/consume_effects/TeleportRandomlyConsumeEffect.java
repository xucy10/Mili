package net.minecraft.world.item.consume_effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public record TeleportRandomlyConsumeEffect(float diameter) implements ConsumeEffect {
    private static final float DEFAULT_DIAMETER = 16.0F;
    public static final MapCodec<TeleportRandomlyConsumeEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("diameter", 16.0F).forGetter(TeleportRandomlyConsumeEffect::diameter))
            .apply(instance, TeleportRandomlyConsumeEffect::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportRandomlyConsumeEffect> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, TeleportRandomlyConsumeEffect::diameter, TeleportRandomlyConsumeEffect::new
    );

    public TeleportRandomlyConsumeEffect() {
        this(16.0F);
    }

    @Override
    public ConsumeEffect.Type<TeleportRandomlyConsumeEffect> getType() {
        return ConsumeEffect.Type.TELEPORT_RANDOMLY;
    }

    @Override
    public boolean apply(Level level, ItemStack stack, LivingEntity entity) {
        boolean flag = false;

        for (int i = 0; i < 16; i++) {
            double d = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * this.diameter;
            double d1 = Mth.clamp(
                entity.getY() + (entity.getRandom().nextDouble() - 0.5) * this.diameter,
                (double)level.getMinY(),
                (double)(level.getMinY() + ((ServerLevel)level).getLogicalHeight() - 1)
            );
            double d2 = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * this.diameter;
            if (entity.isPassenger()) {
                entity.stopRiding();
            }

            Vec3 vec3 = entity.position();
            // CraftBukkit start - handle canceled status of teleport event
            java.util.Optional<Boolean> status = entity.randomTeleport(d, d1, d2, true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT);

            // teleport event was canceled, no more tries
            if (status.isEmpty()) break;
            if (status.get()) {
                // CraftBukkit end
                level.gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(entity));
                SoundSource soundSource;
                SoundEvent soundEvent;
                if (entity instanceof Fox) {
                    soundEvent = SoundEvents.FOX_TELEPORT;
                    soundSource = SoundSource.NEUTRAL;
                } else {
                    soundEvent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                    soundSource = SoundSource.PLAYERS;
                }

                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), soundEvent, soundSource);
                entity.resetFallDistance();
                flag = true;
                break;
            }
        }

        if (flag && entity instanceof Player player) {
            player.resetCurrentImpulseContext();
        }

        return flag;
    }
}
