package net.minecraft.world.entity.ai.goal.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public abstract class TargetGoal extends Goal {
    private static final int EMPTY_REACH_CACHE = 0;
    private static final int CAN_REACH_CACHE = 1;
    private static final int CANT_REACH_CACHE = 2;
    protected final Mob mob;
    protected final boolean mustSee;
    private final boolean mustReach;
    private int reachCache;
    private int reachCacheTime;
    private int unseenTicks;
    protected @Nullable LivingEntity targetMob;
    protected int unseenMemoryTicks = 60;

    public TargetGoal(Mob mob, boolean mustSee) {
        this(mob, mustSee, false);
    }

    public TargetGoal(Mob mob, boolean mustSee, boolean mustReach) {
        this.mob = mob;
        this.mustSee = mustSee;
        this.mustReach = mustReach;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            target = this.targetMob;
        }

        if (target == null) {
            return false;
        } else if (!this.mob.canAttack(target)) {
            return false;
        } else {
            Team team = this.mob.getTeam();
            Team team1 = target.getTeam();
            if (team != null && team1 == team) {
                return false;
            } else {
                double followDistance = this.getFollowDistance();
                if (this.mob.distanceToSqr(target) > followDistance * followDistance) {
                    return false;
                } else {
                    if (this.mustSee) {
                        if (this.mob.getSensing().hasLineOfSight(target)) {
                            this.unseenTicks = 0;
                        } else if (++this.unseenTicks > reducedTickDelay(this.unseenMemoryTicks)) {
                            return false;
                        }
                    }

                    this.mob.setTarget(target, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY); // CraftBukkit
                    return true;
                }
            }
        }
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void start() {
        this.reachCache = 0;
        this.reachCacheTime = 0;
        this.unseenTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.setTarget(null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET); // CraftBukkit
        this.targetMob = null;
    }

    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        if (potentialTarget == null) {
            return false;
        } else if (!targetPredicate.test(getServerLevel(this.mob), this.mob, potentialTarget)) {
            return false;
        } else if (!this.mob.isWithinHome(potentialTarget.blockPosition())) {
            return false;
        } else {
            if (this.mustReach) {
                if (--this.reachCacheTime <= 0) {
                    this.reachCache = 0;
                }

                if (this.reachCache == 0) {
                    this.reachCache = this.canReach(potentialTarget) ? 1 : 2;
                }

                if (this.reachCache == 2) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean canReach(LivingEntity target) {
        this.reachCacheTime = reducedTickDelay(10 + this.mob.getRandom().nextInt(5));
        Path path = this.mob.getNavigation().createPath(target, 0);
        if (path == null) {
            return false;
        } else {
            Node endNode = path.getEndNode();
            if (endNode == null) {
                return false;
            } else {
                int i = endNode.x - target.getBlockX();
                int i1 = endNode.z - target.getBlockZ();
                return i * i + i1 * i1 <= 2.25;
            }
        }
    }

    public TargetGoal setUnseenMemoryTicks(int unseenMemoryTicks) {
        this.unseenMemoryTicks = unseenMemoryTicks;
        return this;
    }
}
