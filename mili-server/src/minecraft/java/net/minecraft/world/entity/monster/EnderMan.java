package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnderMan extends Monster implements NeutralMob {
    private static final Identifier SPEED_MODIFIER_ATTACKING_ID = Identifier.withDefaultNamespace("attacking");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(
        SPEED_MODIFIER_ATTACKING_ID, 0.15F, AttributeModifier.Operation.ADD_VALUE
    );
    private static final int DELAY_BETWEEN_CREEPY_STARE_SOUND = 400;
    private static final int MIN_DEAGGRESSION_TIME = 600;
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRY_STATE = SynchedEntityData.defineId(
        EnderMan.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE
    );
    private static final EntityDataAccessor<Boolean> DATA_CREEPY = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STARED_AT = SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private long persistentAngerEndTime;
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

    public EnderMan(EntityType<? extends EnderMan> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnderMan.EndermanFreezeWhenLookedAt(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0, 0.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new EnderMan.EndermanLeaveBlockGoal(this));
        this.goalSelector.addGoal(11, new EnderMan.EndermanTakeBlockGoal(this));
        this.targetSelector.addGoal(1, new EnderMan.EndermanLookForPlayerGoal(this, this::isAngryAt));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Endermite.class, true, false));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3F)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    // Paper start - EndermanEscapeEvent
    private boolean tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason reason) {
        return new com.destroystokyo.paper.event.entity.EndermanEscapeEvent((org.bukkit.craftbukkit.entity.CraftEnderman) this.getBukkitEntity(), reason).callEvent();
    }
    // Paper end - EndermanEscapeEvent

    @Override
    // CraftBukkit start - fire event
    public boolean setTarget(@Nullable LivingEntity target, org.bukkit.event.entity.EntityTargetEvent.@Nullable TargetReason reason) {
        if (!super.setTarget(target, reason)) {
            return false;
        }
        target = this.getTarget();
        // CraftBukkit end
        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (target == null) {
            this.targetChangeTime = 0;
            this.entityData.set(DATA_CREEPY, false);
            this.entityData.set(DATA_STARED_AT, false);
            attribute.removeModifier(SPEED_MODIFIER_ATTACKING_ID);
        } else {
            this.targetChangeTime = this.tickCount;
            this.entityData.set(DATA_CREEPY, true);
            if (!attribute.hasModifier(SPEED_MODIFIER_ATTACKING_ID)) {
                attribute.addTransientModifier(SPEED_MODIFIER_ATTACKING);
            }
        }
        return true; // CraftBukkit
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CARRY_STATE, Optional.empty());
        builder.define(DATA_CREEPY, false);
        builder.define(DATA_STARED_AT, false);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void setPersistentAngerEndTime(long absoluteTime) {
        this.persistentAngerEndTime = absoluteTime;
    }

    @Override
    public long getPersistentAngerEndTime() {
        return this.persistentAngerEndTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void playStareSound() {
        if (this.tickCount >= this.lastStareSound + 400) {
            this.lastStareSound = this.tickCount;
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ENDERMAN_STARE, this.getSoundSource(), 2.5F, 1.0F, false);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_CREEPY.equals(key) && this.hasBeenStaredAt() && this.level().isClientSide()) {
            this.playStareSound();
        }

        super.onSyncedDataUpdated(key);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        BlockState carriedBlock = this.getCarriedBlock();
        if (carriedBlock != null) {
            output.store("carriedBlockState", BlockState.CODEC, carriedBlock);
        }

        this.addPersistentAngerSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setCarriedBlock(input.read("carriedBlockState", BlockState.CODEC).filter(blockState -> !blockState.isAir()).orElse(null));
        this.readPersistentAngerSaveData(this.level(), input);
    }

    boolean isBeingStaredBy(Player player) {
        // Paper start - EndermanAttackPlayerEvent
        final boolean shouldAttack = this.isBeingStaredBy0(player);
        final com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent event = new com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent((org.bukkit.entity.Enderman) getBukkitEntity(), (org.bukkit.entity.Player) player.getBukkitEntity());
        event.setCancelled(!shouldAttack);
        return event.callEvent();
    }

    boolean isBeingStaredBy0(Player player) {
        // Paper end - EndermanAttackPlayerEvent
        return LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(player) && this.isLookingAtMe(player, 0.025, true, false, this.getEyeY());
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            for (int i = 0; i < 2; i++) {
                this.level()
                    .addParticle(
                        ParticleTypes.PORTAL,
                        this.getRandomX(0.5),
                        this.getRandomY() - 0.25,
                        this.getRandomZ(0.5),
                        (this.random.nextDouble() - 0.5) * 2.0,
                        -this.random.nextDouble(),
                        (this.random.nextDouble() - 0.5) * 2.0
                    );
            }
        }

        this.jumping = false;
        if (!this.level().isClientSide()) {
            this.updatePersistentAnger((ServerLevel)this.level(), true);
        }

        super.aiStep();
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (level.isBrightOutside() && this.tickCount >= this.targetChangeTime + 600) {
            float lightLevelDependentMagicValue = this.getLightLevelDependentMagicValue();
            if (lightLevelDependentMagicValue > 0.5F
                && level.canSeeSky(this.blockPosition())
                && this.random.nextFloat() * 30.0F < (lightLevelDependentMagicValue - 0.4F) * 2.0F && this.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.RUNAWAY)) { // Paper - EndermanEscapeEvent
                this.setTarget(null);
                this.teleport();
            }
        }

        super.customServerAiStep(level);
    }

    public boolean teleport() {
        if (!this.level().isClientSide() && this.isAlive()) {
            double d = this.getX() + (this.random.nextDouble() - 0.5) * 64.0;
            double d1 = this.getY() + (this.random.nextInt(64) - 32);
            double d2 = this.getZ() + (this.random.nextDouble() - 0.5) * 64.0;
            return this.teleport(d, d1, d2);
        } else {
            return false;
        }
    }

    public boolean teleportTowards(Entity target) {
        Vec3 vec3 = new Vec3(this.getX() - target.getX(), this.getY(0.5) - target.getEyeY(), this.getZ() - target.getZ());
        vec3 = vec3.normalize();
        double d = 16.0;
        double d1 = this.getX() + (this.random.nextDouble() - 0.5) * 8.0 - vec3.x * 16.0;
        double d2 = this.getY() + (this.random.nextInt(16) - 8) - vec3.y * 16.0;
        double d3 = this.getZ() + (this.random.nextDouble() - 0.5) * 8.0 - vec3.z * 16.0;
        return this.teleport(d1, d2, d3);
    }

    private boolean teleport(double x, double y, double z) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(x, y, z);

        // Pufferfish start - single chunk lookup
        net.minecraft.world.level.chunk.LevelChunk chunk = this.level().getChunkIfLoaded(mutableBlockPos);
        if (chunk == null) {
            return false;
        }
        // Pufferfish end
        while (mutableBlockPos.getY() > this.level().getMinY() && !chunk.getBlockState(mutableBlockPos).blocksMotion()) { // Pufferfish

            mutableBlockPos.move(Direction.DOWN);
        }

        BlockState blockState = chunk.getBlockState(mutableBlockPos); // Pufferfish
        boolean flag = blockState.blocksMotion();
        boolean isWater = blockState.getFluidState().is(FluidTags.WATER);
        if (flag && !isWater) {
            Vec3 vec3 = this.position();
            boolean flag1 = this.randomTeleport(x, y, z, true);
            if (flag1) {
                this.level().gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(this));
                if (!this.isSilent()) {
                    this.level().playSound(null, this.xo, this.yo, this.zo, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }
            }

            return flag1;
        } else {
            return false;
        }
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.isCreepy() ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        BlockState carriedBlock = this.getCarriedBlock();
        if (carriedBlock != null) {
            ItemStack itemStack = new ItemStack(Items.DIAMOND_AXE);
            EnchantmentHelper.enchantItemFromProvider(
                itemStack,
                level.registryAccess(),
                VanillaEnchantmentProviders.ENDERMAN_LOOT_DROP,
                level.getCurrentDifficultyAt(this.blockPosition()),
                this.getRandom()
            );
            LootParams.Builder builder = new LootParams.Builder((ServerLevel)this.level())
                .withParameter(LootContextParams.ORIGIN, this.position())
                .withParameter(LootContextParams.TOOL, itemStack)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this);

            for (ItemStack itemStack1 : carriedBlock.getDrops(builder)) {
                this.spawnAtLocation(level, itemStack1);
            }
        }
    }

    public void setCarriedBlock(@Nullable BlockState state) {
        this.entityData.set(DATA_CARRY_STATE, Optional.ofNullable(state));
    }

    public @Nullable BlockState getCarriedBlock() {
        return this.entityData.get(DATA_CARRY_STATE).orElse(null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else {
            AbstractThrownPotion abstractThrownPotion1 = damageSource.getDirectEntity() instanceof AbstractThrownPotion abstractThrownPotion
                ? abstractThrownPotion
                : null;
            if (!damageSource.is(DamageTypeTags.IS_PROJECTILE) && abstractThrownPotion1 == null) { // Paper - EndermanEscapeEvent - diff on change - below logic relies on this path covering non-projectile damage.
                boolean flag = super.hurtServer(level, damageSource, amount);
                if (!(damageSource.getEntity() instanceof LivingEntity) && this.random.nextInt(10) != 0) {
                    if (this.tryEscape(damageSource.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) ? com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.DROWN : com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.CRITICAL_HIT)) { // Paper - EndermanEscapeEvent
                    this.teleport();
                    } // Paper - EndermanEscapeEvent
                }

                return flag;
            } else {
                boolean flag = abstractThrownPotion1 != null && this.hurtWithCleanWater(level, damageSource, abstractThrownPotion1, amount);

                if (this.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.INDIRECT)) { // Paper - EndermanEscapeEvent
                for (int i = 0; i < 64; i++) {
                    if (this.teleport()) {
                        return true;
                    }
                }
                } // Paper - EndermanEscapeEvent

                return flag;
            }
        }
    }

    private boolean hurtWithCleanWater(ServerLevel level, DamageSource damageSource, AbstractThrownPotion potion, float damageAmount) {
        ItemStack item = potion.getItem();
        PotionContents potionContents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return potionContents.is(Potions.WATER) && super.hurtServer(level, damageSource, damageAmount);
    }

    public boolean isCreepy() {
        return this.entityData.get(DATA_CREEPY);
    }

    public boolean hasBeenStaredAt() {
        return this.entityData.get(DATA_STARED_AT);
    }

    public void setBeingStaredAt() {
        this.entityData.set(DATA_STARED_AT, true);
    }

    // Paper start
    public void setCreepy(boolean creepy) {
        this.entityData.set(DATA_CREEPY, creepy);
    }

    public void setHasBeenStaredAt(boolean hasBeenStaredAt) {
        this.entityData.set(DATA_STARED_AT, hasBeenStaredAt);
    }
    // Paper end

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCarriedBlock() != null;
    }

    static class EndermanFreezeWhenLookedAt extends Goal {
        private final EnderMan enderman;
        private @Nullable LivingEntity target;

        public EndermanFreezeWhenLookedAt(EnderMan enderman) {
            this.enderman = enderman;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.target = this.enderman.getTarget();
            if (this.target instanceof Player player) {
                double d = this.target.distanceToSqr(this.enderman);
                return !(d > 256.0) && this.enderman.isBeingStaredBy(player);
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            this.enderman.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.enderman.getLookControl().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        }
    }

    static class EndermanLeaveBlockGoal extends Goal {
        private final EnderMan enderman;

        public EndermanLeaveBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() != null
                && getServerLevel(this.enderman).getGameRules().get(GameRules.MOB_GRIEFING)
                && this.enderman.getRandom().nextInt(reducedTickDelay(2000)) == 0;
        }

        @Override
        public void tick() {
            RandomSource random = this.enderman.getRandom();
            Level level = this.enderman.level();
            int floor = Mth.floor(this.enderman.getX() - 1.0 + random.nextDouble() * 2.0);
            int floor1 = Mth.floor(this.enderman.getY() + random.nextDouble() * 2.0);
            int floor2 = Mth.floor(this.enderman.getZ() - 1.0 + random.nextDouble() * 2.0);
            BlockPos blockPos = new BlockPos(floor, floor1, floor2);
            BlockState blockState = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent endermen from loading chunks
            if (blockState == null) return; // Paper - Prevent endermen from loading chunks
            BlockPos blockPos1 = blockPos.below();
            BlockState blockState1 = level.getBlockState(blockPos1);
            BlockState carriedBlock = this.enderman.getCarriedBlock();
            if (carriedBlock != null) {
                carriedBlock = Block.updateFromNeighbourShapes(carriedBlock, this.enderman.level(), blockPos);
                if (this.canPlaceBlock(level, blockPos, carriedBlock, blockState, blockState1, blockPos1)) {
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.enderman, blockPos, carriedBlock)) { // CraftBukkit - Place event
                    level.setBlock(blockPos, carriedBlock, Block.UPDATE_ALL);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockPos, GameEvent.Context.of(this.enderman, carriedBlock));
                    this.enderman.setCarriedBlock(null);
                    } // CraftBukkit
                }
            }
        }

        private boolean canPlaceBlock(
            Level level,
            BlockPos destinationPos,
            BlockState carriedState,
            BlockState destinationState,
            BlockState belowDestinationState,
            BlockPos belowDestinationPos
        ) {
            return destinationState.isAir()
                && !belowDestinationState.isAir()
                && !belowDestinationState.is(Blocks.BEDROCK)
                && belowDestinationState.isCollisionShapeFullBlock(level, belowDestinationPos)
                && carriedState.canSurvive(level, destinationPos)
                && level.getEntities(this.enderman, AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(destinationPos))).isEmpty();
        }
    }

    static class EndermanLookForPlayerGoal extends NearestAttackableTargetGoal<Player> {
        private final EnderMan enderman;
        private @Nullable Player pendingTarget;
        private int aggroTime;
        private int teleportTime;
        private final TargetingConditions startAggroTargetConditions;
        private final TargetingConditions continueAggroTargetConditions = TargetingConditions.forCombat().ignoreLineOfSight();
        private final TargetingConditions.Selector isAngerInducing;

        public EndermanLookForPlayerGoal(EnderMan enderman, TargetingConditions.@Nullable Selector selector) {
            super(enderman, Player.class, 10, false, false, selector);
            this.enderman = enderman;
            this.isAngerInducing = (entity, level) -> (enderman.isBeingStaredBy((Player)entity) || enderman.isAngryAt(entity, level))
                && !enderman.hasIndirectPassenger(entity);
            this.startAggroTargetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(this.isAngerInducing);
        }

        @Override
        public boolean canUse() {
            this.pendingTarget = getServerLevel(this.enderman).getNearestPlayer(this.startAggroTargetConditions.range(this.getFollowDistance()), this.enderman);
            return this.pendingTarget != null;
        }

        @Override
        public void start() {
            this.aggroTime = this.adjustedTickDelay(5);
            this.teleportTime = 0;
            this.enderman.setBeingStaredAt();
        }

        @Override
        public void stop() {
            this.pendingTarget = null;
            super.stop();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.pendingTarget != null) {
                if (!this.isAngerInducing.test(this.pendingTarget, getServerLevel(this.enderman))) {
                    return false;
                } else {
                    this.enderman.lookAt(this.pendingTarget, 10.0F, 10.0F);
                    return true;
                }
            } else {
                if (this.target != null) {
                    if (this.enderman.hasIndirectPassenger(this.target)) {
                        return false;
                    }

                    if (this.continueAggroTargetConditions.test(getServerLevel(this.enderman), this.enderman, this.target)) {
                        return true;
                    }
                }

                return super.canContinueToUse();
            }
        }

        @Override
        public void tick() {
            if (this.enderman.getTarget() == null) {
                super.setTarget(null);
            }

            if (this.pendingTarget != null) {
                if (--this.aggroTime <= 0) {
                    this.target = this.pendingTarget;
                    this.pendingTarget = null;
                    super.start();
                }
            } else {
                if (this.target != null && !this.enderman.isPassenger()) {
                    if (this.enderman.isBeingStaredBy((Player)this.target)) {
                        if (this.target.distanceToSqr(this.enderman) < 16.0 && this.enderman.tryEscape(com.destroystokyo.paper.event.entity.EndermanEscapeEvent.Reason.STARE)) { // Paper - EndermanEscapeEvent
                            this.enderman.teleport();
                        }

                        this.teleportTime = 0;
                    } else if (this.target.distanceToSqr(this.enderman) > 256.0
                        && this.teleportTime++ >= this.adjustedTickDelay(30)
                        && this.enderman.teleportTowards(this.target)) {
                        this.teleportTime = 0;
                    }
                }

                super.tick();
            }
        }
    }

    static class EndermanTakeBlockGoal extends Goal {
        private final EnderMan enderman;

        public EndermanTakeBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() == null
                && getServerLevel(this.enderman).getGameRules().get(GameRules.MOB_GRIEFING)
                && this.enderman.getRandom().nextInt(reducedTickDelay(20)) == 0;
        }

        @Override
        public void tick() {
            RandomSource random = this.enderman.getRandom();
            Level level = this.enderman.level();
            int floor = Mth.floor(this.enderman.getX() - 2.0 + random.nextDouble() * 4.0);
            int floor1 = Mth.floor(this.enderman.getY() + random.nextDouble() * 3.0);
            int floor2 = Mth.floor(this.enderman.getZ() - 2.0 + random.nextDouble() * 4.0);
            BlockPos blockPos = new BlockPos(floor, floor1, floor2);
            BlockState blockState = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent endermen from loading chunks
            if (blockState == null) return; // Paper - Prevent endermen from loading chunks
            Vec3 vec3 = new Vec3(this.enderman.getBlockX() + 0.5, floor1 + 0.5, this.enderman.getBlockZ() + 0.5);
            Vec3 vec31 = new Vec3(floor + 0.5, floor1 + 0.5, floor2 + 0.5);
            BlockHitResult blockHitResult = level.clip(new ClipContext(vec3, vec31, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.enderman));
            boolean flag = blockHitResult.getBlockPos().equals(blockPos);
            if (blockState.is(BlockTags.ENDERMAN_HOLDABLE) && flag) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this.enderman, blockPos, blockState.getFluidState().createLegacyBlock())) { // CraftBukkit - Place event // Paper - fix wrong block state
                level.removeBlock(blockPos, false);
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockPos, GameEvent.Context.of(this.enderman, blockState));
                this.enderman.setCarriedBlock(blockState.getBlock().defaultBlockState());
                } // CraftBukkit
            }
        }
    }
}
