package net.minecraft.world.entity.monster.illager;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;

public abstract class AbstractIllager extends Raider {
    protected AbstractIllager(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    public AbstractIllager.IllagerArmPose getArmPose() {
        return AbstractIllager.IllagerArmPose.CROSSED;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return (!(target instanceof AbstractVillager) || !target.isBaby()) && super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        return super.considersEntityAsAlly(entity) || entity.getType().is(EntityTypeTags.ILLAGER_FRIENDS) && this.getTeam() == null && entity.getTeam() == null;
    }

    public static enum IllagerArmPose {
        CROSSED,
        ATTACKING,
        SPELLCASTING,
        BOW_AND_ARROW,
        CROSSBOW_HOLD,
        CROSSBOW_CHARGE,
        CELEBRATING,
        NEUTRAL;
    }

    protected class RaiderOpenDoorGoal extends OpenDoorGoal {
        public RaiderOpenDoorGoal(final Raider mob) {
            super(mob, false);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && AbstractIllager.this.hasActiveRaid();
        }
    }
}
