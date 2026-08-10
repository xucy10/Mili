package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public interface NeutralMob {
    String TAG_ANGER_END_TIME = "anger_end_time";
    String TAG_ANGRY_AT = "angry_at";
    long NO_ANGER_END_TIME = -1L;

    long getPersistentAngerEndTime();

    default void setTimeToRemainAngry(long time) {
        this.setPersistentAngerEndTime(this.level().getGameTime() + time);
    }

    void setPersistentAngerEndTime(long absoluteTime);

    @Nullable EntityReference<LivingEntity> getPersistentAngerTarget();

    void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target);

    void startPersistentAngerTimer();

    Level level();

    default void addPersistentAngerSaveData(ValueOutput output) {
        output.putLong("anger_end_time", this.getPersistentAngerEndTime());
        output.storeNullable("angry_at", EntityReference.codec(), this.getPersistentAngerTarget());
    }

    default void readPersistentAngerSaveData(Level level, ValueInput input) {
        Optional<Long> _long = input.getLong("anger_end_time");
        if (_long.isPresent()) {
            this.setPersistentAngerEndTime(_long.get());
        } else {
            Optional<Integer> _int = input.getInt("AngerTime");
            if (_int.isPresent()) {
                this.setTimeToRemainAngry(_int.get().intValue());
            } else {
                this.setPersistentAngerEndTime(-1L);
            }
        }

        if (level instanceof ServerLevel) {
            this.setPersistentAngerTarget(EntityReference.read(input, "angry_at"));
            // Paper - Prevent entity loading causing async lookups; Moved diff to separate method
            // If this entity already survived its first tick, e.g. is loaded and ticked in sync, actively
            // tick the initial persistent anger.
            // If not, let the first tick on the baseTick call the method later down the line.
            if (this instanceof Entity entity && !entity.firstTick) this.tickInitialPersistentAnger(level);
        }
    }

    default void updatePersistentAnger(ServerLevel level, boolean updateAnger) {
        LivingEntity target = this.getTarget();
        EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
        if (target != null && target.isDeadOrDying() && persistentAngerTarget != null && persistentAngerTarget.matches(target) && target instanceof Mob) {
            this.stopBeingAngry();
        } else {
            if (target != null) {
                // Paper start - Backport fix for MC-305388 from 26.1-snapshot-5
                boolean newTarget = persistentAngerTarget == null || !persistentAngerTarget.matches(target);
                if (newTarget) {
                    this.setPersistentAngerTarget(EntityReference.of(target));
                }

                if (newTarget || updateAnger) {
                    this.startPersistentAngerTimer();
                }
                // Paper end - Backport fix for MC-305388 from 26.1-snapshot-5
            }

            if (persistentAngerTarget != null && !this.isAngry() && (target == null || !isValidPlayerTarget(target) || !updateAnger)) {
                this.stopBeingAngry();
            }
        }
    }

    private static boolean isValidPlayerTarget(LivingEntity target) {
        return target instanceof Player player && !player.isCreative() && !player.isSpectator();
    }

    default boolean isAngryAt(LivingEntity entity, ServerLevel level) {
        if (!this.canAttack(entity)) {
            return false;
        } else if (isValidPlayerTarget(entity) && this.isAngryAtAllPlayers(level)) {
            return true;
        } else {
            EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
            return persistentAngerTarget != null && persistentAngerTarget.matches(entity);
        }
    }

    default boolean isAngryAtAllPlayers(ServerLevel level) {
        return level.getGameRules().get(GameRules.UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
    }

    default boolean isAngry() {
        long persistentAngerEndTime = this.getPersistentAngerEndTime();
        if (persistentAngerEndTime > 0L) {
            long l = persistentAngerEndTime - this.level().getGameTime();
            return l > 0L;
        } else {
            return false;
        }
    }

    default void playerDied(ServerLevel level, Player player) {
        if (level.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            EntityReference<LivingEntity> persistentAngerTarget = this.getPersistentAngerTarget();
            if (persistentAngerTarget != null && persistentAngerTarget.matches(player)) {
                this.stopBeingAngry();
            }
        }
    }

    default void forgetCurrentTargetAndRefreshUniversalAnger() {
        this.stopBeingAngry();
        this.startPersistentAngerTimer();
    }

    default void stopBeingAngry() {
        this.setLastHurtByMob(null);
        this.setPersistentAngerTarget(null);
        this.setTarget(null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET); // CraftBukkit
        this.setPersistentAngerEndTime(-1L);
    }

    @Nullable LivingEntity getLastHurtByMob();

    void setLastHurtByMob(@Nullable LivingEntity livingEntity);

    void setTarget(@Nullable LivingEntity target);

    boolean setTarget(@Nullable LivingEntity target, org.bukkit.event.entity.EntityTargetEvent.@Nullable TargetReason reason); // CraftBukkit

    boolean canAttack(LivingEntity entity);

    @Nullable LivingEntity getTarget();

    // Paper start - Prevent entity loading causing async lookups
    // Update last hurt when ticking
    default void tickInitialPersistentAnger(Level level) {
        LivingEntity target = EntityReference.getLivingEntity(this.getPersistentAngerTarget(), level);
        if (target != null) {
            this.setTarget(target, null);
        }
    }
    // Paper end - Prevent entity loading causing async lookups
}
