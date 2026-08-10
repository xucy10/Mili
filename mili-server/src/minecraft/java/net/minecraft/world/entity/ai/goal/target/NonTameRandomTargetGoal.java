package net.minecraft.world.entity.ai.goal.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;

public class NonTameRandomTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
    private final TamableAnimal tamableMob;

    public NonTameRandomTargetGoal(TamableAnimal tamableMob, Class<T> targetType, boolean mustSee, TargetingConditions.@Nullable Selector selector) {
        super(tamableMob, targetType, 10, mustSee, false, selector);
        this.tamableMob = tamableMob;
    }

    @Override
    public boolean canUse() {
        return !this.tamableMob.isTame() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetConditions != null ? this.targetConditions.test(getServerLevel(this.mob), this.mob, this.target) : super.canContinueToUse();
    }
}
