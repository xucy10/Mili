package net.minecraft.world.entity;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ExperienceOrb extends Entity {
    protected static final EntityDataAccessor<Integer> DATA_VALUE = SynchedEntityData.defineId(ExperienceOrb.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 6000;
    private static final int ENTITY_SCAN_PERIOD = 20;
    private static final int MAX_FOLLOW_DIST = 8;
    private static final int ORB_GROUPS_PER_AREA = 40;
    private static final double ORB_MERGE_DISTANCE = 0.5;
    private static final short DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_VALUE = 0;
    private static final int DEFAULT_COUNT = 1;
    private int age = 0;
    private int health = 5;
    public int count = 1;
    private @Nullable Player followingPlayer;
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    // Paper start
    public java.util.@Nullable UUID sourceEntityId;
    public java.util.@Nullable UUID triggerEntityId;
    public org.bukkit.entity.ExperienceOrb.SpawnReason spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;

    private void loadPaperNBT(ValueInput input) {
        input.read("Paper.ExpData", net.minecraft.nbt.CompoundTag.CODEC).ifPresent(expData -> {
            this.sourceEntityId = expData.read("source", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            this.triggerEntityId = expData.read("trigger", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            expData.getString("reason").ifPresent(reason -> {
                try {
                    this.spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.valueOf(reason);
                } catch (Exception e) {
                    this.level().getCraftServer().getLogger().warning("Invalid spawnReason set for experience orb: " + e.getMessage() + " - " + reason);
                }
            });
        });
    }
    private void savePaperNBT(ValueOutput output) {
        net.minecraft.nbt.CompoundTag expData = new net.minecraft.nbt.CompoundTag();
        expData.storeNullable("source", net.minecraft.core.UUIDUtil.CODEC, this.sourceEntityId);
        expData.storeNullable("trigger", net.minecraft.core.UUIDUtil.CODEC, this.triggerEntityId);
        if (this.spawnReason != org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN) {
            expData.putString("reason", this.spawnReason.name());
        }
        output.store("Paper.ExpData", net.minecraft.nbt.CompoundTag.CODEC, expData);
    }
    // Paper end
    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - overload ctor
    public ExperienceOrb(Level level, double x, double y, double z, int value) {
    // Paper start - add reasons for orbs
        this(level, x, y, z, value, null, null, null);
    }
    public ExperienceOrb(Level level, double x, double y, double z, int value, org.bukkit.entity.ExperienceOrb.@Nullable SpawnReason reason, @Nullable Entity triggerId, @Nullable Entity sourceId) {
        this(level, new Vec3(x, y, z), Vec3.ZERO, value, reason, triggerId, sourceId);
    // Paper end - add reasons for orbs
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - overload ctor
    public ExperienceOrb(Level level, Vec3 pos, Vec3 direction, int value) {
    // Paper start - add reasons for orbs
        this(level, pos, direction, value, null, null, null);
    }
    public ExperienceOrb(Level level, Vec3 pos, Vec3 direction, int value, org.bukkit.entity.ExperienceOrb.@Nullable SpawnReason reason, @Nullable Entity triggerId, @Nullable Entity sourceId) {
    // Paper end - add reasons for orbs
        this(EntityType.EXPERIENCE_ORB, level);
        // Paper start - add reasons for orbs
        this.sourceEntityId = sourceId != null ? sourceId.getUUID() : null;
        this.triggerEntityId = triggerId != null ? triggerId.getUUID() : null;
        this.spawnReason = reason != null ? reason : org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;
        // Paper end - add reasons for orbs
        this.setPos(pos);
        if (!level.isClientSide()) {
            this.setYRot(this.random.nextFloat() * 360.0F);
            Vec3 vec3 = new Vec3(
                (this.random.nextDouble() * 0.2 - 0.1) * 2.0, this.random.nextDouble() * 0.2 * 2.0, (this.random.nextDouble() * 0.2 - 0.1) * 2.0
            );
            if (direction.lengthSqr() > 0.0 && direction.dot(vec3) < 0.0) {
                vec3 = vec3.scale(-1.0);
            }

            double size = this.getBoundingBox().getSize();
            this.setPos(pos.add(direction.normalize().scale(size * 0.5)));
            this.setDeltaMovement(vec3);
            if (!level.noCollision(this.getBoundingBox())) {
                this.unstuckIfPossible(size);
            }
        }

        this.setValue(value);
    }

    public ExperienceOrb(EntityType<? extends ExperienceOrb> type, Level level) {
        super(type, level);
    }

    protected void unstuckIfPossible(double size) {
        Vec3 vec3 = this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
        VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, size, size, size));
        this.level()
            .findFreePosition(this, voxelShape, vec3, this.getBbWidth(), this.getBbHeight(), this.getBbWidth())
            .ifPresent(vec31 -> this.setPos(vec31.add(0.0, -this.getBbHeight() / 2.0, 0.0)));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VALUE, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        this.interpolation.interpolate();
        if (this.firstTick && this.level().isClientSide()) {
            this.firstTick = false;
        } else {
            super.tick();
            boolean flag = !this.level().noCollision(this.getBoundingBox());
            if (this.isEyeInFluid(FluidTags.WATER)) {
                this.setUnderwaterMovement();
            } else if (!flag) {
                this.applyGravity();
            }

            if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
                this.setDeltaMovement(
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F, 0.2F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                );
            }

            if (this.tickCount % 20 == 1) {
                this.scanForMerges();
            }

            this.followNearbyPlayer();
            if (this.followingPlayer == null && !this.level().isClientSide() && flag) {
                boolean flag1 = !this.level().noCollision(this.getBoundingBox().move(this.getDeltaMovement()));
                if (flag1) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
                    this.needsSync = true;
                }
            }

            double d = this.getDeltaMovement().y;
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            float f = 0.98F;
            if (this.onGround()) {
                f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(f));
            if (this.verticalCollisionBelow && d < -this.getGravity()) {
                this.setDeltaMovement(new Vec3(this.getDeltaMovement().x, -d * 0.4, this.getDeltaMovement().z));
            }

            this.age++;
            if (this.age >= 6000) {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }
    }

    private void followNearbyPlayer() {
        Player prevTarget = this.followingPlayer; // CraftBukkit - store old target
        if (this.followingPlayer == null || this.followingPlayer.isSpectator() || this.followingPlayer.distanceToSqr(this) > 64.0) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 8.0);
            if (nearestPlayer != null && !nearestPlayer.isSpectator() && !nearestPlayer.isDeadOrDying()) {
                this.followingPlayer = nearestPlayer;
            } else {
                this.followingPlayer = null;
            }
        }

        // CraftBukkit start
        boolean cancelled = false;
        if (this.followingPlayer != prevTarget) {
            org.bukkit.event.entity.EntityTargetLivingEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(
                this, this.followingPlayer, (this.followingPlayer != null) ? org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER : org.bukkit.event.entity.EntityTargetEvent.TargetReason.FORGOT_TARGET
            );
            LivingEntity target = (event.getTarget() == null) ? null : ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
            cancelled = event.isCancelled();

            if (cancelled) {
                this.followingPlayer = prevTarget;
            } else {
                this.followingPlayer = (target instanceof Player) ? (Player) target : null;
            }
        }

        if (this.followingPlayer != null && !cancelled) {
            // CraftBukkit end
            Vec3 vec3 = new Vec3(
                this.followingPlayer.getX() - this.getX(),
                this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
                this.followingPlayer.getZ() - this.getZ()
            );
            double d = vec3.lengthSqr();
            double d1 = 1.0 - Math.sqrt(d) / 8.0;
            this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(d1 * d1 * 0.1)));
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForMerges() {
        if (this.level() instanceof ServerLevel) {
            for (ExperienceOrb experienceOrb : this.level()
                .getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.5), this::canMerge)) {
                this.merge(experienceOrb);
            }
        }
    }

    public static void award(ServerLevel level, Vec3 pos, int amount) {
        awardWithDirection(level, pos, Vec3.ZERO, amount);
    }

    public static void awardWithDirection(ServerLevel level, Vec3 pos, Vec3 direction, int amount) {
    // Paper start - add reason to orbs
        awardWithDirection(level, pos, direction, amount, null, null, null);
    }
    public static void awardWithDirection(ServerLevel level, Vec3 pos, Vec3 direction, int amount, org.bukkit.entity.ExperienceOrb.@Nullable SpawnReason reason, @Nullable Entity triggerId, @Nullable Entity sourceId) {
    // Paper end - add reason to orbs
        while (amount > 0) {
            int experienceValue = getExperienceValue(amount);
            amount -= experienceValue;
            if (!tryMergeToExisting(level, pos, experienceValue)) {
                level.addFreshEntity(new ExperienceOrb(level, pos, direction, experienceValue, reason, triggerId, sourceId)); // Paper - add reason to orbs
            }
        }
    }

    private static boolean tryMergeToExisting(ServerLevel level, Vec3 pos, int amount) {
        // Paper - TODO some other event for this kind of merge
        AABB aabb = AABB.ofSize(pos, 1.0, 1.0, 1.0);
        int randomInt = level.getRandom().nextInt(io.papermc.paper.configuration.GlobalConfiguration.get().misc.xpOrbGroupsPerArea.or(ORB_GROUPS_PER_AREA)); // Paper - Configure how many orb groups per area
        List<ExperienceOrb> entities = level.getEntities(
            EntityTypeTest.forClass(ExperienceOrb.class), aabb, experienceOrb1 -> canMerge(experienceOrb1, randomInt, amount)
        );
        if (!entities.isEmpty()) {
            ExperienceOrb experienceOrb = entities.get(0);
            experienceOrb.count++;
            experienceOrb.age = 0;
            return true;
        } else {
            return false;
        }
    }

    private boolean canMerge(ExperienceOrb orb) {
        return orb != this && canMerge(orb, this.getId(), this.getValue());
    }

    private static boolean canMerge(ExperienceOrb orb, int amount, int other) {
        return !orb.isRemoved() && (orb.getId() - amount) % io.papermc.paper.configuration.GlobalConfiguration.get().misc.xpOrbGroupsPerArea.or(ORB_GROUPS_PER_AREA) == 0 && orb.getValue() == other; // Paper - Configure how many orbs will merge together
    }

    private void merge(ExperienceOrb orb) {
        // Paper start - call orb merge event
        if (!new com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent((org.bukkit.entity.ExperienceOrb) this.getBukkitEntity(), (org.bukkit.entity.ExperienceOrb) orb.getBukkitEntity()).callEvent()) {
            return;
        }
        // Paper end - call orb merge event
        this.count = this.count + orb.count;
        this.age = Math.min(this.age, orb.age);
        orb.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause
    }

    private void setUnderwaterMovement() {
        Vec3 deltaMovement = this.getDeltaMovement();
        this.setDeltaMovement(deltaMovement.x * 0.99F, Math.min(deltaMovement.y + 5.0E-4F, 0.06F), deltaMovement.z * 0.99F);
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public final boolean hurtClient(DamageSource damageSource) {
        return !this.isInvulnerableToBase(damageSource);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.isInvulnerableToBase(damageSource)) {
            return false;
        } else {
            this.markHurt();
            this.health = (int)(this.health - amount);
            if (this.health <= 0) {
                this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("Health", (short)this.health);
        output.putShort("Age", (short)this.age);
        output.putInt("Value", this.getValue()); // Paper - save as Integer
        output.putInt("Count", this.count);
        this.savePaperNBT(output); // Paper
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.health = input.getShortOr("Health", (short)5);
        this.age = input.getShortOr("Age", (short)0);
        this.setValue(input.getIntOr("Value", 0)); // Paper - load as Integer
        this.count = input.read("Count", ExtraCodecs.POSITIVE_INT).orElse(1);
        this.loadPaperNBT(input); // Paper
    }

    @Override
    public void playerTouch(Player entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            if (entity.takeXpDelay == 0 && new com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent(serverPlayer.getBukkitEntity(), (org.bukkit.entity.ExperienceOrb) this.getBukkitEntity()).callEvent()) { // Paper - PlayerPickupExperienceEvent
                entity.takeXpDelay = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerXpCooldownEvent(entity, 2, org.bukkit.event.player.PlayerExpCooldownChangeEvent.ChangeReason.PICKUP_ORB).getNewCooldown(); // CraftBukkit - entity.takeXpDelay = 2;
                entity.take(this, 1);
                int i = this.repairPlayerItems(serverPlayer, this.getValue());
                if (i > 0) {
                    entity.giveExperiencePoints(org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerExpChangeEvent(entity, this, i).getAmount()); // CraftBukkit - i -> event.getAmount() // Paper - supply experience orb
                }

                this.count--;
                if (this.count == 0) {
                    this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                }
            }
        }
    }

    private int repairPlayerItems(ServerPlayer player, int value) {
        Optional<EnchantedItemInUse> randomItemWith = EnchantmentHelper.getRandomItemWith(
            EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged
        );
        if (randomItemWith.isPresent()) {
            ItemStack itemStack = randomItemWith.get().itemStack();
            int i = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), itemStack, value);
            int min = Math.min(i, itemStack.getDamageValue());
            // CraftBukkit start
            // Paper start - mending event
            final int consumedExperience = min > 0 ? min * value / i : 0;
            org.bukkit.event.player.PlayerItemMendEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemMendEvent(player, this, itemStack, randomItemWith.get().inSlot(), min, consumedExperience);
            // Paper end - mending event
            min = event.getRepairAmount();
            if (event.isCancelled()) {
                return value;
            }
            // CraftBukkit end
            itemStack.setDamageValue(itemStack.getDamageValue() - min);
            if (min > 0) {
                int i1 = value - min * value / i; // Paper - diff on change - expand PlayerMendEvents
                if (i1 > 0) {
                    return this.repairPlayerItems(player, i1);
                }
            }

            return 0;
        } else {
            return value;
        }
    }

    public int getValue() {
        return this.entityData.get(DATA_VALUE);
    }

    public void setValue(int value) {
        this.entityData.set(DATA_VALUE, value);
    }

    public int getIcon() {
        int value = this.getValue();
        if (value >= 2477) {
            return 10;
        } else if (value >= 1237) {
            return 9;
        } else if (value >= 617) {
            return 8;
        } else if (value >= 307) {
            return 7;
        } else if (value >= 149) {
            return 6;
        } else if (value >= 73) {
            return 5;
        } else if (value >= 37) {
            return 4;
        } else if (value >= 17) {
            return 3;
        } else if (value >= 7) {
            return 2;
        } else {
            return value >= 3 ? 1 : 0;
        }
    }

    public static int getExperienceValue(int expValue) {
        // CraftBukkit start
        if (expValue > 162670129) return expValue - 100000;
        if (expValue > 81335063) return 81335063;
        if (expValue > 40667527) return 40667527;
        if (expValue > 20333759) return 20333759;
        if (expValue > 10166857) return 10166857;
        if (expValue > 5083423) return 5083423;
        if (expValue > 2541701) return 2541701;
        if (expValue > 1270849) return 1270849;
        if (expValue > 635413) return 635413;
        if (expValue > 317701) return 317701;
        if (expValue > 158849) return 158849;
        if (expValue > 79423) return 79423;
        if (expValue > 39709) return 39709;
        if (expValue > 19853) return 19853;
        if (expValue > 9923) return 9923;
        if (expValue > 4957) return 4957;
        // CraftBukkit end
        if (expValue >= 2477) {
            return 2477;
        } else if (expValue >= 1237) {
            return 1237;
        } else if (expValue >= 617) {
            return 617;
        } else if (expValue >= 307) {
            return 307;
        } else if (expValue >= 149) {
            return 149;
        } else if (expValue >= 73) {
            return 73;
        } else if (expValue >= 37) {
            return 37;
        } else if (expValue >= 17) {
            return 17;
        } else if (expValue >= 7) {
            return 7;
        } else {
            return expValue >= 3 ? 3 : 1;
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }
}
