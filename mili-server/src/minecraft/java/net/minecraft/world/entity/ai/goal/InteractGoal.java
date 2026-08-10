package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public class InteractGoal extends LookAtPlayerGoal {
    public InteractGoal(Mob mob, Class<? extends LivingEntity> lookAtType, float lookDistance) {
        super(mob, lookAtType, lookDistance);
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
    }

    public InteractGoal(Mob mob, Class<? extends LivingEntity> lookAtType, float lookDistance, float probability) {
        super(mob, lookAtType, lookDistance, probability);
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
    }
}
