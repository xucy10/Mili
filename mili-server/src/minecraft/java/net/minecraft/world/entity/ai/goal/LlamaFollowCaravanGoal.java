package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.phys.Vec3;

public class LlamaFollowCaravanGoal extends Goal {
    public final Llama llama;
    private double speedModifier;
    private static final int CARAVAN_LIMIT = 8;
    private int distCheckCounter;

    public LlamaFollowCaravanGoal(Llama llama, double speedModifier) {
        this.llama = llama;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.llama.isLeashed() && !this.llama.inCaravan()) {
            List<Entity> entities = this.llama.level().getEntities(this.llama, this.llama.getBoundingBox().inflate(9.0, 4.0, 9.0), entity1 -> {
                EntityType<?> type = entity1.getType();
                return type == EntityType.LLAMA || type == EntityType.TRADER_LLAMA;
            });
            Llama llama = null;
            double d = Double.MAX_VALUE;

            for (Entity entity : entities) {
                Llama llama1 = (Llama)entity;
                if (llama1.inCaravan() && !llama1.hasCaravanTail()) {
                    double d1 = this.llama.distanceToSqr(llama1);
                    if (!(d1 > d)) {
                        d = d1;
                        llama = llama1;
                    }
                }
            }

            if (llama == null) {
                for (Entity entityx : entities) {
                    Llama llama1 = (Llama)entityx;
                    if (llama1.isLeashed() && !llama1.hasCaravanTail()) {
                        double d1 = this.llama.distanceToSqr(llama1);
                        if (!(d1 > d)) {
                            d = d1;
                            llama = llama1;
                        }
                    }
                }
            }

            if (llama == null) {
                return false;
            } else if (d < 4.0) {
                return false;
            } else if (!llama.isLeashed() && !this.firstIsLeashed(llama, 1)) {
                return false;
            } else {
                this.llama.joinCaravan(llama);
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.llama.inCaravan() && this.llama.getCaravanHead().isAlive() && this.firstIsLeashed(this.llama, 0)) {
            double d = this.llama.distanceToSqr(this.llama.getCaravanHead());
            if (d > 676.0) {
                if (this.speedModifier <= 3.0) {
                    this.speedModifier *= 1.2;
                    this.distCheckCounter = reducedTickDelay(40);
                    return true;
                }

                if (this.distCheckCounter == 0) {
                    return false;
                }
            }

            if (this.distCheckCounter > 0) {
                this.distCheckCounter--;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stop() {
        this.llama.leaveCaravan();
        this.speedModifier = 2.1;
    }

    @Override
    public void tick() {
        if (this.llama.inCaravan()) {
            if (!(this.llama.getLeashHolder() instanceof LeashFenceKnotEntity)) {
                Llama caravanHead = this.llama.getCaravanHead();
                double d = this.llama.distanceTo(caravanHead);
                float f = 2.0F;
                Vec3 vec3 = new Vec3(caravanHead.getX() - this.llama.getX(), caravanHead.getY() - this.llama.getY(), caravanHead.getZ() - this.llama.getZ())
                    .normalize()
                    .scale(Math.max(d - 2.0, 0.0));
                this.llama.getNavigation().moveTo(this.llama.getX() + vec3.x, this.llama.getY() + vec3.y, this.llama.getZ() + vec3.z, this.speedModifier);
            }
        }
    }

    private boolean firstIsLeashed(Llama llama, int leashedQueuePosition) {
        return leashedQueuePosition <= 8
            && llama.inCaravan()
            && (llama.getCaravanHead().isLeashed() || this.firstIsLeashed(llama.getCaravanHead(), ++leashedQueuePosition));
    }
}
