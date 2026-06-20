package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public class TradeWithPlayerGoal extends Goal {
    private final AbstractVillager mob;

    public TradeWithPlayerGoal(AbstractVillager mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isAlive()) {
            return false;
        } else if (this.mob.isInWater()) {
            return false;
        } else if (!this.mob.onGround()) {
            return false;
        } else if (this.mob.hurtMarked) {
            return false;
        } else {
            Player tradingPlayer = this.mob.getTradingPlayer();
            return tradingPlayer != null && !(this.mob.distanceToSqr(tradingPlayer) > 16.0);
        }
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.mob.setTradingPlayer(null);
    }
}
