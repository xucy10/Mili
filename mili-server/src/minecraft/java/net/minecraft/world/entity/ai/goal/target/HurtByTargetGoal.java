package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class HurtByTargetGoal extends TargetGoal {
    private static final TargetingConditions HURT_BY_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private static final int ALERT_RANGE_Y = 10;
    private boolean alertSameType;
    private int timestamp;
    private final Class<?>[] toIgnoreDamage;
    private Class<?> @Nullable [] toIgnoreAlert;

    public HurtByTargetGoal(PathfinderMob mob, Class<?>... toIgnoreDamage) {
        super(mob, true);
        this.toIgnoreDamage = toIgnoreDamage;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int lastHurtByMobTimestamp = this.mob.getLastHurtByMobTimestamp();
        LivingEntity lastHurtByMob = this.mob.getLastHurtByMob();
        if (lastHurtByMobTimestamp != this.timestamp && lastHurtByMob != null) {
            if (lastHurtByMob.getType() == EntityType.PLAYER && getServerLevel(this.mob).getGameRules().get(GameRules.UNIVERSAL_ANGER)) {
                return false;
            } else {
                for (Class<?> clazz : this.toIgnoreDamage) {
                    if (clazz.isAssignableFrom(lastHurtByMob.getClass())) {
                        return false;
                    }
                }

                return this.canAttack(lastHurtByMob, HURT_BY_TARGETING);
            }
        } else {
            return false;
        }
    }

    public HurtByTargetGoal setAlertOthers(Class<?>... reinforcementTypes) {
        this.alertSameType = true;
        this.toIgnoreAlert = reinforcementTypes;
        return this;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.mob.getLastHurtByMob(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY); // CraftBukkit - reason
        this.targetMob = this.mob.getTarget();
        this.timestamp = this.mob.getLastHurtByMobTimestamp();
        this.unseenMemoryTicks = 300;
        if (this.alertSameType) {
            this.alertOthers();
        }

        super.start();
    }

    protected void alertOthers() {
        double followDistance = this.getFollowDistance();
        AABB aabb = AABB.unitCubeFromLowerCorner(this.mob.position()).inflate(followDistance, 10.0, followDistance);
        List<? extends Mob> entitiesOfClass = this.mob
            .level()
            .getEntitiesOfClass((Class<? extends Mob>)this.mob.getClass(), aabb, EntitySelector.NO_SPECTATORS);
        Iterator var5 = entitiesOfClass.iterator();

        while (true) {
            Mob mob;
            while (true) {
                if (!var5.hasNext()) {
                    return;
                }

                mob = (Mob)var5.next();
                if (this.mob != mob
                    && mob.getTarget() == null
                    && (!(this.mob instanceof TamableAnimal) || ((TamableAnimal)this.mob).getOwner() == ((TamableAnimal)mob).getOwner())
                    && !mob.isAlliedTo(this.mob.getLastHurtByMob())) {
                    if (this.toIgnoreAlert == null) {
                        break;
                    }

                    boolean flag = false;

                    for (Class<?> clazz : this.toIgnoreAlert) {
                        if (mob.getClass() == clazz) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        break;
                    }
                }
            }

            this.alertOther(mob, this.mob.getLastHurtByMob());
        }
    }

    protected void alertOther(Mob mob, LivingEntity target) {
        mob.setTarget(target, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY); // CraftBukkit - reason
    }
}
