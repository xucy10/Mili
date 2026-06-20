package net.minecraft.world.entity.boss.wither;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WitherBoss extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> DATA_TARGET_A = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_B = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_C = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Integer>> DATA_TARGETS = ImmutableList.of(DATA_TARGET_A, DATA_TARGET_B, DATA_TARGET_C);
    private static final EntityDataAccessor<Integer> DATA_ID_INV = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final int INVULNERABLE_TICKS = 220;
    private static final int DEFAULT_INVULNERABLE_TICKS = 0;
    private final float[] xRotHeads = new float[2];
    private final float[] yRotHeads = new float[2];
    private final float[] xRotOHeads = new float[2];
    private final float[] yRotOHeads = new float[2];
    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    private boolean canPortal = false; // Paper
    public final ServerBossEvent bossEvent = (ServerBossEvent)new ServerBossEvent(
            this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS
        )
        .setDarkenScreen(true);
    private static final TargetingConditions.Selector LIVING_ENTITY_SELECTOR = (entity, level) -> !entity.getType().is(EntityTypeTags.WITHER_FRIENDS)
        && entity.attackable();
    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forCombat().range(20.0).selector(LIVING_ENTITY_SELECTOR);

    public WitherBoss(EntityType<? extends WitherBoss> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setHealth(this.getMaxHealth());
        this.xpReward = 50;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level);
        flyingPathNavigation.setCanOpenDoors(false);
        flyingPathNavigation.setCanFloat(true);
        return flyingPathNavigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WitherBoss.WitherDoNothingGoal());
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 20.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 0, false, false, LIVING_ENTITY_SELECTOR));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TARGET_A, 0);
        builder.define(DATA_TARGET_B, 0);
        builder.define(DATA_TARGET_C, 0);
        builder.define(DATA_ID_INV, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Invul", this.getInvulnerableTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setInvulnerableTicks(input.getIntOr("Invul", 0));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public void aiStep() {
        Vec3 vec3 = this.getDeltaMovement().multiply(1.0, 0.6, 1.0);
        if (!this.level().isClientSide() && this.getAlternativeTarget(0) > 0) {
            Entity entity = this.level().getEntity(this.getAlternativeTarget(0));
            if (entity != null) {
                double d = vec3.y;
                if (this.getY() < entity.getY() || !this.isPowered() && this.getY() < entity.getY() + 5.0) {
                    d = Math.max(0.0, d);
                    d += 0.3 - d * 0.6F;
                }

                vec3 = new Vec3(vec3.x, d, vec3.z);
                Vec3 vec31 = new Vec3(entity.getX() - this.getX(), 0.0, entity.getZ() - this.getZ());
                if (vec31.horizontalDistanceSqr() > 9.0) {
                    Vec3 vec32 = vec31.normalize();
                    vec3 = vec3.add(vec32.x * 0.3 - vec3.x * 0.6, 0.0, vec32.z * 0.3 - vec3.z * 0.6);
                }
            }
        }

        this.setDeltaMovement(vec3);
        if (vec3.horizontalDistanceSqr() > 0.05) {
            this.setYRot((float)Mth.atan2(vec3.z, vec3.x) * (180.0F / (float)Math.PI) - 90.0F);
        }

        super.aiStep();

        for (int i = 0; i < 2; i++) {
            this.yRotOHeads[i] = this.yRotHeads[i];
            this.xRotOHeads[i] = this.xRotHeads[i];
        }

        for (int i = 0; i < 2; i++) {
            int alternativeTarget = this.getAlternativeTarget(i + 1);
            Entity entity1 = null;
            if (alternativeTarget > 0) {
                entity1 = this.level().getEntity(alternativeTarget);
            }

            if (entity1 != null) {
                double headX = this.getHeadX(i + 1);
                double headY = this.getHeadY(i + 1);
                double headZ = this.getHeadZ(i + 1);
                double d1 = entity1.getX() - headX;
                double d2 = entity1.getEyeY() - headY;
                double d3 = entity1.getZ() - headZ;
                double squareRoot = Math.sqrt(d1 * d1 + d3 * d3);
                float f = (float)(Mth.atan2(d3, d1) * 180.0F / (float)Math.PI) - 90.0F;
                float f1 = (float)(-(Mth.atan2(d2, squareRoot) * 180.0F / (float)Math.PI));
                this.xRotHeads[i] = this.rotlerp(this.xRotHeads[i], f1, 40.0F);
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], f, 10.0F);
            } else {
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], this.yBodyRot, 10.0F);
            }
        }

        boolean isPowered = this.isPowered();

        for (int alternativeTargetx = 0; alternativeTargetx < 3; alternativeTargetx++) {
            double headX1 = this.getHeadX(alternativeTargetx);
            double headY1 = this.getHeadY(alternativeTargetx);
            double headZ1 = this.getHeadZ(alternativeTargetx);
            float f2 = 0.3F * this.getScale();
            this.level()
                .addParticle(
                    ParticleTypes.SMOKE,
                    headX1 + this.random.nextGaussian() * f2,
                    headY1 + this.random.nextGaussian() * f2,
                    headZ1 + this.random.nextGaussian() * f2,
                    0.0,
                    0.0,
                    0.0
                );
            if (isPowered && this.level().random.nextInt(4) == 0) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.5F),
                        headX1 + this.random.nextGaussian() * f2,
                        headY1 + this.random.nextGaussian() * f2,
                        headZ1 + this.random.nextGaussian() * f2,
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }

        if (this.getInvulnerableTicks() > 0) {
            float f3 = 3.3F * this.getScale();

            for (int i1 = 0; i1 < 3; i1++) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.9F),
                        this.getX() + this.random.nextGaussian(),
                        this.getY() + this.random.nextFloat() * f3,
                        this.getZ() + this.random.nextGaussian(),
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.getInvulnerableTicks() > 0) {
            int i = this.getInvulnerableTicks() - 1;
            this.bossEvent.setProgress(1.0F - i / 220.0F);
            if (i <= 0) {
                // CraftBukkit start
                org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent(this.getBukkitEntity(), 7.0F, false);
                level.getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    level.explode(this, this.getX(), this.getEyeY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
                }
                // CraftBukkit end
                if (!this.isSilent()) {
                    // CraftBukkit start - Use relative location for far away sounds
                    // level.globalLevelEvent(LevelEvent.SOUND_WITHER_BOSS_SPAWN, this.blockPosition(), 0);
                    int viewDistance = level.getCraftServer().getViewDistance() * 16;
                    for (ServerPlayer player : level.getPlayersForGlobalSoundGamerule()) { // Paper - respect global sound events gamerule
                        double deltaX = this.getX() - player.getX();
                        double deltaZ = this.getZ() - player.getZ();
                        double distanceSquared = Mth.square(deltaX) + Mth.square(deltaZ);
                        final double soundRadiusSquared = level.getGlobalSoundRangeSquared(config -> config.witherSpawnSoundRadius); // Paper - respect global sound events gamerule
                        if (!level.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS) && distanceSquared > soundRadiusSquared) continue; // Spigot // Paper - respect global sound events gamerule
                        if (distanceSquared > Mth.square(viewDistance)) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_WITHER_BOSS_SPAWN, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_WITHER_BOSS_SPAWN, this.blockPosition(), 0, true));
                        }
                    }
                    // CraftBukkit end
                }
            }

            this.setInvulnerableTicks(i);
            if (this.tickCount % 10 == 0) {
                this.heal(10.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.WITHER_SPAWN); // CraftBukkit
            }
        } else {
            super.customServerAiStep(level);

            for (int ix = 1; ix < 3; ix++) {
                if (this.tickCount >= this.nextHeadUpdate[ix - 1]) {
                    this.nextHeadUpdate[ix - 1] = this.tickCount + 10 + this.random.nextInt(10);
                    if ((level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) && this.idleHeadUpdates[ix - 1]++ > 15) {
                        float f = 10.0F;
                        float f1 = 5.0F;
                        double randomDouble = Mth.nextDouble(this.random, this.getX() - 10.0, this.getX() + 10.0);
                        double randomDouble1 = Mth.nextDouble(this.random, this.getY() - 5.0, this.getY() + 5.0);
                        double randomDouble2 = Mth.nextDouble(this.random, this.getZ() - 10.0, this.getZ() + 10.0);
                        this.performRangedAttack(ix + 1, randomDouble, randomDouble1, randomDouble2, true);
                        this.idleHeadUpdates[ix - 1] = 0;
                    }

                    int alternativeTarget = this.getAlternativeTarget(ix);
                    if (alternativeTarget > 0) {
                        LivingEntity livingEntity = (LivingEntity)level.getEntity(alternativeTarget);
                        if (livingEntity != null
                            && this.canAttack(livingEntity)
                            && !(this.distanceToSqr(livingEntity) > 900.0)
                            && this.hasLineOfSight(livingEntity)) {
                            this.performRangedAttack(ix + 1, livingEntity);
                            this.nextHeadUpdate[ix - 1] = this.tickCount + 40 + this.random.nextInt(20);
                            this.idleHeadUpdates[ix - 1] = 0;
                        } else {
                            this.setAlternativeTarget(ix, 0);
                        }
                    } else {
                        List<LivingEntity> nearbyEntities = level.getNearbyEntities(
                            LivingEntity.class, TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0, 8.0, 20.0)
                        );
                        if (!nearbyEntities.isEmpty()) {
                            LivingEntity livingEntity1 = nearbyEntities.get(this.random.nextInt(nearbyEntities.size()));
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(this, livingEntity1, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY).isCancelled()) continue; // CraftBukkit
                            this.setAlternativeTarget(ix, livingEntity1.getId());
                        }
                    }
                }
            }

            if (this.getTarget() != null) {
                this.setAlternativeTarget(0, this.getTarget().getId());
            } else {
                this.setAlternativeTarget(0, 0);
            }

            if (this.destroyBlocksTick > 0) {
                this.destroyBlocksTick--;
                if (this.destroyBlocksTick == 0 && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    boolean flag = false;
                    int alternativeTarget = Mth.floor(this.getBbWidth() / 2.0F + 1.0F);
                    int floor = Mth.floor(this.getBbHeight());

                    for (BlockPos blockPos : BlockPos.betweenClosed(
                        this.getBlockX() - alternativeTarget,
                        this.getBlockY(),
                        this.getBlockZ() - alternativeTarget,
                        this.getBlockX() + alternativeTarget,
                        this.getBlockY() + floor,
                        this.getBlockZ() + alternativeTarget
                    )) {
                        BlockState blockState = level.getBlockState(blockPos);
                        if (canDestroy(blockState)) {
                            // CraftBukkit start
                            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockPos, blockState.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                                continue;
                            }
                            // CraftBukkit end
                            flag = level.destroyBlock(blockPos, true, this) || flag;
                        }
                    }

                    if (flag) {
                        level.levelEvent(null, LevelEvent.SOUND_WITHER_BLOCK_BREAK, this.blockPosition(), 0);
                    }
                }
            }

            if (this.tickCount % 20 == 0) {
                this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
            }

            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(BlockState state) {
        return !state.isAir() && !state.is(BlockTags.WITHER_IMMUNE);
    }

    public void makeInvulnerable() {
        this.setInvulnerableTicks(220);
        this.bossEvent.setProgress(0.0F);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private double getHeadX(int head) {
        if (head <= 0) {
            return this.getX();
        } else {
            float f = (this.yBodyRot + 180 * (head - 1)) * (float) (Math.PI / 180.0);
            float cos = Mth.cos(f);
            return this.getX() + cos * 1.3 * this.getScale();
        }
    }

    private double getHeadY(int head) {
        float f = head <= 0 ? 3.0F : 2.2F;
        return this.getY() + f * this.getScale();
    }

    private double getHeadZ(int head) {
        if (head <= 0) {
            return this.getZ();
        } else {
            float f = (this.yBodyRot + 180 * (head - 1)) * (float) (Math.PI / 180.0);
            float sin = Mth.sin(f);
            return this.getZ() + sin * 1.3 * this.getScale();
        }
    }

    private float rotlerp(float angle, float targetAngle, float max) {
        float f = Mth.wrapDegrees(targetAngle - angle);
        if (f > max) {
            f = max;
        }

        if (f < -max) {
            f = -max;
        }

        return angle + f;
    }

    private void performRangedAttack(int head, LivingEntity target) {
        this.performRangedAttack(head, target.getX(), target.getY() + target.getEyeHeight() * 0.5, target.getZ(), head == 0 && this.random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(int head, double x, double y, double z, boolean isDangerous) {
        if (!this.isSilent()) {
            this.level().levelEvent(null, LevelEvent.SOUND_WITHER_BOSS_SHOOT, this.blockPosition(), 0);
        }

        double headX = this.getHeadX(head);
        double headY = this.getHeadY(head);
        double headZ = this.getHeadZ(head);
        double d = x - headX;
        double d1 = y - headY;
        double d2 = z - headZ;
        Vec3 vec3 = new Vec3(d, d1, d2);
        WitherSkull witherSkull = new WitherSkull(this.level(), this, vec3.normalize());
        witherSkull.setOwner(this);
        if (isDangerous) {
            witherSkull.setDangerous(true);
        }

        witherSkull.setPos(headX, headY, headZ);
        this.level().addFreshEntity(witherSkull);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.performRangedAttack(0, target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableTo(level, damageSource)) {
            return false;
        } else if (damageSource.is(DamageTypeTags.WITHER_IMMUNE_TO) || damageSource.getEntity() instanceof WitherBoss) {
            return false;
        } else if (this.getInvulnerableTicks() > 0 && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            if (this.isPowered()) {
                Entity directEntity = damageSource.getDirectEntity();
                if (directEntity instanceof AbstractArrow || directEntity instanceof WindCharge) {
                    return false;
                }
            }

            Entity directEntity = damageSource.getEntity();
            if (directEntity != null && directEntity.getType().is(EntityTypeTags.WITHER_FRIENDS)) {
                return false;
            } else {
                if (this.destroyBlocksTick <= 0) {
                    this.destroyBlocksTick = 20;
                }

                for (int i = 0; i < this.idleHeadUpdates.length; i++) {
                    this.idleHeadUpdates[i] = this.idleHeadUpdates[i] + 3;
                }

                return super.hurtServer(level, damageSource, amount);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        ItemEntity itemEntity = this.spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Items.NETHER_STAR), net.minecraft.world.phys.Vec3.ZERO, ItemEntity::setExtendedLifetime); // Paper - Restore vanilla drops behavior; spawnAtLocation returns null so modify the item entity with a consumer
        if (itemEntity != null) {
            itemEntity.setExtendedLifetime(); // Paper - diff on change
        }
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) { // Paper - allow changing despawnInPeaceful
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)
            .add(Attributes.MOVEMENT_SPEED, 0.6F)
            .add(Attributes.FLYING_SPEED, 0.6F)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ARMOR, 4.0);
    }

    public float[] getHeadYRots() {
        return this.yRotHeads;
    }

    public float[] getHeadXRots() {
        return this.xRotHeads;
    }

    public int getInvulnerableTicks() {
        return this.entityData.get(DATA_ID_INV);
    }

    public void setInvulnerableTicks(int invulnerableTicks) {
        this.entityData.set(DATA_ID_INV, invulnerableTicks);
    }

    public int getAlternativeTarget(int head) {
        return this.entityData.get(DATA_TARGETS.get(head));
    }

    public void setAlternativeTarget(int targetOffset, int newId) {
        this.entityData.set(DATA_TARGETS.get(targetOffset), newId);
    }

    public boolean isPowered() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return this.canPortal; // Paper
    }

    // Paper start
    public void setCanTravelThroughPortals(boolean canPortal) {
        this.canPortal = canPortal;
    }
    // Paper end

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return (!effectInstance.is(MobEffects.WITHER) || !this.level().paperConfig().entities.mobEffects.immuneToWitherEffect.wither) && super.canBeAffected(effectInstance);
    }

    class WitherDoNothingGoal extends Goal {
        public WitherDoNothingGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return WitherBoss.this.getInvulnerableTicks() > 0;
        }
    }
}
