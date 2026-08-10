package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class Shulker extends AbstractGolem implements Enemy {
    private static final Identifier COVERED_ARMOR_MODIFIER_ID = Identifier.withDefaultNamespace("covered");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(
        COVERED_ARMOR_MODIFIER_ID, 20.0, AttributeModifier.Operation.ADD_VALUE
    );
    protected static final EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Byte> DATA_PEEK_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Byte> DATA_COLOR_ID = SynchedEntityData.defineId(Shulker.class, EntityDataSerializers.BYTE);
    private static final int TELEPORT_STEPS = 6;
    private static final byte NO_COLOR = 16;
    private static final byte DEFAULT_COLOR = 16;
    private static final int MAX_TELEPORT_DISTANCE = 8;
    private static final int OTHER_SHULKER_SCAN_RADIUS = 8;
    private static final int OTHER_SHULKER_LIMIT = 5;
    private static final float PEEK_PER_TICK = 0.05F;
    private static final byte DEFAULT_PEEK = 0;
    private static final Direction DEFAULT_ATTACH_FACE = Direction.DOWN;
    static final Vector3f FORWARD = Util.make(() -> {
        Vec3i unitVec3i = Direction.SOUTH.getUnitVec3i();
        return new Vector3f(unitVec3i.getX(), unitVec3i.getY(), unitVec3i.getZ());
    });
    private static final float MAX_SCALE = 3.0F;
    private float currentPeekAmountO;
    private float currentPeekAmount;
    private @Nullable BlockPos clientOldAttachPosition;
    private int clientSideTeleportInterpolation;
    private static final float MAX_LID_OPEN = 1.0F;

    public Shulker(EntityType<? extends Shulker> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.lookControl = new Shulker.ShulkerLookControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.02F, true));
        this.goalSelector.addGoal(4, new Shulker.ShulkerAttackGoal());
        this.goalSelector.addGoal(7, new Shulker.ShulkerPeekGoal());
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, this.getClass()).setAlertOthers());
        this.targetSelector.addGoal(2, new Shulker.ShulkerNearestAttackGoal(this));
        this.targetSelector.addGoal(3, new Shulker.ShulkerDefenseAttackGoal(this));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACH_FACE_ID, DEFAULT_ATTACH_FACE);
        builder.define(DATA_PEEK_ID, (byte)0);
        builder.define(DATA_COLOR_ID, (byte)16);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Shulker.ShulkerBodyRotationControl(this);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setAttachFace(input.read("AttachFace", Direction.LEGACY_ID_CODEC).orElse(DEFAULT_ATTACH_FACE));
        this.entityData.set(DATA_PEEK_ID, input.getByteOr("Peek", (byte)0));
        this.entityData.set(DATA_COLOR_ID, input.getByteOr("Color", (byte)16));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("AttachFace", Direction.LEGACY_ID_CODEC, this.getAttachFace());
        output.putByte("Peek", this.entityData.get(DATA_PEEK_ID));
        output.putByte("Color", this.entityData.get(DATA_COLOR_ID));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && !this.isPassenger() && !this.canStayAt(this.blockPosition(), this.getAttachFace())) {
            this.findNewAttachment();
        }

        if (this.updatePeekAmount()) {
            this.onPeekAmountChange();
        }

        if (this.level().isClientSide()) {
            if (this.clientSideTeleportInterpolation > 0) {
                this.clientSideTeleportInterpolation--;
            } else {
                this.clientOldAttachPosition = null;
            }
        }
    }

    private void findNewAttachment() {
        Direction direction = this.findAttachableSurface(this.blockPosition());
        if (direction != null) {
            this.setAttachFace(direction);
        } else {
            this.teleportSomewhere();
        }
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        float physicalPeek = getPhysicalPeek(this.currentPeekAmount);
        Direction opposite = this.getAttachFace().getOpposite();
        return getProgressAabb(this.getScale(), opposite, physicalPeek, position);
    }

    private static float getPhysicalPeek(float peek) {
        return 0.5F - Mth.sin((0.5F + peek) * (float) Math.PI) * 0.5F;
    }

    private boolean updatePeekAmount() {
        this.currentPeekAmountO = this.currentPeekAmount;
        float f = this.getRawPeekAmount() * 0.01F;
        if (this.currentPeekAmount == f) {
            return false;
        } else {
            if (this.currentPeekAmount > f) {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
            } else {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
            }

            return true;
        }
    }

    private void onPeekAmountChange() {
        this.reapplyPosition();
        float physicalPeek = getPhysicalPeek(this.currentPeekAmount);
        float physicalPeek1 = getPhysicalPeek(this.currentPeekAmountO);
        Direction opposite = this.getAttachFace().getOpposite();
        float f = (physicalPeek - physicalPeek1) * this.getScale();
        if (!(f <= 0.0F)) {
            for (Entity entity : this.level()
                .getEntities(
                    this,
                    getProgressDeltaAabb(this.getScale(), opposite, physicalPeek1, physicalPeek, this.position()),
                    EntitySelector.NO_SPECTATORS.and(entity1 -> !entity1.isPassengerOfSameVehicle(this))
                )) {
                if (!(entity instanceof Shulker) && !entity.noPhysics) {
                    entity.move(MoverType.SHULKER, new Vec3(f * opposite.getStepX(), f * opposite.getStepY(), f * opposite.getStepZ()));
                }
            }
        }
    }

    public static AABB getProgressAabb(float scale, Direction expansionDirection, float peek, Vec3 position) {
        return getProgressDeltaAabb(scale, expansionDirection, -1.0F, peek, position);
    }

    public static AABB getProgressDeltaAabb(float scale, Direction expansionDirection, float currentPeek, float oldPeek, Vec3 position) {
        AABB aabb = new AABB(-scale * 0.5, 0.0, -scale * 0.5, scale * 0.5, scale, scale * 0.5);
        double d = Math.max(currentPeek, oldPeek);
        double d1 = Math.min(currentPeek, oldPeek);
        AABB aabb1 = aabb.expandTowards(
                expansionDirection.getStepX() * d * scale, expansionDirection.getStepY() * d * scale, expansionDirection.getStepZ() * d * scale
            )
            .contract(
                -expansionDirection.getStepX() * (1.0 + d1) * scale,
                -expansionDirection.getStepY() * (1.0 + d1) * scale,
                -expansionDirection.getStepZ() * (1.0 + d1) * scale
            );
        return aabb1.move(position.x, position.y, position.z);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean triggerEvents) {
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = null;
            this.clientSideTeleportInterpolation = 0;
        }

        this.setAttachFace(Direction.DOWN);
        return super.startRiding(entity, force, triggerEvents);
    }

    @Override
    // Paper start - Force entity dismount during teleportation
    public void stopRiding(boolean suppressCancellation) {
        super.stopRiding(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = this.blockPosition();
        }

        this.yBodyRotO = 0.0F;
        this.yBodyRot = 0.0F;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setYRot(0.0F);
        this.yHeadRot = this.getYRot();
        this.setOldPosAndRot();
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(type, movement);
        }
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
    }

    @Override
    public void setPos(double x, double y, double z) {
        BlockPos blockPos = this.blockPosition();
        if (this.isPassenger()) {
            super.setPos(x, y, z);
        } else {
            super.setPos(Mth.floor(x) + 0.5, Mth.floor(y + 0.5), Mth.floor(z) + 0.5);
        }

        if (this.tickCount != 0) {
            BlockPos blockPos1 = this.blockPosition();
            if (!blockPos1.equals(blockPos)) {
                this.entityData.set(DATA_PEEK_ID, (byte)0);
                this.needsSync = true;
                if (this.level().isClientSide() && !this.isPassenger() && !blockPos1.equals(this.clientOldAttachPosition)) {
                    this.clientOldAttachPosition = blockPos;
                    this.clientSideTeleportInterpolation = 6;
                    this.xOld = this.getX();
                    this.yOld = this.getY();
                    this.zOld = this.getZ();
                }
            }
        }
    }

    protected @Nullable Direction findAttachableSurface(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.canStayAt(pos, direction)) {
                return direction;
            }
        }

        return null;
    }

    boolean canStayAt(BlockPos pos, Direction facing) {
        if (this.isPositionBlocked(pos)) {
            return false;
        } else {
            Direction opposite = facing.getOpposite();
            if (!this.level().loadedAndEntityCanStandOnFace(pos.relative(facing), this, opposite)) {
                return false;
            } else {
                AABB aabb = getProgressAabb(this.getScale(), opposite, 1.0F, pos.getBottomCenter()).deflate(1.0E-6);
                return this.level().noCollision(this, aabb);
            }
        }
    }

    private boolean isPositionBlocked(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.isAir()) {
            return false;
        } else {
            boolean flag = blockState.is(Blocks.MOVING_PISTON) && pos.equals(this.blockPosition());
            return !flag;
        }
    }

    protected boolean teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPos blockPos = this.blockPosition();

            for (int i = 0; i < 5; i++) {
                BlockPos blockPos1 = blockPos.offset(
                    Mth.randomBetweenInclusive(this.random, -8, 8),
                    Mth.randomBetweenInclusive(this.random, -8, 8),
                    Mth.randomBetweenInclusive(this.random, -8, 8)
                );
                if (blockPos1.getY() > this.level().getMinY()
                    && this.level().isEmptyBlock(blockPos1)
                    && this.level().getWorldBorder().isWithinBounds(blockPos1)
                    && this.level().noCollision(this, new AABB(blockPos1).deflate(1.0E-6))) {
                    Direction direction = this.findAttachableSurface(blockPos1);
                    // CraftBukkit start
                    org.bukkit.event.entity.EntityTeleportEvent teleportEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTeleportEvent(this, blockPos1.getX(), blockPos1.getY(), blockPos1.getZ());
                    if (teleportEvent.isCancelled() || teleportEvent.getTo() == null) { // Paper
                        return false;
                    } else {
                        blockPos1 = org.bukkit.craftbukkit.util.CraftLocation.toBlockPosition(teleportEvent.getTo());
                    }
                    // CraftBukkit end
                    if (direction != null) {
                        this.unRide();
                        this.setAttachFace(direction);
                        this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.setPos(blockPos1.getX() + 0.5, blockPos1.getY(), blockPos1.getZ() + 0.5);
                        this.level().gameEvent(GameEvent.TELEPORT, blockPos, GameEvent.Context.of(this));
                        this.entityData.set(DATA_PEEK_ID, (byte)0);
                        this.setTarget(null);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return null;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isClosed()) {
            Entity directEntity = damageSource.getDirectEntity();
            if (directEntity instanceof AbstractArrow) {
                return false;
            }
        }

        if (!super.hurtServer(level, damageSource, amount)) {
            return false;
        } else {
            if (this.getHealth() < this.getMaxHealth() * 0.5 && this.random.nextInt(4) == 0) {
                this.teleportSomewhere();
            } else if (damageSource.is(DamageTypeTags.IS_PROJECTILE)) {
                Entity directEntity = damageSource.getDirectEntity();
                if (directEntity != null && directEntity.getType() == EntityType.SHULKER_BULLET) {
                    this.hitByShulkerBullet();
                }
            }

            return true;
        }
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    private void hitByShulkerBullet() {
        Vec3 vec3 = this.position();
        AABB boundingBox = this.getBoundingBox();
        if (!this.isClosed() && this.teleportSomewhere()) {
            int size = this.level().getEntities(EntityType.SHULKER, boundingBox.inflate(8.0), Entity::isAlive).size();
            float f = (size - 1) / 5.0F;
            if (!(this.level().random.nextFloat() < f)) {
                Shulker shulker = EntityType.SHULKER.create(this.level(), EntitySpawnReason.BREEDING);
                if (shulker != null) {
                    shulker.setVariant(this.getVariant());
                    shulker.snapTo(vec3);
                    // Paper start - Call ShulkerDuplicateEvent
                    if (!new io.papermc.paper.event.entity.ShulkerDuplicateEvent((org.bukkit.entity.Shulker) shulker.getBukkitEntity(), (org.bukkit.entity.Shulker) this.getBukkitEntity()).callEvent()) {
                        return;
                    }
                    // Paper end - Call ShulkerDuplicateEvent
                    this.level().addFreshEntity(shulker, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - the mysteries of life
                }
            }
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return this.isAlive();
    }

    public Direction getAttachFace() {
        return this.entityData.get(DATA_ATTACH_FACE_ID);
    }

    public void setAttachFace(Direction attachFace) {
        this.entityData.set(DATA_ATTACH_FACE_ID, attachFace);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_ATTACH_FACE_ID.equals(key)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

        super.onSyncedDataUpdated(key);
    }

    public int getRawPeekAmount() {
        return this.entityData.get(DATA_PEEK_ID);
    }

    public void setRawPeekAmount(int peekAmount) {
        if (!this.level().isClientSide()) {
            this.getAttribute(Attributes.ARMOR).removeModifier(COVERED_ARMOR_MODIFIER_ID);
            if (peekAmount == 0) {
                this.getAttribute(Attributes.ARMOR).addPermanentModifier(COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_CLOSE);
            } else {
                this.playSound(SoundEvents.SHULKER_OPEN, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_OPEN);
            }
        }

        this.entityData.set(DATA_PEEK_ID, (byte)peekAmount);
    }

    public float getClientPeekAmount(float partialTick) {
        return Mth.lerp(partialTick, this.currentPeekAmountO, this.currentPeekAmount);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public void push(Entity entity) {
    }

    public @Nullable Vec3 getRenderPosition(float partialTick) {
        if (this.clientOldAttachPosition != null && this.clientSideTeleportInterpolation > 0) {
            double d = (this.clientSideTeleportInterpolation - partialTick) / 6.0;
            d *= d;
            d *= this.getScale();
            BlockPos blockPos = this.blockPosition();
            double d1 = (blockPos.getX() - this.clientOldAttachPosition.getX()) * d;
            double d2 = (blockPos.getY() - this.clientOldAttachPosition.getY()) * d;
            double d3 = (blockPos.getZ() - this.clientOldAttachPosition.getZ()) * d;
            return new Vec3(-d1, -d2, -d3);
        } else {
            return null;
        }
    }

    @Override
    protected float sanitizeScale(float scale) {
        return Math.min(scale, 3.0F);
    }

    private void setVariant(Optional<DyeColor> variant) {
        this.entityData.set(DATA_COLOR_ID, variant.<Byte>map(color -> (byte)color.getId()).orElse((byte)16));
    }

    public Optional<DyeColor> getVariant() {
        return Optional.ofNullable(this.getColor());
    }

    public @Nullable DyeColor getColor() {
        byte b = this.entityData.get(DATA_COLOR_ID);
        return b != 16 && b <= 15 ? DyeColor.byId(b) : null;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> component) {
        return component == DataComponents.SHULKER_COLOR ? castComponentValue((DataComponentType<T>)component, this.getColor()) : super.get(component);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentGetter) {
        this.applyImplicitComponentIfPresent(componentGetter, DataComponents.SHULKER_COLOR);
        super.applyImplicitComponents(componentGetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> component, T value) {
        if (component == DataComponents.SHULKER_COLOR) {
            this.setVariant(Optional.of(castComponentValue(DataComponents.SHULKER_COLOR, value)));
            return true;
        } else {
            return super.applyImplicitComponent(component, value);
        }
    }

    class ShulkerAttackGoal extends Goal {
        private int attackTime;

        public ShulkerAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = Shulker.this.getTarget();
            return target != null && target.isAlive() && Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL;
        }

        @Override
        public void start() {
            this.attackTime = 20;
            Shulker.this.setRawPeekAmount(100);
        }

        @Override
        public void stop() {
            Shulker.this.setRawPeekAmount(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL) {
                this.attackTime--;
                LivingEntity target = Shulker.this.getTarget();
                if (target != null) {
                    Shulker.this.getLookControl().setLookAt(target, 180.0F, 180.0F);
                    double d = Shulker.this.distanceToSqr(target);
                    if (d < 400.0) {
                        if (this.attackTime <= 0) {
                            this.attackTime = 20 + Shulker.this.random.nextInt(10) * 20 / 2;
                            Shulker.this.level()
                                .addFreshEntity(new ShulkerBullet(Shulker.this.level(), Shulker.this, target, Shulker.this.getAttachFace().getAxis()));
                            Shulker.this.playSound(
                                SoundEvents.SHULKER_SHOOT, 2.0F, (Shulker.this.random.nextFloat() - Shulker.this.random.nextFloat()) * 0.2F + 1.0F
                            );
                        }
                    } else {
                        Shulker.this.setTarget(null);
                    }

                    super.tick();
                }
            }
        }
    }

    static class ShulkerBodyRotationControl extends BodyRotationControl {
        public ShulkerBodyRotationControl(Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
        }
    }

    static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {
        public ShulkerDefenseAttackGoal(Shulker shulker) {
            super(shulker, LivingEntity.class, 10, true, false, (entity, level) -> entity instanceof Enemy);
        }

        @Override
        public boolean canUse() {
            return this.mob.getTeam() != null && super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double targetDistance) {
            Direction attachFace = ((Shulker)this.mob).getAttachFace();
            if (attachFace.getAxis() == Direction.Axis.X) {
                return this.mob.getBoundingBox().inflate(4.0, targetDistance, targetDistance);
            } else {
                return attachFace.getAxis() == Direction.Axis.Z
                    ? this.mob.getBoundingBox().inflate(targetDistance, targetDistance, 4.0)
                    : this.mob.getBoundingBox().inflate(targetDistance, 4.0, targetDistance);
            }
        }
    }

    class ShulkerLookControl extends LookControl {
        public ShulkerLookControl(final Mob mob) {
            super(mob);
        }

        @Override
        protected void clampHeadRotationToBody() {
        }

        @Override
        protected Optional<Float> getYRotD() {
            Direction opposite = Shulker.this.getAttachFace().getOpposite();
            Vector3f vector3f = opposite.getRotation().transform(new Vector3f(Shulker.FORWARD));
            Vec3i unitVec3i = opposite.getUnitVec3i();
            Vector3f vector3f1 = new Vector3f(unitVec3i.getX(), unitVec3i.getY(), unitVec3i.getZ());
            vector3f1.cross(vector3f);
            double d = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getEyeY();
            double d2 = this.wantedZ - this.mob.getZ();
            Vector3f vector3f2 = new Vector3f((float)d, (float)d1, (float)d2);
            float f = vector3f1.dot(vector3f2);
            float f1 = vector3f.dot(vector3f2);
            return !(Math.abs(f) > 1.0E-5F) && !(Math.abs(f1) > 1.0E-5F) ? Optional.empty() : Optional.of((float)(Mth.atan2(-f, f1) * 180.0F / (float)Math.PI));
        }

        @Override
        protected Optional<Float> getXRotD() {
            return Optional.of(0.0F);
        }
    }

    class ShulkerNearestAttackGoal extends NearestAttackableTargetGoal<Player> {
        public ShulkerNearestAttackGoal(final Shulker shulker) {
            super(shulker, Player.class, true);
        }

        @Override
        public boolean canUse() {
            return Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL && super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double targetDistance) {
            Direction attachFace = ((Shulker)this.mob).getAttachFace();
            if (attachFace.getAxis() == Direction.Axis.X) {
                return this.mob.getBoundingBox().inflate(4.0, targetDistance, targetDistance);
            } else {
                return attachFace.getAxis() == Direction.Axis.Z
                    ? this.mob.getBoundingBox().inflate(targetDistance, targetDistance, 4.0)
                    : this.mob.getBoundingBox().inflate(targetDistance, 4.0, targetDistance);
            }
        }
    }

    class ShulkerPeekGoal extends Goal {
        private int peekTime;

        @Override
        public boolean canUse() {
            return Shulker.this.getTarget() == null
                && Shulker.this.random.nextInt(reducedTickDelay(40)) == 0
                && Shulker.this.canStayAt(Shulker.this.blockPosition(), Shulker.this.getAttachFace());
        }

        @Override
        public boolean canContinueToUse() {
            return Shulker.this.getTarget() == null && this.peekTime > 0;
        }

        @Override
        public void start() {
            this.peekTime = this.adjustedTickDelay(20 * (1 + Shulker.this.random.nextInt(3)));
            Shulker.this.setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (Shulker.this.getTarget() == null) {
                Shulker.this.setRawPeekAmount(0);
            }
        }

        @Override
        public void tick() {
            this.peekTime--;
        }
    }
}
