package net.minecraft.world.entity.raid;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Raider extends PatrollingMonster {
    protected static final EntityDataAccessor<Boolean> IS_CELEBRATING = SynchedEntityData.defineId(Raider.class, EntityDataSerializers.BOOLEAN);
    static final Predicate<ItemEntity> ALLOWED_ITEMS = item -> !item.hasPickUpDelay()
        && item.isAlive()
        && ItemStack.matches(item.getItem(), Raid.getOminousBannerInstance(item.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
    private static final int DEFAULT_WAVE = 0;
    private static final boolean DEFAULT_CAN_JOIN_RAID = false;
    protected @Nullable Raid raid;
    private int wave = 0;
    private boolean canJoinRaid = false;
    private int ticksOutsideRaid;

    protected Raider(EntityType<? extends Raider> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Raider.ObtainRaidLeaderBannerGoal<>(this));
        this.goalSelector.addGoal(3, new PathfindToRaidGoal<>(this));
        this.goalSelector.addGoal(4, new Raider.RaiderMoveThroughVillageGoal(this, 1.05F, 1));
        this.goalSelector.addGoal(5, new Raider.RaiderCelebration(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_CELEBRATING, false);
    }

    public abstract void applyRaidBuffs(ServerLevel level, int wave, boolean flag);

    public boolean canJoinRaid() {
        return this.canJoinRaid;
    }

    public void setCanJoinRaid(boolean canJoinRaid) {
        this.canJoinRaid = canJoinRaid;
    }

    @Override
    public void aiStep() {
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            Raid currentRaid = this.getCurrentRaid();
            if (this.canJoinRaid()) {
                if (currentRaid == null) {
                    if (this.level().getRedstoneGameTime() % 20L == 0L) { // Folia - region threading
                        Raid raidAt = serverLevel.getRaidAt(this.blockPosition());
                        if (raidAt != null && Raids.canJoinRaid(this)) {
                            raidAt.joinRaid(serverLevel, raidAt.getGroupsSpawned(), this, null, true);
                        }
                    }
                } else {
                    LivingEntity target = this.getTarget();
                    if (target != null && (target.getType() == EntityType.PLAYER || target.getType() == EntityType.IRON_GOLEM)) {
                        this.noActionTime = 0;
                    }
                }
            }
        }

        super.aiStep();
    }

    @Override
    protected void updateNoActionTime() {
        this.noActionTime += 2;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel serverLevel) {
            Entity entity = damageSource.getEntity();
            Raid currentRaid = this.getCurrentRaid();
            if (currentRaid != null) {
                if (this.isPatrolLeader()) {
                    currentRaid.removeLeader(this.getWave());
                }

                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    currentRaid.addHeroOfTheVillage(entity);
                }

                currentRaid.removeFromRaid(serverLevel, this, false);
            }

            // Leaves start - Revert raid changes
            if (this.level() instanceof ServerLevel) {
                if (fun.bm.mili.config.modules.function.OldFeatureConfig.oldRaidBehavior && !this.hasRaid()) {
                    ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
                    net.minecraft.world.entity.player.Player entityhuman = null;
                    if (entity instanceof net.minecraft.world.entity.player.Player player) {
                        entityhuman = player;
                    } else if (entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf) {
                        LivingEntity entityliving = wolf.getOwner();
                        if (wolf.isTame() && entityliving instanceof net.minecraft.world.entity.player.Player player) {
                            entityhuman = player;
                        }
                    }

                    if (entityhuman != null && !itemstack.isEmpty() && this.isCaptain()) {
                        net.minecraft.world.effect.MobEffectInstance mobeffect = entityhuman.getEffect(net.minecraft.world.effect.MobEffects.BAD_OMEN);
                        int i = 1;

                        if (mobeffect != null) {
                            i += mobeffect.getAmplifier();
                            entityhuman.removeEffectNoUpdate(net.minecraft.world.effect.MobEffects.BAD_OMEN);
                        } else {
                            --i;
                        }

                        i = net.minecraft.util.Mth.clamp(i, 0, 4);
                        net.minecraft.world.effect.MobEffectInstance mobeffect1 = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BAD_OMEN, 120000, i, false, false, true);

                        if (serverLevel.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RAIDS)) {
                            entityhuman.addEffect(mobeffect1, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PATROL_CAPTAIN); // CraftBukkit
                        }
                        this.setPatrolLeader(false);
                    }
                }
            }
            // Leaves end - Revert raid changes
        }

        super.die(damageSource);
    }

    @Override
    public boolean canJoinPatrol() {
        return !this.hasActiveRaid();
    }

    public void setCurrentRaid(@Nullable Raid raid) {
        this.raid = raid;
    }

    public @Nullable Raid getCurrentRaid() {
        return this.raid;
    }

    public boolean isCaptain() {
        ItemStack itemBySlot = this.getItemBySlot(EquipmentSlot.HEAD);
        boolean flag = !itemBySlot.isEmpty()
            && ItemStack.matches(itemBySlot, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        boolean isPatrolLeader = this.isPatrolLeader();
        return flag && isPatrolLeader;
    }

    public boolean hasRaid() {
        return this.level() instanceof ServerLevel serverLevel && (this.getCurrentRaid() != null || serverLevel.getRaidAt(this.blockPosition()) != null);
    }

    public boolean hasActiveRaid() {
        return this.getCurrentRaid() != null && this.getCurrentRaid().isActive();
    }

    public void setWave(int wave) {
        this.wave = wave;
    }

    public int getWave() {
        return this.wave;
    }

    public boolean isCelebrating() {
        return this.entityData.get(IS_CELEBRATING);
    }

    public void setCelebrating(boolean celebrating) {
        this.entityData.set(IS_CELEBRATING, celebrating);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Wave", this.wave);
        output.putBoolean("CanJoinRaid", this.canJoinRaid);
        if (this.raid != null && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getRaids().getId(this.raid).ifPresent(i -> output.putInt("RaidId", i));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.wave = input.getIntOr("Wave", 0);
        this.canJoinRaid = input.getBooleanOr("CanJoinRaid", false);
        if (this.level() instanceof ServerLevel serverLevel) {
            input.getInt("RaidId").ifPresent(integer -> {
                this.raid = serverLevel.getRaids().get(integer);
                if (this.raid != null) {
                    this.raid.addWaveMob(serverLevel, this.wave, this, false);
                    if (this.isPatrolLeader()) {
                        this.raid.setLeader(this.wave, this);
                    }
                }
            });
        }
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack item = entity.getItem();
        boolean flag = this.hasActiveRaid() && this.getCurrentRaid().getLeader(this.getWave()) != null;
        if (this.hasActiveRaid()
            && !flag
            && ItemStack.matches(item, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))) {
            // Paper start - EntityPickupItemEvent fixes
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entity, 0).isCancelled()) {
                return;
            }
            // Paper end - EntityPickupItemEvent fixes
            EquipmentSlot equipmentSlot = EquipmentSlot.HEAD;
            ItemStack itemBySlot = this.getItemBySlot(equipmentSlot);
            double d = this.getDropChances().byEquipment(equipmentSlot);
            if (!itemBySlot.isEmpty() && Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d) {
                this.forceDrops = true; // Paper - Add missing forceDrop toggles
                this.spawnAtLocation(level, itemBySlot);
                this.forceDrops = false; // Paper - Add missing forceDrop toggles
            }

            this.onItemPickup(entity);
            this.setItemSlot(equipmentSlot, item);
            this.take(entity, item.getCount());
            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            this.getCurrentRaid().setLeader(this.getWave(), this);
            this.setPatrolLeader(true);
        } else {
            super.pickUpItem(level, entity);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.getCurrentRaid() == null && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCurrentRaid() != null;
    }

    public int getTicksOutsideRaid() {
        return this.ticksOutsideRaid;
    }

    public void setTicksOutsideRaid(int ticksOutsideRaid) {
        this.ticksOutsideRaid = ticksOutsideRaid;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        if (this.hasActiveRaid()) {
            this.getCurrentRaid().updateBossbar();
        }

        return super.hurtServer(level, damageSource, amount);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        this.setCanJoinRaid(this.getType() != EntityType.WITCH || spawnReason != EntitySpawnReason.NATURAL);
        return super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
    }

    public abstract SoundEvent getCelebrateSound();

    public static class HoldGroundAttackGoal extends Goal {
        private final Raider mob;
        private final float hostileRadiusSqr;
        public final TargetingConditions shoutTargeting = TargetingConditions.forNonCombat().range(8.0).ignoreLineOfSight().ignoreInvisibilityTesting();

        public HoldGroundAttackGoal(AbstractIllager mob, float radius) {
            this.mob = mob;
            this.hostileRadiusSqr = radius * radius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity lastHurtByMob = this.mob.getLastHurtByMob();
            return this.mob.getCurrentRaid() == null
                && this.mob.isPatrolling()
                && this.mob.getTarget() != null
                && !this.mob.isAggressive()
                && (lastHurtByMob == null || lastHurtByMob.getType() != EntityType.PLAYER);
        }

        @Override
        public void start() {
            super.start();
            this.mob.getNavigation().stop();

            for (Raider raider : getServerLevel(this.mob)
                .getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0, 8.0, 8.0))) {
                raider.setTarget(this.mob.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER); // CraftBukkit
            }
        }

        @Override
        public void stop() {
            super.stop();
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                for (Raider raider : getServerLevel(this.mob)
                    .getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0, 8.0, 8.0))) {
                    raider.setTarget(target, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER); // CraftBukkit
                    raider.setAggressive(true);
                }

                this.mob.setAggressive(true);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                if (this.mob.distanceToSqr(target) > this.hostileRadiusSqr) {
                    this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (this.mob.random.nextInt(50) == 0) {
                        this.mob.playAmbientSound();
                    }
                } else {
                    this.mob.setAggressive(true);
                }

                super.tick();
            }
        }
    }

    public class ObtainRaidLeaderBannerGoal<T extends Raider> extends Goal {
        private final T mob;
        private Int2LongOpenHashMap unreachableBannerCache = new Int2LongOpenHashMap();
        private @Nullable Path pathToBanner;
        private @Nullable ItemEntity pursuedBannerItemEntity;

        public ObtainRaidLeaderBannerGoal(final T mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cannotPickUpBanner()) {
                return false;
            } else {
                Int2LongOpenHashMap map = new Int2LongOpenHashMap();
                double attributeValue = Raider.this.getAttributeValue(Attributes.FOLLOW_RANGE);

                for (ItemEntity itemEntity : this.mob
                    .level()
                    .getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(attributeValue, 8.0, attributeValue), Raider.ALLOWED_ITEMS)) {
                    long orDefault = this.unreachableBannerCache.getOrDefault(itemEntity.getId(), Long.MIN_VALUE);
                    if (Raider.this.level().getGameTime() < orDefault) {
                        map.put(itemEntity.getId(), orDefault);
                    } else {
                        Path path = this.mob.getNavigation().createPath(itemEntity, 1);
                        if (path != null && path.canReach()) {
                            this.pathToBanner = path;
                            this.pursuedBannerItemEntity = itemEntity;
                            return true;
                        }

                        map.put(itemEntity.getId(), Raider.this.level().getGameTime() + 600L);
                    }
                }

                this.unreachableBannerCache = map;
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.pursuedBannerItemEntity != null
                && this.pathToBanner != null
                && !this.pursuedBannerItemEntity.isRemoved()
                && !this.pathToBanner.isDone()
                && !this.cannotPickUpBanner();
        }

        private boolean cannotPickUpBanner() {
            if (!getServerLevel(this.mob).getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)) return true; // Paper - respect game and entity rules for picking up items
            if (!this.mob.hasActiveRaid()) {
                return true;
            } else if (this.mob.getCurrentRaid().isOver()) {
                return true;
            } else if (!this.mob.canBeLeader()) {
                return true;
            } else if (ItemStack.matches(
                this.mob.getItemBySlot(EquipmentSlot.HEAD), Raid.getOminousBannerInstance(this.mob.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))
            )) {
                return true;
            } else {
                Raider leader = Raider.this.raid.getLeader(this.mob.getWave());
                return leader != null && leader.isAlive();
            }
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.pathToBanner, 1.15F);
        }

        @Override
        public void stop() {
            this.pathToBanner = null;
            this.pursuedBannerItemEntity = null;
        }

        @Override
        public void tick() {
            if (this.pursuedBannerItemEntity != null && this.pursuedBannerItemEntity.closerThan(this.mob, 1.414)) {
                this.mob.pickUpItem(getServerLevel(Raider.this.level()), this.pursuedBannerItemEntity);
            }
        }
    }

    public class RaiderCelebration extends Goal {
        private final Raider mob;

        RaiderCelebration(final Raider mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Raid currentRaid = this.mob.getCurrentRaid();
            return this.mob.isAlive() && this.mob.getTarget() == null && currentRaid != null && currentRaid.isLoss();
        }

        @Override
        public void start() {
            this.mob.setCelebrating(true);
            super.start();
        }

        @Override
        public void stop() {
            this.mob.setCelebrating(false);
            super.stop();
        }

        @Override
        public void tick() {
            if (!this.mob.isSilent() && this.mob.random.nextInt(this.adjustedTickDelay(100)) == 0) {
                Raider.this.makeSound(Raider.this.getCelebrateSound());
            }

            if (!this.mob.isPassenger() && this.mob.random.nextInt(this.adjustedTickDelay(50)) == 0) {
                this.mob.getJumpControl().jump();
            }

            super.tick();
        }
    }

    static class RaiderMoveThroughVillageGoal extends Goal {
        private final Raider raider;
        private final double speedModifier;
        private BlockPos poiPos;
        private final List<BlockPos> visited = Lists.newArrayList();
        private final int distanceToPoi;
        private boolean stuck;

        public RaiderMoveThroughVillageGoal(Raider raider, double speedModifier, int distanceToPoi) {
            this.raider = raider;
            this.speedModifier = speedModifier;
            this.distanceToPoi = distanceToPoi;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.updateVisited();
            return this.isValidRaid() && this.hasSuitablePoi() && this.raider.getTarget() == null;
        }

        private boolean isValidRaid() {
            return this.raider.hasActiveRaid() && !this.raider.getCurrentRaid().isOver();
        }

        private boolean hasSuitablePoi() {
            ServerLevel serverLevel = (ServerLevel)this.raider.level();
            BlockPos blockPos = this.raider.blockPosition();
            Optional<BlockPos> random = serverLevel.getPoiManager()
                .getRandom(poi -> poi.is(PoiTypes.HOME), this::hasNotVisited, PoiManager.Occupancy.ANY, blockPos, 48, this.raider.random);
            if (random.isEmpty()) {
                return false;
            } else {
                this.poiPos = random.get().immutable();
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.raider.getNavigation().isDone()
                && this.raider.getTarget() == null
                && !this.poiPos.closerToCenterThan(this.raider.position(), this.raider.getBbWidth() + this.distanceToPoi)
                && !this.stuck;
        }

        @Override
        public void stop() {
            if (this.poiPos.closerToCenterThan(this.raider.position(), this.distanceToPoi)) {
                this.visited.add(this.poiPos);
            }
        }

        @Override
        public void start() {
            super.start();
            this.raider.setNoActionTime(0);
            this.raider.getNavigation().moveTo(this.poiPos.getX(), this.poiPos.getY(), this.poiPos.getZ(), this.speedModifier);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.raider.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atBottomCenterOf(this.poiPos);
                Vec3 posTowards = DefaultRandomPos.getPosTowards(this.raider, 16, 7, vec3, (float) (Math.PI / 10));
                if (posTowards == null) {
                    posTowards = DefaultRandomPos.getPosTowards(this.raider, 8, 7, vec3, (float) (Math.PI / 2));
                }

                if (posTowards == null) {
                    this.stuck = true;
                    return;
                }

                this.raider.getNavigation().moveTo(posTowards.x, posTowards.y, posTowards.z, this.speedModifier);
            }
        }

        private boolean hasNotVisited(BlockPos pos) {
            for (BlockPos blockPos : this.visited) {
                if (Objects.equals(pos, blockPos)) {
                    return false;
                }
            }

            return true;
        }

        private void updateVisited() {
            if (this.visited.size() > 2) {
                this.visited.remove(0);
            }
        }
    }
}
