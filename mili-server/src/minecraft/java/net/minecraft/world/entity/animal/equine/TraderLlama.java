package net.minecraft.world.entity.animal.equine;

import java.util.EnumSet;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TraderLlama extends Llama {
    private static final int DEFAULT_DESPAWN_DELAY = 47999;
    private int despawnDelay = 47999;

    public TraderLlama(EntityType<? extends TraderLlama> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isTraderLlama() {
        return true;
    }

    @Override
    protected @Nullable Llama makeNewLlama() {
        return EntityType.TRADER_LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DespawnDelay", this.despawnDelay);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.despawnDelay = input.getIntOr("DespawnDelay", 47999);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0));
        this.targetSelector.addGoal(1, new TraderLlama.TraderLlamaDefendWanderingTraderGoal(this));
        this.targetSelector
            .addGoal(2, new NearestAttackableTargetGoal<>(this, Zombie.class, true, (entity, level) -> entity.getType() != EntityType.ZOMBIFIED_PIGLIN));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
    }

    public void setDespawnDelay(int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    @Override
    protected void doPlayerRide(Player player) {
        Entity leashHolder = this.getLeashHolder();
        if (!(leashHolder instanceof WanderingTrader)) {
            super.doPlayerRide(player);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.maybeDespawn();
        }
    }

    private void maybeDespawn() {
        if (this.canDespawn()) {
            this.despawnDelay = this.isLeashedToWanderingTrader() ? ((WanderingTrader)this.getLeashHolder()).getDespawnDelay() - 1 : this.despawnDelay - 1;
            if (this.despawnDelay <= 0) {
                this.removeLeash();
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    private boolean canDespawn() {
        return !this.isTamed() && !this.isLeashedToSomethingOtherThanTheWanderingTrader() && !this.hasExactlyOnePlayerPassenger();
    }

    private boolean isLeashedToWanderingTrader() {
        return this.getLeashHolder() instanceof WanderingTrader;
    }

    private boolean isLeashedToSomethingOtherThanTheWanderingTrader() {
        return this.isLeashed() && !this.isLeashedToWanderingTrader();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnReason == EntitySpawnReason.EVENT) {
            this.setAge(0);
        }

        if (spawnGroupData == null) {
            spawnGroupData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    protected static class TraderLlamaDefendWanderingTraderGoal extends TargetGoal {
        private final Llama llama;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public TraderLlamaDefendWanderingTraderGoal(Llama llama) {
            super(llama, false);
            this.llama = llama;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!this.llama.isLeashed()) {
                return false;
            } else if (!(this.llama.getLeashHolder() instanceof WanderingTrader wanderingTrader)) {
                return false;
            } else {
                this.ownerLastHurtBy = wanderingTrader.getLastHurtByMob();
                int lastHurtByMobTimestamp = wanderingTrader.getLastHurtByMobTimestamp();
                return lastHurtByMobTimestamp != this.timestamp && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
            }
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurtBy, org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER); // CraftBukkit
            Entity leashHolder = this.llama.getLeashHolder();
            if (leashHolder instanceof WanderingTrader) {
                this.timestamp = ((WanderingTrader)leashHolder).getLastHurtByMobTimestamp();
            }

            super.start();
        }
    }

    // KioCG start
    @Override
    public boolean shouldTickHot() {
        return super.shouldTickHot() && !this.canDespawn();
    }
    // KioCG end
}
