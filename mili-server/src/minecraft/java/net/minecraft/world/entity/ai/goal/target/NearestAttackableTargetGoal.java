package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class NearestAttackableTargetGoal<T extends LivingEntity> extends TargetGoal {
    private static final int DEFAULT_RANDOM_INTERVAL = 10;
    protected final Class<T> targetType;
    protected final int randomInterval;
    protected @Nullable LivingEntity target;
    protected TargetingConditions targetConditions;

    public NearestAttackableTargetGoal(Mob mob, Class<T> targetType, boolean mustSee) {
        this(mob, targetType, 10, mustSee, false, null);
    }

    public NearestAttackableTargetGoal(Mob mob, Class<T> targetType, boolean mustSee, TargetingConditions.Selector selector) {
        this(mob, targetType, 10, mustSee, false, selector);
    }

    public NearestAttackableTargetGoal(Mob mob, Class<T> targetType, boolean mustSee, boolean mustReach) {
        this(mob, targetType, 10, mustSee, mustReach, null);
    }

    public NearestAttackableTargetGoal(
        Mob mob, Class<T> targetType, int interval, boolean mustSee, boolean mustReach, TargetingConditions.@Nullable Selector selector
    ) {
        super(mob, mustSee, mustReach);
        this.targetType = targetType;
        this.randomInterval = reducedTickDelay(interval);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        this.targetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(selector);
    }

    @Override
    public boolean canUse() {
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        } else {
            this.findTarget();
            return this.target != null;
        }
    }

    protected AABB getTargetSearchArea(double targetDistance) {
        return this.mob.getBoundingBox().inflate(targetDistance, targetDistance, targetDistance);
    }

    protected void findTarget() {
        ServerLevel serverLevel = getServerLevel(this.mob);
        if (this.targetType != Player.class && this.targetType != ServerPlayer.class) {
            this.target = serverLevel.getNearestEntity(
                this.mob.level().getEntitiesOfClass(this.targetType, this.getTargetSearchArea(this.getFollowDistance()), entity -> true),
                this.getTargetConditions(),
                this.mob,
                this.mob.getX(),
                this.mob.getEyeY(),
                this.mob.getZ()
            );
        } else {
            this.target = serverLevel.getNearestPlayer(this.getTargetConditions(), this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
        }
    }

    @Override
    public void start() {
        this.mob.setTarget(this.target, this.target instanceof ServerPlayer ? org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER : org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY); // CraftBukkit - reason
        super.start();
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    private TargetingConditions getTargetConditions() {
        return this.targetConditions.range(this.getFollowDistance());
    }
}
