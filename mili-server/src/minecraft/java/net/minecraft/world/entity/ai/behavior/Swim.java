package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;

public class Swim<T extends Mob> extends Behavior<T> {
    private final float chance;

    public Swim(float chance) {
        super(ImmutableMap.of());
        this.chance = chance;
    }

    public static <T extends Mob> boolean shouldSwim(T mob) {
        return mob.isInWater() && mob.getFluidHeight(FluidTags.WATER) > mob.getFluidJumpThreshold() || mob.isInLava();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        return shouldSwim(owner);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob entity, long gameTime) {
        return this.checkExtraStartConditions(level, entity);
    }

    @Override
    protected void tick(ServerLevel level, Mob owner, long gameTime) {
        if (owner.getRandom().nextFloat() < this.chance) {
            owner.getJumpControl().jump();
        }
    }
}
