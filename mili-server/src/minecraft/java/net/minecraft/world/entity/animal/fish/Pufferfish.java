package net.minecraft.world.entity.animal.fish;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Pufferfish extends AbstractFish {
    private static final EntityDataAccessor<Integer> PUFF_STATE = SynchedEntityData.defineId(Pufferfish.class, EntityDataSerializers.INT);
    int inflateCounter;
    int deflateTimer;
    private static final TargetingConditions.Selector SCARY_MOB = (entity, level) -> !(entity instanceof Player player && player.isCreative())
        && !entity.getType().is(EntityTypeTags.NOT_SCARY_FOR_PUFFERFISH);
    static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forNonCombat()
        .ignoreInvisibilityTesting()
        .ignoreLineOfSight()
        .selector(SCARY_MOB);
    public static final int STATE_SMALL = 0;
    public static final int STATE_MID = 1;
    public static final int STATE_FULL = 2;
    private static final int DEFAULT_PUFF_STATE = 0;

    public Pufferfish(EntityType<? extends Pufferfish> type, Level level) {
        super(type, level);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PUFF_STATE, 0);
    }

    public int getPuffState() {
        return this.entityData.get(PUFF_STATE);
    }

    public void setPuffState(int puffState) {
        this.entityData.set(PUFF_STATE, puffState);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (PUFF_STATE.equals(key)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PuffState", this.getPuffState());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPuffState(Math.min(input.getIntOr("PuffState", 0), 2));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.PUFFERFISH_BUCKET);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Pufferfish.PufferfishPuffGoal(this));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && this.isEffectiveAi()) {
            if (this.inflateCounter > 0) {
                boolean increase = true; // Paper - Add PufferFishStateChangeEvent
                if (this.getPuffState() == 0) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), STATE_MID).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.makeSound(SoundEvents.PUFFER_FISH_BLOW_UP);
                    this.setPuffState(STATE_MID);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                } else if (this.inflateCounter > 40 && this.getPuffState() == 1) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), STATE_FULL).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.makeSound(SoundEvents.PUFFER_FISH_BLOW_UP);
                    this.setPuffState(STATE_FULL);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                }

                if (increase) // Paper - Add PufferFishStateChangeEvent
                this.inflateCounter++;
            } else if (this.getPuffState() != 0) {
                boolean increase = true; // Paper - Add PufferFishStateChangeEvent
                if (this.deflateTimer > 60 && this.getPuffState() == 2) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), STATE_MID).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.makeSound(SoundEvents.PUFFER_FISH_BLOW_OUT);
                    this.setPuffState(STATE_MID);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                } else if (this.deflateTimer > 100 && this.getPuffState() == 1) {
                    if (new io.papermc.paper.event.entity.PufferFishStateChangeEvent((org.bukkit.entity.PufferFish) getBukkitEntity(), STATE_SMALL).callEvent()) { // Paper - Add PufferFishStateChangeEvent
                    this.makeSound(SoundEvents.PUFFER_FISH_BLOW_OUT);
                    this.setPuffState(STATE_SMALL);
                    } else { increase = false; } // Paper - Add PufferFishStateChangeEvent
                }

                if (increase) // Paper - Add PufferFishStateChangeEvent
                this.deflateTimer++;
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && this.getPuffState() > 0) {
            for (Mob mob : this.level()
                .getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(0.3), mob1 -> TARGETING_CONDITIONS.test(serverLevel, this, mob1))) {
                if (mob.isAlive()) {
                    this.touch(serverLevel, mob);
                }
            }
        }
    }

    private void touch(ServerLevel level, Mob mob) {
        int puffState = this.getPuffState();
        if (mob.hurtServer(level, this.damageSources().mobAttack(this), 1 + puffState)) {
            mob.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * puffState, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            this.playSound(SoundEvents.PUFFER_FISH_STING, 1.0F, 1.0F);
        }
    }

    @Override
    public void playerTouch(Player entity) {
        int puffState = this.getPuffState();
        if (entity instanceof ServerPlayer serverPlayer
            && puffState > 0
            && entity.hurtServer(serverPlayer.level(), this.damageSources().mobAttack(this), 1 + puffState)) {
            if (!this.isSilent()) {
                serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.PUFFER_FISH_STING, 0.0F));
            }

            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60 * puffState, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
        }
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PUFFER_FISH_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PUFFER_FISH_HURT;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.PUFFER_FISH_FLOP;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(getScale(this.getPuffState()));
    }

    private static float getScale(int puffState) {
        switch (puffState) {
            case 0:
                return 0.5F;
            case 1:
                return 0.7F;
            default:
                return 1.0F;
        }
    }

    static class PufferfishPuffGoal extends Goal {
        private final Pufferfish fish;

        public PufferfishPuffGoal(Pufferfish fish) {
            this.fish = fish;
        }

        @Override
        public boolean canUse() {
            List<LivingEntity> entitiesOfClass = this.fish
                .level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    this.fish.getBoundingBox().inflate(2.0),
                    entity -> Pufferfish.TARGETING_CONDITIONS.test(getServerLevel(this.fish), this.fish, entity)
                );
            return !entitiesOfClass.isEmpty();
        }

        @Override
        public void start() {
            this.fish.inflateCounter = 1;
            this.fish.deflateTimer = 0;
        }

        @Override
        public void stop() {
            this.fish.inflateCounter = 0;
        }
    }
}
