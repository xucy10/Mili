package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Animal extends AgeableMob {
    protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
    private static final int DEFAULT_IN_LOVE_TIME = 0;
    public int inLove = 0;
    public @Nullable EntityReference<ServerPlayer> loveCause;
    public @Nullable ItemStack breedItem; // CraftBukkit - Add breedItem variable

    protected Animal(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAnimalAttributes() {
        return Mob.createMobAttributes().add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep(level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            this.inLove--;
            if (this.inLove % 10 == 0) {
                double d = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
            }
        }
    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(ServerLevel level, DamageSource damageSource, float amount, org.bukkit.event.entity.EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(level, damageSource, amount, event);
        if (!damageResult) return false;
        this.resetLove();
        return true;
        // CraftBukkit end
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("InLove", this.inLove);
        EntityReference.store(this.loveCause, output, "LoveCause");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.inLove = input.getIntOr("InLove", 0);
        this.loveCause = EntityReference.read(input, "LoveCause");
    }

    public static boolean checkAnimalSpawnRules(
        EntityType<? extends Animal> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        boolean flag = EntitySpawnReason.ignoresLightRequirements(spawnReason) || isBrightEnoughToSpawn(level, pos);
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && flag;
    }

    protected static boolean isBrightEnoughToSpawn(BlockAndTintGetter level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 1 + this.random.nextInt(3);
    }

    public abstract boolean isFood(ItemStack stack);

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (this.isFood(itemInHand)) {
            int age = this.getAge();
            if (player instanceof ServerPlayer serverPlayer && age == 0 && this.canFallInLove()) {
                final ItemStack breedCopy = itemInHand.copy(); // Paper - Fix EntityBreedEvent copying
                this.usePlayerItem(player, hand, itemInHand);
                this.setInLove(serverPlayer, breedCopy); // Paper - Fix EntityBreedEvent copying
                this.playEatingSound();
                return InteractionResult.SUCCESS_SERVER;
            }

            if (this.isBaby()) {
                this.usePlayerItem(player, hand, itemInHand);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-age), true);
                this.playEatingSound();
                return InteractionResult.SUCCESS;
            }

            if (this.level().isClientSide()) {
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(player, hand);
    }

    protected void playEatingSound() {
    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Fix EntityBreedEvent copying
    public void setInLove(@Nullable Player player) {
        // Paper start - Fix EntityBreedEvent copying
        this.setInLove(player, null);
    }

    public void setInLove(@Nullable Player player, @Nullable ItemStack breedItemCopy) {
        if (breedItemCopy != null) this.breedItem = breedItemCopy;
        // Paper end - Fix EntityBreedEvent copying
        // CraftBukkit start
        org.bukkit.event.entity.EntityEnterLoveModeEvent entityEnterLoveModeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityEnterLoveModeEvent(player, this, 600);
        if (entityEnterLoveModeEvent.isCancelled()) {
            this.breedItem = null; // Paper - Fix EntityBreedEvent copying; clear if cancelled
            return;
        }
        this.inLove = entityEnterLoveModeEvent.getTicksInLove();
        // CraftBukkit end
        if (player instanceof ServerPlayer serverPlayer) {
            this.loveCause = EntityReference.of(serverPlayer);
        }

        this.level().broadcastEntityEvent(this, EntityEvent.IN_LOVE_HEARTS);
    }

    public void setInLoveTime(int inLove) {
        this.inLove = inLove;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    public @Nullable ServerPlayer getLoveCause() {
        return EntityReference.get(this.loveCause, this.level(), ServerPlayer.class);
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    public boolean canMate(Animal otherAnimal) {
        return otherAnimal != this && otherAnimal.getClass() == this.getClass() && this.isInLove() && otherAnimal.isInLove();
    }

    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        AgeableMob breedOffspring = this.getBreedOffspring(level, partner);
        if (breedOffspring != null) {
            breedOffspring.setBaby(true);
            breedOffspring.snapTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            // CraftBukkit start - Call EntityBreedEvent
            ServerPlayer breeder = Optional.ofNullable(this.getLoveCause()).or(() -> Optional.ofNullable(partner.getLoveCause())).orElse(null);
            int experience = this.getRandom().nextInt(7) + 1;
            org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(breedOffspring, this, partner, breeder, this.breedItem, experience);
            if (entityBreedEvent.isCancelled()) {
                this.resetLove();
                partner.resetLove();
                return;
            }
            experience = entityBreedEvent.getExperience();

            this.finalizeSpawnChildFromBreeding(level, partner, breedOffspring, experience);
            level.addFreshEntityWithPassengers(breedOffspring, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING);
            // CraftBukkit end - Call EntityBreedEvent
        }
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby) {
        // CraftBukkit start - Call EntityBreedEvent
        this.finalizeSpawnChildFromBreeding(level, animal, baby, this.getRandom().nextInt(7) + 1);
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby, int experience) {
        // CraftBukkit end - Call EntityBreedEvent
        // Paper start - Call EntityBreedEvent
        ServerPlayer player = this.getLoveCause();
        if (player == null) player = animal.getLoveCause();
        if (player != null) {
            // Paper end - Call EntityBreedEvent
            player.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(player, this, animal, baby);
        } // Paper - Call EntityBreedEvent
        this.setAge(6000);
        animal.setAge(6000);
        this.resetLove();
        animal.resetLove();
        level.broadcastEntityEvent(this, EntityEvent.IN_LOVE_HEARTS);
        if (experience > 0 && level.getGameRules().get(GameRules.MOB_DROPS)) { // Paper - Call EntityBreedEvent
            level.addFreshEntity(new ExperienceOrb(level, this.position(), net.minecraft.world.phys.Vec3.ZERO, experience, org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, player, baby)); // Paper - Call EntityBreedEvent, add spawn context
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EntityEvent.IN_LOVE_HEARTS) {
            for (int i = 0; i < 7; i++) {
                double d = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, d1, d2);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction motionDirection = this.getMotionDirection();
        if (motionDirection.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(passenger);
        } else {
            int[][] ints = DismountHelper.offsetsForDirection(motionDirection);
            BlockPos blockPos = this.blockPosition();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Pose pose : passenger.getDismountPoses()) {
                AABB localBoundsForPose = passenger.getLocalBoundsForPose(pose);

                for (int[] ints1 : ints) {
                    mutableBlockPos.set(blockPos.getX() + ints1[0], blockPos.getY(), blockPos.getZ() + ints1[1]);
                    double blockFloorHeight = this.level().getBlockFloorHeight(mutableBlockPos);
                    if (DismountHelper.isBlockFloorValid(blockFloorHeight)) {
                        Vec3 vec3 = Vec3.upFromBottomCenterOf(mutableBlockPos, blockFloorHeight);
                        if (DismountHelper.canDismountTo(this.level(), passenger, localBoundsForPose.move(vec3))) {
                            passenger.setPose(pose);
                            return vec3;
                        }
                    }
                }
            }

            return super.getDismountLocationForPassenger(passenger);
        }
    }
}
