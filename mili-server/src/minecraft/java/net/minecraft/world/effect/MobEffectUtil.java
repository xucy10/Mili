package net.minecraft.world.effect;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MobEffectUtil {
    public static Component formatDuration(MobEffectInstance effect, float durationFactor, float ticksPerSecond) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int floor = Mth.floor(effect.getDuration() * durationFactor);
            return Component.literal(StringUtil.formatTickDuration(floor, ticksPerSecond));
        }
    }

    public static boolean hasDigSpeed(LivingEntity entity) {
        return entity.hasEffect(MobEffects.HASTE) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity entity) {
        int i = 0;
        int i1 = 0;
        if (entity.hasEffect(MobEffects.HASTE)) {
            i = entity.getEffect(MobEffects.HASTE).getAmplifier();
        }

        if (entity.hasEffect(MobEffects.CONDUIT_POWER)) {
            i1 = entity.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, i1);
    }

    public static boolean hasWaterBreathing(LivingEntity entity) {
        return entity.hasEffect(MobEffects.WATER_BREATHING)
            || entity.hasEffect(MobEffects.CONDUIT_POWER)
            || entity.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS);
    }

    public static boolean shouldEffectsRefillAirsupply(LivingEntity entity) {
        return !entity.hasEffect(MobEffects.BREATH_OF_THE_NAUTILUS)
            || entity.hasEffect(MobEffects.WATER_BREATHING)
            || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(
        ServerLevel level, @Nullable Entity source, Vec3 pos, double radius, MobEffectInstance effect, int duration
    ) {
        // CraftBukkit start
        return MobEffectUtil.addEffectToPlayersAround(level, source, pos, radius, effect, duration, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel level, @Nullable Entity source, Vec3 pos, double radius, MobEffectInstance effect, int duration, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        // Paper start - Add ElderGuardianAppearanceEvent
        return addEffectToPlayersAround(level, source, pos, radius, effect, duration, cause, null);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel level, @Nullable Entity source, Vec3 pos, double radius, MobEffectInstance effect, int duration, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause, java.util.function.@Nullable Predicate<ServerPlayer> playerPredicate) {
        // Paper end - Add ElderGuardianAppearanceEvent
        // CraftBukkit end
        Holder<MobEffect> effect1 = effect.getEffect();
        List<ServerPlayer> players = level.getPlayers(
            // Paper start - Add ElderGuardianAppearanceEvent
            serverPlayer -> {
            final boolean condition = serverPlayer.gameMode.isSurvival()
                && (source == null || !source.isAlliedTo(serverPlayer))
                && pos.closerThan(serverPlayer.position(), radius)
                && (
                !serverPlayer.hasEffect(effect1)
                    || serverPlayer.getEffect(effect1).getAmplifier() < effect.getAmplifier()
                    || serverPlayer.getEffect(effect1).endsWithin(duration - 1)
            );
            if (condition) {
                return playerPredicate == null || playerPredicate.test(serverPlayer); // Only test the player AFTER it is true
            } else {
                return false;
            }
        });
        // Paper end - Add ElderGuardianAppearanceEvent
        players.forEach(serverPlayer -> serverPlayer.addEffect(new MobEffectInstance(effect), source, cause)); // CraftBukkit
        return players;
    }
}
