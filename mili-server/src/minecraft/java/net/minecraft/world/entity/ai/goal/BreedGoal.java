package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

public class BreedGoal extends Goal {
    private static final TargetingConditions PARTNER_TARGETING = TargetingConditions.forNonCombat().range(8.0).ignoreLineOfSight();
    protected final Animal animal;
    private final Class<? extends Animal> partnerClass;
    protected final ServerLevel level;
    protected @Nullable Animal partner;
    private int loveTime;
    private final double speedModifier;

    public BreedGoal(Animal animal, double speedModifier) {
        this(animal, speedModifier, (Class<? extends Animal>)animal.getClass());
    }

    public BreedGoal(Animal animal, double speedModifier, Class<? extends Animal> partnerClass) {
        this.animal = animal;
        this.level = getServerLevel(animal);
        this.partnerClass = partnerClass;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.animal.isInLove()) {
            return false;
        } else {
            this.partner = this.getFreePartner();
            return this.partner != null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.partner.isAlive() && this.partner.isInLove() && this.loveTime < 60 && !this.partner.isPanicking();
    }

    @Override
    public void stop() {
        this.partner = null;
        this.loveTime = 0;
    }

    @Override
    public void tick() {
        this.animal.getLookControl().setLookAt(this.partner, 10.0F, this.animal.getMaxHeadXRot());
        this.animal.getNavigation().moveTo(this.partner, this.speedModifier);
        this.loveTime++;
        if (this.loveTime >= this.adjustedTickDelay(60) && this.animal.distanceToSqr(this.partner) < 9.0) {
            this.breed();
        }
    }

    private @Nullable Animal getFreePartner() {
        List<? extends Animal> nearbyEntities = this.level
            .getNearbyEntities(this.partnerClass, PARTNER_TARGETING, this.animal, this.animal.getBoundingBox().inflate(8.0));
        double d = Double.MAX_VALUE;
        Animal animal = null;

        for (Animal animal1 : nearbyEntities) {
            if (this.animal.canMate(animal1) && !animal1.isPanicking() && this.animal.distanceToSqr(animal1) < d) {
                animal = animal1;
                d = this.animal.distanceToSqr(animal1);
            }
        }

        return animal;
    }

    protected void breed() {
        this.animal.spawnChildFromBreeding(this.level, this.partner);
    }
}
