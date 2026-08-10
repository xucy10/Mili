package net.minecraft.world.entity.boss.enderdragon;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EnderDragon extends Mob implements Enemy {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.INT);
    private static final TargetingConditions CRYSTAL_DESTROY_TARGETING = TargetingConditions.forCombat().range(64.0);
    private static final int GROWL_INTERVAL_MIN = 200;
    private static final int GROWL_INTERVAL_MAX = 400;
    private static final float SITTING_ALLOWED_DAMAGE_PERCENTAGE = 0.25F;
    private static final String DRAGON_DEATH_TIME_KEY = "DragonDeathTime";
    private static final String DRAGON_PHASE_KEY = "DragonPhase";
    private static final int DEFAULT_DEATH_TIME = 0;
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    public final EnderDragonPart[] subEntities;
    public final EnderDragonPart head;
    private final EnderDragonPart neck;
    private final EnderDragonPart body;
    private final EnderDragonPart tail1;
    private final EnderDragonPart tail2;
    private final EnderDragonPart tail3;
    private final EnderDragonPart wing1;
    private final EnderDragonPart wing2;
    public float oFlapTime;
    public float flapTime;
    public boolean inWall;
    public int dragonDeathTime = 0;
    public float yRotA;
    public @Nullable EndCrystal nearestCrystal;
    private @Nullable EndDragonFight dragonFight;
    private BlockPos fightOrigin = BlockPos.ZERO;
    private final EnderDragonPhaseManager phaseManager;
    private int growlTime = 100;
    private float sittingDamageReceived;
    private final Node[] nodes = new Node[24];
    private final int[] nodeAdjacency = new int[24];
    private final BinaryHeap openSet = new BinaryHeap();
    // Paper start
    private final net.minecraft.world.level.Explosion explosionSource; // Paper - reusable source for CraftTNTPrimed.getSource()
    @Nullable private BlockPos podium;
    // Paper end

    public EnderDragon(EntityType<? extends EnderDragon> type, Level level) {
        super(EntityType.ENDER_DRAGON, level);
        this.head = new EnderDragonPart(this, "head", 1.0F, 1.0F);
        this.neck = new EnderDragonPart(this, "neck", 3.0F, 3.0F);
        this.body = new EnderDragonPart(this, "body", 5.0F, 3.0F);
        this.tail1 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail2 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.tail3 = new EnderDragonPart(this, "tail", 2.0F, 2.0F);
        this.wing1 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.wing2 = new EnderDragonPart(this, "wing", 4.0F, 2.0F);
        this.subEntities = new EnderDragonPart[]{this.head, this.neck, this.body, this.tail1, this.tail2, this.tail3, this.wing1, this.wing2};
        this.setHealth(this.getMaxHealth());
        this.noPhysics = true;
        this.phaseManager = new EnderDragonPhaseManager(this);
        this.explosionSource = new net.minecraft.world.level.ServerExplosion(level.getMinecraftWorld(), this, null, null, new Vec3(Double.NaN, Double.NaN, Double.NaN), Float.NaN, true, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY); // Paper
    }

    public void setDragonFight(EndDragonFight dragonFight) {
        this.dragonFight = dragonFight;
    }

    public void setFightOrigin(BlockPos fightOrigin) {
        this.fightOrigin = fightOrigin;
    }

    public BlockPos getFightOrigin() {
        return this.fightOrigin;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200.0).add(Attributes.CAMERA_DISTANCE, 16.0);
    }

    // Paper start - Allow changing the EnderDragon podium
    public BlockPos getPodium() {
        if (this.podium == null) {
            return EndPodiumFeature.getLocation(this.getFightOrigin());
        }
        return this.podium;
    }

    public void setPodium(@Nullable BlockPos blockPos) {
        this.podium = blockPos;
    }
    // Paper end - Allow changing the EnderDragon podium

    @Override
    public boolean isFlapping() {
        float cos = Mth.cos(this.flapTime * (float) (Math.PI * 2));
        float cos1 = Mth.cos(this.oFlapTime * (float) (Math.PI * 2));
        return cos1 <= -0.3F && cos >= -0.3F;
    }

    @Override
    public void onFlap() {
        if (this.level().isClientSide() && !this.isSilent()) {
            this.level()
                .playLocalSound(
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    SoundEvents.ENDER_DRAGON_FLAP,
                    this.getSoundSource(),
                    5.0F,
                    0.8F + this.random.nextFloat() * 0.3F,
                    false
                );
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, EnderDragonPhase.HOVERING.getId());
    }

    @Override
    public void aiStep() {
        this.processFlappingMovement();
        if (this.level().isClientSide()) {
            this.setHealth(this.getHealth());
            if (!this.isSilent() && !this.phaseManager.getCurrentPhase().isSitting() && --this.growlTime < 0) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.ENDER_DRAGON_GROWL,
                        this.getSoundSource(),
                        2.5F,
                        0.8F + this.random.nextFloat() * 0.3F,
                        false
                    );
                this.growlTime = 200 + this.random.nextInt(200);
            }
        }

        if (this.dragonFight == null && this.level() instanceof ServerLevel serverLevel) {
            EndDragonFight dragonFight = serverLevel.getDragonFight();
            if (dragonFight != null && this.getUUID().equals(dragonFight.getDragonUUID())) {
                this.dragonFight = dragonFight;
            }
        }

        this.oFlapTime = this.flapTime;
        if (this.isDeadOrDying()) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;
            this.level().addParticle(ParticleTypes.EXPLOSION, this.getX() + f, this.getY() + 2.0 + f1, this.getZ() + f2, 0.0, 0.0, 0.0);
        } else {
            this.checkCrystals();
            Vec3 deltaMovement = this.getDeltaMovement();
            float f1 = 0.2F / ((float)deltaMovement.horizontalDistance() * 10.0F + 1.0F);
            f1 *= (float)Math.pow(2.0, deltaMovement.y);
            if (this.phaseManager.getCurrentPhase().isSitting()) {
                this.flapTime += 0.1F;
            } else if (this.inWall) {
                this.flapTime += f1 * 0.5F;
            } else {
                this.flapTime += f1;
            }

            this.setYRot(Mth.wrapDegrees(this.getYRot()));
            if (this.isNoAi()) {
                this.flapTime = 0.5F;
            } else {
                this.flightHistory.record(this.getY(), this.getYRot());
                if (this.level() instanceof ServerLevel serverLevel1) {
                    DragonPhaseInstance currentPhase = this.phaseManager.getCurrentPhase();
                    currentPhase.doServerTick(serverLevel1);
                    if (this.phaseManager.getCurrentPhase() != currentPhase) {
                        currentPhase = this.phaseManager.getCurrentPhase();
                        currentPhase.doServerTick(serverLevel1);
                    }

                    Vec3 flyTargetLocation = currentPhase.getFlyTargetLocation();
                    if (flyTargetLocation != null && currentPhase.getPhase() != EnderDragonPhase.HOVERING) { // CraftBukkit - Don't move when hovering
                        double d = flyTargetLocation.x - this.getX();
                        double d1 = flyTargetLocation.y - this.getY();
                        double d2 = flyTargetLocation.z - this.getZ();
                        double d3 = d * d + d1 * d1 + d2 * d2;
                        float flySpeed = currentPhase.getFlySpeed();
                        double squareRoot = Math.sqrt(d * d + d2 * d2);
                        if (squareRoot > 0.0) {
                            d1 = Mth.clamp(d1 / squareRoot, (double)(-flySpeed), (double)flySpeed);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(0.0, d1 * 0.01, 0.0));
                        this.setYRot(Mth.wrapDegrees(this.getYRot()));
                        Vec3 vec3 = flyTargetLocation.subtract(this.getX(), this.getY(), this.getZ()).normalize();
                        Vec3 vec31 = new Vec3(
                                Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)),
                                this.getDeltaMovement().y,
                                -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0))
                            )
                            .normalize();
                        float max = Math.max(((float)vec31.dot(vec3) + 0.5F) / 1.5F, 0.0F);
                        if (Math.abs(d) > 1.0E-5F || Math.abs(d2) > 1.0E-5F) {
                            float f3 = Mth.clamp(Mth.wrapDegrees(180.0F - (float)Mth.atan2(d, d2) * (180.0F / (float)Math.PI) - this.getYRot()), -50.0F, 50.0F);
                            this.yRotA *= 0.8F;
                            this.yRotA = this.yRotA + f3 * currentPhase.getTurnSpeed();
                            this.setYRot(this.getYRot() + this.yRotA * 0.1F);
                        }

                        float f3 = (float)(2.0 / (d3 + 1.0));
                        float f4 = 0.06F;
                        this.moveRelative(0.06F * (max * f3 + (1.0F - f3)), new Vec3(0.0, 0.0, -1.0));
                        if (this.inWall) {
                            this.move(MoverType.SELF, this.getDeltaMovement().scale(0.8F));
                        } else {
                            this.move(MoverType.SELF, this.getDeltaMovement());
                        }

                        Vec3 vec32 = this.getDeltaMovement().normalize();
                        double d4 = 0.8 + 0.15 * (vec32.dot(vec31) + 1.0) / 2.0;
                        this.setDeltaMovement(this.getDeltaMovement().multiply(d4, 0.91F, d4));
                    }
                } else {
                    this.interpolation.interpolate();
                    this.phaseManager.getCurrentPhase().doClientTick();
                }

                if (!this.level().isClientSide()) {
                    this.applyEffectsFromBlocks();
                }

                this.yBodyRot = this.getYRot();
                Vec3[] vec3s = new Vec3[this.subEntities.length];

                for (int i = 0; i < this.subEntities.length; i++) {
                    vec3s[i] = new Vec3(this.subEntities[i].getX(), this.subEntities[i].getY(), this.subEntities[i].getZ());
                }

                float f5 = (float)(this.flightHistory.get(5).y() - this.flightHistory.get(10).y()) * 10.0F * (float) (Math.PI / 180.0);
                float cos = Mth.cos(f5);
                float sin = Mth.sin(f5);
                float f6 = this.getYRot() * (float) (Math.PI / 180.0);
                float sin1 = Mth.sin(f6);
                float cos1 = Mth.cos(f6);
                this.tickPart(this.body, sin1 * 0.5F, 0.0, -cos1 * 0.5F);
                this.tickPart(this.wing1, cos1 * 4.5F, 2.0, sin1 * 4.5F);
                this.tickPart(this.wing2, cos1 * -4.5F, 2.0, sin1 * -4.5F);
                if (this.level() instanceof ServerLevel serverLevel2 && this.hurtTime == 0) {
                    this.knockBack(
                        serverLevel2,
                        serverLevel2.getEntities(
                            this, this.wing1.getBoundingBox().inflate(4.0, 2.0, 4.0).move(0.0, -2.0, 0.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR
                        )
                    );
                    this.knockBack(
                        serverLevel2,
                        serverLevel2.getEntities(
                            this, this.wing2.getBoundingBox().inflate(4.0, 2.0, 4.0).move(0.0, -2.0, 0.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR
                        )
                    );
                    this.hurt(serverLevel2, serverLevel2.getEntities(this, this.head.getBoundingBox().inflate(1.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    this.hurt(serverLevel2, serverLevel2.getEntities(this, this.neck.getBoundingBox().inflate(1.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR));
                }

                float sin2 = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0) - this.yRotA * 0.01F);
                float cos2 = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0) - this.yRotA * 0.01F);
                float headYOffset = this.getHeadYOffset();
                this.tickPart(this.head, sin2 * 6.5F * cos, headYOffset + sin * 6.5F, -cos2 * 6.5F * cos);
                this.tickPart(this.neck, sin2 * 5.5F * cos, headYOffset + sin * 5.5F, -cos2 * 5.5F * cos);
                DragonFlightHistory.Sample sample = this.flightHistory.get(5);

                for (int i1 = 0; i1 < 3; i1++) {
                    EnderDragonPart enderDragonPart = null;
                    if (i1 == 0) {
                        enderDragonPart = this.tail1;
                    }

                    if (i1 == 1) {
                        enderDragonPart = this.tail2;
                    }

                    if (i1 == 2) {
                        enderDragonPart = this.tail3;
                    }

                    DragonFlightHistory.Sample sample1 = this.flightHistory.get(12 + i1 * 2);
                    float f7 = this.getYRot() * (float) (Math.PI / 180.0) + this.rotWrap(sample1.yRot() - sample.yRot()) * (float) (Math.PI / 180.0);
                    float sin3 = Mth.sin(f7);
                    float maxx = Mth.cos(f7);
                    float f3 = 1.5F;
                    float f4 = (i1 + 1) * 2.0F;
                    this.tickPart(
                        enderDragonPart, -(sin1 * 1.5F + sin3 * f4) * cos, sample1.y() - sample.y() - (f4 + 1.5F) * sin + 1.5, (cos1 * 1.5F + maxx * f4) * cos
                    );
                }

                if (this.level() instanceof ServerLevel serverLevel3) {
                    this.inWall = this.checkWalls(serverLevel3, this.head.getBoundingBox())
                        | this.checkWalls(serverLevel3, this.neck.getBoundingBox())
                        | this.checkWalls(serverLevel3, this.body.getBoundingBox());
                    if (this.dragonFight != null) {
                        this.dragonFight.updateDragon(this);
                    }
                }

                for (int i1 = 0; i1 < this.subEntities.length; i1++) {
                    this.subEntities[i1].xo = vec3s[i1].x;
                    this.subEntities[i1].yo = vec3s[i1].y;
                    this.subEntities[i1].zo = vec3s[i1].z;
                    this.subEntities[i1].xOld = vec3s[i1].x;
                    this.subEntities[i1].yOld = vec3s[i1].y;
                    this.subEntities[i1].zOld = vec3s[i1].z;
                }
            }
        }
    }

    private void tickPart(EnderDragonPart part, double offsetX, double offsetY, double offsetZ) {
        part.setPos(this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ);
    }

    private float getHeadYOffset() {
        if (this.phaseManager.getCurrentPhase().isSitting()) {
            return -1.0F;
        } else {
            DragonFlightHistory.Sample sample = this.flightHistory.get(5);
            DragonFlightHistory.Sample sample1 = this.flightHistory.get(0);
            return (float)(sample.y() - sample1.y());
        }
    }

    private void checkCrystals() {
        if (this.nearestCrystal != null) {
            if (this.nearestCrystal.isRemoved()) {
                this.nearestCrystal = null;
            } else if (this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityRegainHealthEvent event = new org.bukkit.event.entity.EntityRegainHealthEvent(this.getBukkitEntity(), 1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
                if (event.callEvent()) {
                    this.setHealth((float) (this.getHealth() + event.getAmount()));
                }
                // CraftBukkit end
            }
        }

        if (this.random.nextInt(10) == 0) {
            List<EndCrystal> entitiesOfClass = this.level().getEntitiesOfClass(EndCrystal.class, this.getBoundingBox().inflate(32.0));
            EndCrystal endCrystal = null;
            double d = Double.MAX_VALUE;

            for (EndCrystal endCrystal1 : entitiesOfClass) {
                double d1 = endCrystal1.distanceToSqr(this);
                if (d1 < d) {
                    d = d1;
                    endCrystal = endCrystal1;
                }
            }

            this.nearestCrystal = endCrystal;
        }
    }

    private void knockBack(ServerLevel level, List<Entity> targets) {
        double d = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0;
        double d1 = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity livingEntity) {
                double d2 = entity.getX() - d;
                double d3 = entity.getZ() - d1;
                double max = Math.max(d2 * d2 + d3 * d3, 0.1);
                entity.push(d2 / max * 4.0, 0.2F, d3 / max * 4.0, this); // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                if (!this.phaseManager.getCurrentPhase().isSitting() && livingEntity.getLastHurtByMobTimestamp() < entity.tickCount - 2) {
                    DamageSource damageSource = this.damageSources().mobAttack(this);
                    entity.hurtServer(level, damageSource, 5.0F);
                    EnchantmentHelper.doPostAttackEffects(level, entity, damageSource);
                }
            }
        }
    }

    private void hurt(ServerLevel level, List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                DamageSource damageSource = this.damageSources().mobAttack(this);
                entity.hurtServer(level, damageSource, 10.0F);
                EnchantmentHelper.doPostAttackEffects(level, entity, damageSource);
            }
        }
    }

    private float rotWrap(double angle) {
        return (float)Mth.wrapDegrees(angle);
    }

    private boolean checkWalls(ServerLevel level, AABB box) {
        int floor = Mth.floor(box.minX);
        int floor1 = Mth.floor(box.minY);
        int floor2 = Mth.floor(box.minZ);
        int floor3 = Mth.floor(box.maxX);
        int floor4 = Mth.floor(box.maxY);
        int floor5 = Mth.floor(box.maxZ);
        boolean flag = false;
        boolean flag1 = false;
        List<org.bukkit.block.Block> destroyedBlocks = new java.util.ArrayList<>(); // Paper - Create a list to hold all the destroyed blocks

        for (int i = floor; i <= floor3; i++) {
            for (int i1 = floor1; i1 <= floor4; i1++) {
                for (int i2 = floor2; i2 <= floor5; i2++) {
                    BlockPos blockPos = new BlockPos(i, i1, i2);
                    BlockState blockState = level.getBlockState(blockPos);
                    if (!blockState.isAir() && !blockState.is(BlockTags.DRAGON_TRANSPARENT)) {
                        if (level.getGameRules().get(GameRules.MOB_GRIEFING) && !blockState.is(BlockTags.DRAGON_IMMUNE)) {
                            // CraftBukkit start - Add blocks to list rather than destroying them
                            //flag1 = level.removeBlock(blockPos, false) || flag1;
                            flag1 = true;
                            destroyedBlocks.add(org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos));
                            // CraftBukkit end
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        // CraftBukkit start - Set off an EntityExplodeEvent for the dragon exploding all these blocks
        // SPIGOT-4882: don't fire event if nothing hit
        if (!flag1) {
            return flag;
        }

        org.bukkit.event.entity.EntityExplodeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityExplodeEvent(this, destroyedBlocks, 0F, this.explosionSource.getBlockInteraction());
        if (event.isCancelled()) {
            // This flag literally means 'Dragon hit something hard' (Obsidian, White Stone or Bedrock) and will cause the dragon to slow down.
            // We should consider adding an event extension for it, or perhaps returning true if the event is cancelled.
            return flag;
        } else if (event.getYield() == 0F) {
            // Yield zero ==> no drops
            for (org.bukkit.block.Block block : event.blockList()) {
                this.level().removeBlock(new BlockPos(block.getX(), block.getY(), block.getZ()), false);
            }
        } else {
            for (org.bukkit.block.Block block : event.blockList()) {
                org.bukkit.Material blockType = block.getType();
                if (blockType.isAir()) {
                    continue;
                }

                org.bukkit.craftbukkit.block.CraftBlock craftBlock = ((org.bukkit.craftbukkit.block.CraftBlock) block);
                BlockPos pos = craftBlock.getPosition();

                net.minecraft.world.level.block.Block nmsBlock = craftBlock.getNMS().getBlock();
                if (nmsBlock.dropFromExplosion(this.explosionSource)) {
                    net.minecraft.world.level.block.entity.BlockEntity blockEntity = craftBlock.getNMS().hasBlockEntity() ? this.level().getBlockEntity(pos) : null;
                    net.minecraft.world.level.storage.loot.LootParams.Builder builder = new net.minecraft.world.level.storage.loot.LootParams.Builder((ServerLevel) this.level())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, net.minecraft.world.item.ItemStack.EMPTY)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, 1.0F / event.getYield())
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, blockEntity);

                    craftBlock.getNMS().getDrops(builder).forEach((stack) -> {
                        net.minecraft.world.level.block.Block.popResource(this.level(), pos, stack);
                    });
                    craftBlock.getNMS().spawnAfterBreak((ServerLevel) this.level(), pos, net.minecraft.world.item.ItemStack.EMPTY, false);
                }
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(this.level(), pos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION, explosionSource.getIndirectSourceEntity().getBukkitEntity()).callEvent())
                    continue;
                // Paper end - TNTPrimeEvent
                nmsBlock.wasExploded((ServerLevel) this.level(), pos, this.explosionSource);

                this.level().removeBlock(pos, false);
            }
        }
        // CraftBukkit end

        if (flag1) {
            BlockPos blockPos1 = new BlockPos(
                floor + this.random.nextInt(floor3 - floor + 1),
                floor1 + this.random.nextInt(floor4 - floor1 + 1),
                floor2 + this.random.nextInt(floor5 - floor2 + 1)
            );
            level.levelEvent(LevelEvent.PARTICLES_DRAGON_BLOCK_BREAK, blockPos1, 0);
        }

        return flag;
    }

    public boolean hurt(ServerLevel level, EnderDragonPart part, DamageSource damageSource, float amount) {
        if (this.phaseManager.getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return false;
        } else {
            amount = this.phaseManager.getCurrentPhase().onHurt(damageSource, amount);
            if (part != this.head) {
                amount = amount / 4.0F + Math.min(amount, 1.0F);
            }

            if (amount < 0.01F) {
                return false;
            } else {
                if (damageSource.getEntity() instanceof Player || damageSource.is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)) {
                    float health = this.getHealth();
                    this.reallyHurt(level, damageSource, amount);
                    if (this.isDeadOrDying() && !this.phaseManager.getCurrentPhase().isSitting()) {
                        this.setHealth(1.0F);
                        this.phaseManager.setPhase(EnderDragonPhase.DYING);
                    }

                    if (this.phaseManager.getCurrentPhase().isSitting()) {
                        this.sittingDamageReceived = this.sittingDamageReceived + health - this.getHealth();
                        if (this.sittingDamageReceived > 0.25F * this.getMaxHealth()) {
                            this.sittingDamageReceived = 0.0F;
                            this.phaseManager.setPhase(EnderDragonPhase.TAKEOFF);
                        }
                    }
                }

                return true;
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return this.hurt(level, this.body, damageSource, amount);
    }

    protected void reallyHurt(ServerLevel level, DamageSource damageSource, float amount) {
        super.hurtServer(level, damageSource, amount);
    }

    @Override
    public void kill(ServerLevel level) {
        // Paper start - Fire entity death event
        this.silentDeath = true;
        org.bukkit.event.entity.EntityDeathEvent deathEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this, this.damageSources().genericKill());
        if (deathEvent.isCancelled()) {
            this.silentDeath = false; // Reset to default if event was cancelled
            return;
        }
        this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        // Paper end - Fire entity death event
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
            this.dragonFight.setDragonKilled(this);
        }
    }

    @Override
    protected void tickDeath() {
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
        }

        this.dragonDeathTime++;
        if (this.dragonDeathTime >= 180 && this.dragonDeathTime <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;
            this.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, this.getX() + f, this.getY() + 2.0 + f1, this.getZ() + f2, 0.0, 0.0, 0.0);
        }

        // CraftBukkit start - SPIGOT-2420: Moved up to #getExpReward method
        /*
        int i = 500;
        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            i = 12000;
        }
         */
        int i = this.expToDrop;
        // CraftBukkit end

        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.dragonDeathTime > 150 && this.dragonDeathTime % 5 == 0) { // CraftBukkit - SPIGOT-2420: Already checked for the game rule when calculating the xp
                ExperienceOrb.awardWithDirection(serverLevel, this.position(), net.minecraft.world.phys.Vec3.ZERO, Mth.floor(i * 0.08F), org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, net.minecraft.world.entity.EntityReference.get(this.lastHurtByPlayer, this.level(), Player.class), this); // Paper
            }

            if (this.dragonDeathTime == 1 && !this.isSilent()) {
                // CraftBukkit start - Use relative location for far away sounds
                // serverLevel.globalLevelEvent(LevelEvent.SOUND_DRAGON_DEATH, this.blockPosition(), 0);
                int viewDistance = serverLevel.getCraftServer().getViewDistance() * 16;
                for (net.minecraft.server.level.ServerPlayer player : serverLevel.getPlayersForGlobalSoundGamerule()) { // Paper - respect global sound events gamerule
                    double deltaX = this.getX() - player.getX();
                    double deltaZ = this.getZ() - player.getZ();
                    double distanceSquared = Mth.square(deltaX) + Mth.square(deltaZ);
                    final double soundRadiusSquared = serverLevel.getGlobalSoundRangeSquared(config -> config.dragonDeathSoundRadius); // Paper - respect global sound events gamerule
                    if (!serverLevel.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS) && distanceSquared > soundRadiusSquared) continue; // Paper - respect global sound events gamerule
                    if (distanceSquared > Mth.square(viewDistance)) {
                        double deltaLength = Math.sqrt(distanceSquared);
                        double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                        double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_DRAGON_DEATH, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                    } else {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_DRAGON_DEATH, new BlockPos((int) this.getX(), (int) this.getY(), (int) this.getZ()), 0, true));
                    }
                }
                // CraftBukkit end
            }
        }

        Vec3 vec3 = new Vec3(0.0, 0.1F, 0.0);
        this.move(MoverType.SELF, vec3);

        for (EnderDragonPart enderDragonPart : this.subEntities) {
            enderDragonPart.setOldPosAndRot();
            enderDragonPart.setPos(enderDragonPart.position().add(vec3));
        }

        if (this.dragonDeathTime == 200 && this.level() instanceof ServerLevel serverLevel1) {
            if (true) { // Paper - SPIGOT-2420: Already checked for the game rule when calculating the xp
                ExperienceOrb.awardWithDirection(serverLevel1, this.position(), net.minecraft.world.phys.Vec3.ZERO, Mth.floor(i * 0.2F), org.bukkit.entity.ExperienceOrb.SpawnReason.ENTITY_DEATH, net.minecraft.world.entity.EntityReference.get(this.lastHurtByPlayer, this.level(), Player.class), this); // Paper
            }

            if (this.dragonFight != null) {
                this.dragonFight.setDragonKilled(this);
            }

            this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            this.gameEvent(GameEvent.ENTITY_DIE);
        }
    }

    public int findClosestNode() {
        if (this.nodes[0] == null) {
            for (int i = 0; i < 24; i++) {
                int i1 = 5;
                int floor;
                int floor1;
                if (i < 12) {
                    floor = Mth.floor(60.0F * Mth.cos(2.0F * ((float) -Math.PI + (float) (Math.PI / 12) * i)));
                    floor1 = Mth.floor(60.0F * Mth.sin(2.0F * ((float) -Math.PI + (float) (Math.PI / 12) * i)));
                } else if (i < 20) {
                    int i2 = i - 12;
                    floor = Mth.floor(40.0F * Mth.cos(2.0F * ((float) -Math.PI + (float) (Math.PI / 8) * i2)));
                    floor1 = Mth.floor(40.0F * Mth.sin(2.0F * ((float) -Math.PI + (float) (Math.PI / 8) * i2)));
                    i1 += 10;
                } else {
                    int var7 = i - 20;
                    floor = Mth.floor(20.0F * Mth.cos(2.0F * ((float) -Math.PI + (float) (Math.PI / 4) * var7)));
                    floor1 = Mth.floor(20.0F * Mth.sin(2.0F * ((float) -Math.PI + (float) (Math.PI / 4) * var7)));
                }

                int max = Math.max(73, this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(floor, 0, floor1)).getY() + i1);
                this.nodes[i] = new Node(floor, max, floor1);
            }

            this.nodeAdjacency[0] = 6146;
            this.nodeAdjacency[1] = 8197;
            this.nodeAdjacency[2] = 8202;
            this.nodeAdjacency[3] = 16404;
            this.nodeAdjacency[4] = 32808;
            this.nodeAdjacency[5] = 32848;
            this.nodeAdjacency[6] = 65696;
            this.nodeAdjacency[7] = 131392;
            this.nodeAdjacency[8] = 131712;
            this.nodeAdjacency[9] = 263424;
            this.nodeAdjacency[10] = 526848;
            this.nodeAdjacency[11] = 525313;
            this.nodeAdjacency[12] = 1581057;
            this.nodeAdjacency[13] = 3166214;
            this.nodeAdjacency[14] = 2138120;
            this.nodeAdjacency[15] = 6373424;
            this.nodeAdjacency[16] = 4358208;
            this.nodeAdjacency[17] = 12910976;
            this.nodeAdjacency[18] = 9044480;
            this.nodeAdjacency[19] = 9706496;
            this.nodeAdjacency[20] = 15216640;
            this.nodeAdjacency[21] = 13688832;
            this.nodeAdjacency[22] = 11763712;
            this.nodeAdjacency[23] = 8257536;
        }

        return this.findClosestNode(this.getX(), this.getY(), this.getZ());
    }

    public int findClosestNode(double x, double y, double z) {
        float f = 10000.0F;
        int i = 0;
        Node node = new Node(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int i1 = 0;
        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            i1 = 12;
        }

        for (int i2 = i1; i2 < 24; i2++) {
            if (this.nodes[i2] != null) {
                float f1 = this.nodes[i2].distanceToSqr(node);
                if (f1 < f) {
                    f = f1;
                    i = i2;
                }
            }
        }

        return i;
    }

    public @Nullable Path findPath(int startIndex, int finishIndex, @Nullable Node andThen) {
        for (int i = 0; i < 24; i++) {
            Node node = this.nodes[i];
            node.closed = false;
            node.f = 0.0F;
            node.g = 0.0F;
            node.h = 0.0F;
            node.cameFrom = null;
            node.heapIdx = -1;
        }

        Node node1 = this.nodes[startIndex];
        Node node = this.nodes[finishIndex];
        node1.g = 0.0F;
        node1.h = node1.distanceTo(node);
        node1.f = node1.h;
        this.openSet.clear();
        this.openSet.insert(node1);
        Node node2 = node1;
        int i1 = 0;
        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            i1 = 12;
        }

        while (!this.openSet.isEmpty()) {
            Node node3 = this.openSet.pop();
            if (node3.equals(node)) {
                if (andThen != null) {
                    andThen.cameFrom = node;
                    node = andThen;
                }

                return this.reconstructPath(node1, node);
            }

            if (node3.distanceTo(node) < node2.distanceTo(node)) {
                node2 = node3;
            }

            node3.closed = true;
            int i2 = 0;

            for (int i3 = 0; i3 < 24; i3++) {
                if (this.nodes[i3] == node3) {
                    i2 = i3;
                    break;
                }
            }

            for (int i3x = i1; i3x < 24; i3x++) {
                if ((this.nodeAdjacency[i2] & 1 << i3x) > 0) {
                    Node node4 = this.nodes[i3x];
                    if (!node4.closed) {
                        float f = node3.g + node3.distanceTo(node4);
                        if (!node4.inOpenSet() || f < node4.g) {
                            node4.cameFrom = node3;
                            node4.g = f;
                            node4.h = node4.distanceTo(node);
                            if (node4.inOpenSet()) {
                                this.openSet.changeCost(node4, node4.g + node4.h);
                            } else {
                                node4.f = node4.g + node4.h;
                                this.openSet.insert(node4);
                            }
                        }
                    }
                }
            }
        }

        if (node2 == node1) {
            return null;
        } else {
            LOGGER.debug("Failed to find path from {} to {}", startIndex, finishIndex);
            if (andThen != null) {
                andThen.cameFrom = node2;
                node2 = andThen;
            }

            return this.reconstructPath(node1, node2);
        }
    }

    private Path reconstructPath(Node start, Node finish) {
        List<Node> list = Lists.newArrayList();
        Node node = finish;
        list.add(0, finish);

        while (node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }

        return new Path(list, new BlockPos(finish.x, finish.y, finish.z), true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("DragonPhase", this.phaseManager.getCurrentPhase().getPhase().getId());
        output.putInt("DragonDeathTime", this.dragonDeathTime);
        output.putInt("Bukkit.expToDrop", this.expToDrop); // CraftBukkit - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getInt("DragonPhase").ifPresent(integer -> this.phaseManager.setPhase(EnderDragonPhase.getById(integer)));
        this.dragonDeathTime = input.getIntOr("DragonDeathTime", 0);
        this.expToDrop = input.getIntOr("Bukkit.expToDrop", 0); // CraftBukkit - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
    }

    @Override
    public void checkDespawn() {
    }

    public EnderDragonPart[] getSubEntities() {
        return this.subEntities;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.ENDER_DRAGON_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    public float getSoundVolume() {
        return 5.0F;
    }

    public Vec3 getHeadLookVector(float partialTick) {
        DragonPhaseInstance currentPhase = this.phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> phase = currentPhase.getPhase();
        Vec3 viewVector;
        if (phase == EnderDragonPhase.LANDING || phase == EnderDragonPhase.TAKEOFF) {
            BlockPos heightmapPos = this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getPodium()); // Paper - Allow changing the EnderDragon podium
            float max = Math.max((float)Math.sqrt(heightmapPos.distToCenterSqr(this.position())) / 4.0F, 1.0F);
            float f = 6.0F / max;
            float xRot = this.getXRot();
            float f1 = 1.5F;
            this.setXRot(-f * 1.5F * 5.0F);
            viewVector = this.getViewVector(partialTick);
            this.setXRot(xRot);
        } else if (currentPhase.isSitting()) {
            float xRot1 = this.getXRot();
            float max = 1.5F;
            this.setXRot(-45.0F);
            viewVector = this.getViewVector(partialTick);
            this.setXRot(xRot1);
        } else {
            viewVector = this.getViewVector(partialTick);
        }

        return viewVector;
    }

    public void onCrystalDestroyed(ServerLevel level, EndCrystal crystal, BlockPos pos, DamageSource damageSource) {
        Player player1;
        if (damageSource.getEntity() instanceof Player player) {
            player1 = player;
        } else {
            player1 = level.getNearestPlayer(CRYSTAL_DESTROY_TARGETING, pos.getX(), pos.getY(), pos.getZ());
        }

        if (crystal == this.nearestCrystal) {
            this.hurt(level, this.head, this.damageSources().explosion(crystal, player1), 10.0F);
        }

        this.phaseManager.getCurrentPhase().onCrystalDestroyed(crystal, pos, damageSource, player1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_PHASE.equals(key) && this.level().isClientSide()) {
            this.phaseManager.setPhase(EnderDragonPhase.getById(this.getEntityData().get(DATA_PHASE)));
        }

        super.onSyncedDataUpdated(key);
    }

    public EnderDragonPhaseManager getPhaseManager() {
        return this.phaseManager;
    }

    public @Nullable EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        EnderDragonPart[] subEntities = this.getSubEntities();

        for (int i = 0; i < subEntities.length; i++) {
            subEntities[i].setId(i + packet.getId() + 1);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target.canBeSeenAsEnemy();
    }

    @Override
    protected float sanitizeScale(float scale) {
        return 1.0F;
    }

    // CraftBukkit start - SPIGOT-2420: Special case, the ender dragon drops 12000 xp for the first kill and 500 xp for every other kill and this over time.
    @Override
    public int getExpReward(ServerLevel level, Entity entity) {
        // CraftBukkit - Moved from #tickDeath method
        boolean flag = level.getGameRules().get(GameRules.MOB_DROPS);
        int i = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            i = 12000;
        }

        return flag ? i : 0;
    }
    // CraftBukkit end
    
    // Luminol start - Sync dragon part when teleportation or firstly created
    public void syncDragonPartsAfterTeleportTransform() {
        for (EnderDragonPart part : this.subEntities) {
            this.tickPart(part, 0.0, 0.0, 0.0); // offset -> 0.0
        }
    }
    // Luminol end
}
