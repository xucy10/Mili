package net.minecraft.world.entity.vehicle.minecart;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class NewMinecartBehavior extends MinecartBehavior {
    public static final int POS_ROT_LERP_TICKS = 3;
    public static final double ON_RAIL_Y_OFFSET = 0.1;
    public static final double OPPOSING_SLOPES_REST_AT_SPEED_THRESHOLD = 0.005;
    private NewMinecartBehavior.@Nullable StepPartialTicks cacheIndexAlpha;
    private int cachedLerpDelay;
    private float cachedPartialTick;
    private int lerpDelay = 0;
    public final List<NewMinecartBehavior.MinecartStep> lerpSteps = new LinkedList<>();
    public final List<NewMinecartBehavior.MinecartStep> currentLerpSteps = new LinkedList<>();
    public double currentLerpStepsTotalWeight = 0.0;
    public NewMinecartBehavior.MinecartStep oldLerp = NewMinecartBehavior.MinecartStep.ZERO;

    public NewMinecartBehavior(AbstractMinecart minecart) {
        super(minecart);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) {
            BlockPos var5 = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockState = this.level().getBlockState(var5);
            if (this.minecart.isFirstTick()) {
                this.minecart.setOnRails(BaseRailBlock.isRail(blockState));
                this.adjustToRails(var5, blockState, true);
            }

            this.minecart.applyGravity();
            this.minecart.moveAlongTrack(serverLevel);
        } else {
            this.lerpClientPositionAndRotation();
            boolean isRail = BaseRailBlock.isRail(this.level().getBlockState(this.minecart.getCurrentBlockPosOrRailBelow()));
            this.minecart.setOnRails(isRail);
        }
    }

    private void lerpClientPositionAndRotation() {
        if (--this.lerpDelay <= 0) {
            this.setOldLerpValues();
            this.currentLerpSteps.clear();
            if (!this.lerpSteps.isEmpty()) {
                this.currentLerpSteps.addAll(this.lerpSteps);
                this.lerpSteps.clear();
                this.currentLerpStepsTotalWeight = 0.0;

                for (NewMinecartBehavior.MinecartStep minecartStep : this.currentLerpSteps) {
                    this.currentLerpStepsTotalWeight = this.currentLerpStepsTotalWeight + minecartStep.weight;
                }

                this.lerpDelay = this.currentLerpStepsTotalWeight == 0.0 ? 0 : 3;
            }
        }

        if (this.cartHasPosRotLerp()) {
            this.setPos(this.getCartLerpPosition(1.0F));
            this.setDeltaMovement(this.getCartLerpMovements(1.0F));
            this.setXRot(this.getCartLerpXRot(1.0F));
            this.setYRot(this.getCartLerpYRot(1.0F));
        }
    }

    public void setOldLerpValues() {
        this.oldLerp = new NewMinecartBehavior.MinecartStep(this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot(), 0.0F);
    }

    public boolean cartHasPosRotLerp() {
        return !this.currentLerpSteps.isEmpty();
    }

    public float getCartLerpXRot(float partialTick) {
        NewMinecartBehavior.StepPartialTicks currentLerpStep = this.getCurrentLerpStep(partialTick);
        return Mth.rotLerp(currentLerpStep.partialTicksInStep, currentLerpStep.previousStep.xRot, currentLerpStep.currentStep.xRot);
    }

    public float getCartLerpYRot(float partialTick) {
        NewMinecartBehavior.StepPartialTicks currentLerpStep = this.getCurrentLerpStep(partialTick);
        return Mth.rotLerp(currentLerpStep.partialTicksInStep, currentLerpStep.previousStep.yRot, currentLerpStep.currentStep.yRot);
    }

    public Vec3 getCartLerpPosition(float partialTick) {
        NewMinecartBehavior.StepPartialTicks currentLerpStep = this.getCurrentLerpStep(partialTick);
        return Mth.lerp(currentLerpStep.partialTicksInStep, currentLerpStep.previousStep.position, currentLerpStep.currentStep.position);
    }

    public Vec3 getCartLerpMovements(float partialTick) {
        NewMinecartBehavior.StepPartialTicks currentLerpStep = this.getCurrentLerpStep(partialTick);
        return Mth.lerp(currentLerpStep.partialTicksInStep, currentLerpStep.previousStep.movement, currentLerpStep.currentStep.movement);
    }

    private NewMinecartBehavior.StepPartialTicks getCurrentLerpStep(float partialTick) {
        if (partialTick == this.cachedPartialTick && this.lerpDelay == this.cachedLerpDelay && this.cacheIndexAlpha != null) {
            return this.cacheIndexAlpha;
        } else {
            float f = (3 - this.lerpDelay + partialTick) / 3.0F;
            float f1 = 0.0F;
            float f2 = 1.0F;
            boolean flag = false;

            int i;
            for (i = 0; i < this.currentLerpSteps.size(); i++) {
                float f3 = this.currentLerpSteps.get(i).weight;
                if (!(f3 <= 0.0F)) {
                    f1 += f3;
                    if (f1 >= this.currentLerpStepsTotalWeight * f) {
                        float f4 = f1 - f3;
                        f2 = (float)((f * this.currentLerpStepsTotalWeight - f4) / f3);
                        flag = true;
                        break;
                    }
                }
            }

            if (!flag) {
                i = this.currentLerpSteps.size() - 1;
            }

            NewMinecartBehavior.MinecartStep minecartStep = this.currentLerpSteps.get(i);
            NewMinecartBehavior.MinecartStep minecartStep1 = i > 0 ? this.currentLerpSteps.get(i - 1) : this.oldLerp;
            this.cacheIndexAlpha = new NewMinecartBehavior.StepPartialTicks(f2, minecartStep, minecartStep1);
            this.cachedLerpDelay = this.lerpDelay;
            this.cachedPartialTick = partialTick;
            return this.cacheIndexAlpha;
        }
    }

    public void adjustToRails(BlockPos pos, BlockState state, boolean snapToStart) {
        if (BaseRailBlock.isRail(state)) {
            RailShape railShape = state.getValue(((BaseRailBlock)state.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
            Vec3 vec3 = new Vec3(pair.getFirst()).scale(0.5);
            Vec3 vec31 = new Vec3(pair.getSecond()).scale(0.5);
            Vec3 vec32 = vec3.horizontal();
            Vec3 vec33 = vec31.horizontal();
            if (this.getDeltaMovement().length() > 1.0E-5F && this.getDeltaMovement().dot(vec32) < this.getDeltaMovement().dot(vec33)
                || this.isDecending(vec33, railShape)) {
                Vec3 vec34 = vec32;
                vec32 = vec33;
                vec33 = vec34;
            }

            float f = 180.0F - (float)(Math.atan2(vec32.z, vec32.x) * 180.0 / Math.PI);
            f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            Vec3 vec35 = this.position();
            boolean flag = vec3.x() != vec31.x() && vec3.z() != vec31.z();
            Vec3 vec39;
            if (flag) {
                Vec3 vec36 = vec31.subtract(vec3);
                Vec3 vec37 = vec35.subtract(pos.getBottomCenter()).subtract(vec3);
                Vec3 vec38 = vec36.scale(vec36.dot(vec37) / vec36.dot(vec36));
                vec39 = pos.getBottomCenter().add(vec3).add(vec38);
                f = 180.0F - (float)(Math.atan2(vec38.z, vec38.x) * 180.0 / Math.PI);
                f += this.minecart.isFlipped() ? 180.0F : 0.0F;
            } else {
                boolean flag1 = vec3.subtract(vec31).x != 0.0;
                boolean flag2 = vec3.subtract(vec31).z != 0.0;
                vec39 = new Vec3(flag2 ? pos.getCenter().x : vec35.x, pos.getY(), flag1 ? pos.getCenter().z : vec35.z);
            }

            Vec3 vec36 = vec39.subtract(vec35);
            this.setPos(vec35.add(vec36));
            float f1 = 0.0F;
            boolean flag3 = vec3.y() != vec31.y();
            if (flag3) {
                Vec3 vec310 = pos.getBottomCenter().add(vec33);
                double d = vec310.distanceTo(this.position());
                this.setPos(this.position().add(0.0, d + 0.1, 0.0));
                f1 = this.minecart.isFlipped() ? 45.0F : -45.0F;
            } else {
                this.setPos(this.position().add(0.0, 0.1, 0.0));
            }

            this.setRotation(f, f1);
            double d1 = vec35.distanceTo(this.position());
            if (d1 > 0.0) {
                this.lerpSteps
                    .add(
                        new NewMinecartBehavior.MinecartStep(
                            this.position(), this.getDeltaMovement(), this.getYRot(), this.getXRot(), snapToStart ? 0.0F : (float)d1
                        )
                    );
            }
        }
    }

    private void setRotation(float yRot, float xRot) {
        double d = Math.abs(yRot - this.getYRot());
        if (d >= 175.0 && d <= 185.0) {
            this.minecart.setFlipped(!this.minecart.isFlipped());
            yRot -= 180.0F;
            xRot *= -1.0F;
        }

        xRot = Math.clamp(xRot, -45.0F, 45.0F);
        this.setXRot(xRot % 360.0F);
        this.setYRot(yRot % 360.0F);
    }

    @Override
    public void moveAlongTrack(ServerLevel level) {
        for (NewMinecartBehavior.TrackIteration trackIteration = new NewMinecartBehavior.TrackIteration();
            trackIteration.shouldIterate() && this.minecart.isAlive();
            trackIteration.firstIteration = false
        ) {
            Vec3 deltaMovement = this.getDeltaMovement();
            BlockPos currentBlockPosOrRailBelow = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockState = this.level().getBlockState(currentBlockPosOrRailBelow);
            boolean isRail = BaseRailBlock.isRail(blockState);
            if (this.minecart.isOnRails() != isRail) {
                this.minecart.setOnRails(isRail);
                this.adjustToRails(currentBlockPosOrRailBelow, blockState, false);
            }

            if (isRail) {
                this.minecart.resetFallDistance();
                this.minecart.setOldPosAndRot();
                if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
                    this.minecart
                        .activateMinecart(
                            level,
                            currentBlockPosOrRailBelow.getX(),
                            currentBlockPosOrRailBelow.getY(),
                            currentBlockPosOrRailBelow.getZ(),
                            blockState.getValue(PoweredRailBlock.POWERED)
                        );
                }

                RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
                Vec3 vec3 = this.calculateTrackSpeed(level, deltaMovement.horizontal(), trackIteration, currentBlockPosOrRailBelow, blockState, railShape);
                if (trackIteration.firstIteration) {
                    trackIteration.movementLeft = vec3.horizontalDistance();
                } else {
                    trackIteration.movementLeft = trackIteration.movementLeft + (vec3.horizontalDistance() - deltaMovement.horizontalDistance());
                }

                this.setDeltaMovement(vec3);
                trackIteration.movementLeft = this.minecart.makeStepAlongTrack(currentBlockPosOrRailBelow, railShape, trackIteration.movementLeft);
            } else {
                this.minecart.comeOffTrack(level);
                trackIteration.movementLeft = 0.0;
            }

            Vec3 vec31 = this.position();
            Vec3 vec3 = vec31.subtract(this.minecart.oldPosition());
            double len = vec3.length();
            if (len > 1.0E-5F) {
                if (!(vec3.horizontalDistanceSqr() > 1.0E-5F)) {
                    if (!this.minecart.isOnRails()) {
                        this.setXRot(this.minecart.onGround() ? 0.0F : Mth.rotLerp(0.2F, this.getXRot(), 0.0F));
                    }
                } else {
                    float f = 180.0F - (float)(Math.atan2(vec3.z, vec3.x) * 180.0 / Math.PI);
                    float f1 = this.minecart.onGround() && !this.minecart.isOnRails()
                        ? 0.0F
                        : 90.0F - (float)(Math.atan2(vec3.horizontalDistance(), vec3.y) * 180.0 / Math.PI);
                    f += this.minecart.isFlipped() ? 180.0F : 0.0F;
                    f1 *= this.minecart.isFlipped() ? -1.0F : 1.0F;
                    this.setRotation(f, f1);
                }

                this.lerpSteps
                    .add(
                        new NewMinecartBehavior.MinecartStep(
                            vec31, this.getDeltaMovement(), this.getYRot(), this.getXRot(), (float)Math.min(len, this.getMaxSpeed(level))
                        )
                    );
            } else if (deltaMovement.horizontalDistanceSqr() > 0.0) {
                this.lerpSteps.add(new NewMinecartBehavior.MinecartStep(vec31, this.getDeltaMovement(), this.getYRot(), this.getXRot(), 1.0F));
            }

            if (len > 1.0E-5F || trackIteration.firstIteration) {
                this.minecart.applyEffectsFromBlocks();
                this.minecart.applyEffectsFromBlocks();
            }
        }
    }

    private Vec3 calculateTrackSpeed(
        ServerLevel level, Vec3 speed, NewMinecartBehavior.TrackIteration trackIteration, BlockPos pos, BlockState state, RailShape railShape
    ) {
        Vec3 vec3 = speed;
        if (!trackIteration.hasGainedSlopeSpeed) {
            Vec3 vec31 = this.calculateSlopeSpeed(speed, railShape);
            if (vec31.horizontalDistanceSqr() != speed.horizontalDistanceSqr()) {
                trackIteration.hasGainedSlopeSpeed = true;
                vec3 = vec31;
            }
        }

        if (trackIteration.firstIteration) {
            Vec3 vec31 = this.calculatePlayerInputSpeed(vec3);
            if (vec31.horizontalDistanceSqr() != vec3.horizontalDistanceSqr()) {
                trackIteration.hasHalted = true;
                vec3 = vec31;
            }
        }

        if (!trackIteration.hasHalted) {
            Vec3 vec31 = this.calculateHaltTrackSpeed(vec3, state);
            if (vec31.horizontalDistanceSqr() != vec3.horizontalDistanceSqr()) {
                trackIteration.hasHalted = true;
                vec3 = vec31;
            }
        }

        if (trackIteration.firstIteration) {
            vec3 = this.minecart.applyNaturalSlowdown(vec3);
            if (vec3.lengthSqr() > 0.0) {
                double min = Math.min(vec3.length(), this.minecart.getMaxSpeed(level));
                vec3 = vec3.normalize().scale(min);
            }
        }

        if (!trackIteration.hasBoosted) {
            Vec3 vec31 = this.calculateBoostTrackSpeed(vec3, pos, state);
            if (vec31.horizontalDistanceSqr() != vec3.horizontalDistanceSqr()) {
                trackIteration.hasBoosted = true;
                vec3 = vec31;
            }
        }

        return vec3;
    }

    private Vec3 calculateSlopeSpeed(Vec3 speed, RailShape railShape) {
        double max = Math.max(0.0078125, speed.horizontalDistance() * 0.02);
        if (this.minecart.isInWater()) {
            max *= 0.2;
        }
        return switch (railShape) {
            case ASCENDING_EAST -> speed.add(-max, 0.0, 0.0);
            case ASCENDING_WEST -> speed.add(max, 0.0, 0.0);
            case ASCENDING_NORTH -> speed.add(0.0, 0.0, max);
            case ASCENDING_SOUTH -> speed.add(0.0, 0.0, -max);
            default -> speed;
        };
    }

    private Vec3 calculatePlayerInputSpeed(Vec3 speed) {
        if (this.minecart.getFirstPassenger() instanceof ServerPlayer serverPlayer) {
            Vec3 lastClientMoveIntent = serverPlayer.getLastClientMoveIntent();
            if (lastClientMoveIntent.lengthSqr() > 0.0) {
                Vec3 vec3 = lastClientMoveIntent.normalize();
                double d = speed.horizontalDistanceSqr();
                if (vec3.lengthSqr() > 0.0 && d < 0.01) {
                    return speed.add(new Vec3(vec3.x, 0.0, vec3.z).normalize().scale(0.001));
                }
            }

            return speed;
        } else {
            return speed;
        }
    }

    private Vec3 calculateHaltTrackSpeed(Vec3 speed, BlockState state) {
        if (state.is(Blocks.POWERED_RAIL) && !state.getValue(PoweredRailBlock.POWERED)) {
            return speed.length() < 0.03 ? Vec3.ZERO : speed.scale(0.5);
        } else {
            return speed;
        }
    }

    private Vec3 calculateBoostTrackSpeed(Vec3 speed, BlockPos pos, BlockState state) {
        if (state.is(Blocks.POWERED_RAIL) && state.getValue(PoweredRailBlock.POWERED)) {
            if (speed.length() > 0.01) {
                return speed.normalize().scale(speed.length() + 0.06);
            } else {
                Vec3 redstoneDirection = this.minecart.getRedstoneDirection(pos);
                return redstoneDirection.lengthSqr() <= 0.0 ? speed : redstoneDirection.scale(speed.length() + 0.2);
            }
        } else {
            return speed;
        }
    }

    @Override
    public double stepAlongTrack(BlockPos pos, RailShape railShape, double speed) {
        if (speed < 1.0E-5F) {
            return 0.0;
        } else {
            Vec3 vec3 = this.position();
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i1 = pair.getSecond();
            Vec3 vec31 = this.getDeltaMovement().horizontal();
            if (vec31.length() < 1.0E-5F) {
                this.setDeltaMovement(Vec3.ZERO);
                return 0.0;
            } else {
                boolean flag = vec3i.getY() != vec3i1.getY();
                Vec3 vec32 = new Vec3(vec3i1).scale(0.5).horizontal();
                Vec3 vec33 = new Vec3(vec3i).scale(0.5).horizontal();
                if (vec31.dot(vec33) < vec31.dot(vec32)) {
                    vec33 = vec32;
                }

                Vec3 vec34 = pos.getBottomCenter().add(vec33).add(0.0, 0.1, 0.0).add(vec33.normalize().scale(1.0E-5F));
                if (flag && !this.isDecending(vec31, railShape)) {
                    vec34 = vec34.add(0.0, 1.0, 0.0);
                }

                Vec3 vec35 = vec34.subtract(this.position()).normalize();
                vec31 = vec35.scale(vec31.length() / vec35.horizontalDistance());
                Vec3 vec36 = vec3.add(vec31.normalize().scale(speed * (flag ? Mth.SQRT_OF_TWO : 1.0F)));
                if (vec3.distanceToSqr(vec34) <= vec3.distanceToSqr(vec36)) {
                    speed = vec34.subtract(vec36).horizontalDistance();
                    vec36 = vec34;
                } else {
                    speed = 0.0;
                }

                this.minecart.move(MoverType.SELF, vec36.subtract(vec3));
                BlockState blockState = this.level().getBlockState(BlockPos.containing(vec36));
                if (flag) {
                    if (BaseRailBlock.isRail(blockState)) {
                        RailShape railShape1 = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
                        if (this.restAtVShape(railShape, railShape1)) {
                            return 0.0;
                        }
                    }

                    double d = vec34.horizontal().distanceTo(this.position().horizontal());
                    double d1 = vec34.y + (this.isDecending(vec31, railShape) ? d : -d);
                    if (this.position().y < d1) {
                        this.setPos(this.position().x, d1, this.position().z);
                    }
                }

                if (this.position().distanceTo(vec3) < 1.0E-5F && vec36.distanceTo(vec3) > 1.0E-5F) {
                    this.setDeltaMovement(Vec3.ZERO);
                    return 0.0;
                } else {
                    this.setDeltaMovement(vec31);
                    return speed;
                }
            }
        }
    }

    private boolean restAtVShape(RailShape shape1, RailShape shape2) {
        if (this.getDeltaMovement().lengthSqr() < 0.005
            && shape2.isSlope()
            && this.isDecending(this.getDeltaMovement(), shape1)
            && !this.isDecending(this.getDeltaMovement(), shape2)) {
            this.setDeltaMovement(Vec3.ZERO);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public double getMaxSpeed(ServerLevel level) {
        // CraftBukkit start
        Double maxSpeed = this.minecart.maxSpeed;
        if (maxSpeed != null) {
            return this.minecart.isInWater() ? maxSpeed / 2.0D : maxSpeed;
        }
        // CraftBukkit end
        return level.getGameRules().get(GameRules.MAX_MINECART_SPEED).intValue() * (this.minecart.isInWater() ? 0.5 : 1.0) / 20.0;
    }

    private boolean isDecending(Vec3 speed, RailShape railShape) {
        return switch (railShape) {
            case ASCENDING_EAST -> speed.x < 0.0;
            case ASCENDING_WEST -> speed.x > 0.0;
            case ASCENDING_NORTH -> speed.z > 0.0;
            case ASCENDING_SOUTH -> speed.z < 0.0;
            default -> false;
        };
    }

    @Override
    public double getSlowdownFactor() {
        if (this.minecart.frictionState == net.kyori.adventure.util.TriState.FALSE) return 1; // Paper
        return this.minecart.isVehicle() || !this.minecart.slowWhenEmpty ? 0.997 : 0.975; // CraftBukkit - add !this.slowWhenEmpty
    }

    @Override
    public boolean pushAndPickupEntities() {
        boolean flag = this.pickupEntities(this.minecart.getBoundingBox().inflate(0.2, 0.0, 0.2));
        if (!this.minecart.horizontalCollision && !this.minecart.verticalCollision) {
            return false;
        } else {
            boolean flag1 = this.pushEntities(this.minecart.getBoundingBox().inflate(1.0E-7));
            return flag && !flag1;
        }
    }

    public boolean pickupEntities(AABB box) {
        if (this.minecart.isRideable() && !this.minecart.isVehicle()) {
            List<Entity> entities = this.level().getEntities(this.minecart, box, EntitySelector.pushableBy(this.minecart));
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    if (!(entity instanceof Player)
                        && !(entity instanceof IronGolem)
                        && !(entity instanceof AbstractMinecart)
                        && !this.minecart.isVehicle()
                        && !entity.isPassenger()) {
                        // CraftBukkit start
                        org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                            (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(),
                            entity.getBukkitEntity()
                        );
                        if (!collisionEvent.callEvent()) continue;
                        // CraftBukkit end
                        boolean flag = entity.startRiding(this.minecart);
                        if (flag) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean pushEntities(AABB box) {
        boolean flag = false;
        if (this.minecart.isRideable()) {
            List<Entity> entities = this.level().getEntities(this.minecart, box, EntitySelector.pushableBy(this.minecart));
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    if (entity instanceof Player
                        || entity instanceof IronGolem
                        || entity instanceof AbstractMinecart
                        || this.minecart.isVehicle()
                        || entity.isPassenger()) {
                        // CraftBukkit start
                        if (!this.minecart.isPassengerOfSameVehicle(entity)) {
                            org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                                (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(),
                                entity.getBukkitEntity()
                            );
                            if (!collisionEvent.callEvent()) {
                                continue;
                            }
                        }
                        // CraftBukkit end
                        entity.push(this.minecart);
                        flag = true;
                    }
                }
            }
        } else {
            for (Entity entity1 : this.level().getEntities(this.minecart, box)) {
                if (!this.minecart.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                    // CraftBukkit start
                    org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                        (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(),
                        entity1.getBukkitEntity()
                    );
                    if (!collisionEvent.callEvent()) {
                        continue;
                    }
                    // CraftBukkit end
                    entity1.push(this.minecart);
                    flag = true;
                }
            }
        }

        return flag;
    }

    public record MinecartStep(Vec3 position, Vec3 movement, float yRot, float xRot, float weight) {
        public static final StreamCodec<ByteBuf, NewMinecartBehavior.MinecartStep> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC,
            NewMinecartBehavior.MinecartStep::position,
            Vec3.STREAM_CODEC,
            NewMinecartBehavior.MinecartStep::movement,
            ByteBufCodecs.ROTATION_BYTE,
            NewMinecartBehavior.MinecartStep::yRot,
            ByteBufCodecs.ROTATION_BYTE,
            NewMinecartBehavior.MinecartStep::xRot,
            ByteBufCodecs.FLOAT,
            NewMinecartBehavior.MinecartStep::weight,
            NewMinecartBehavior.MinecartStep::new
        );
        public static NewMinecartBehavior.MinecartStep ZERO = new NewMinecartBehavior.MinecartStep(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F, 0.0F);
    }

    record StepPartialTicks(float partialTicksInStep, NewMinecartBehavior.MinecartStep currentStep, NewMinecartBehavior.MinecartStep previousStep) {
    }

    static class TrackIteration {
        double movementLeft = 0.0;
        boolean firstIteration = true;
        boolean hasGainedSlopeSpeed = false;
        boolean hasHalted = false;
        boolean hasBoosted = false;

        public boolean shouldIterate() {
            return this.firstIteration || this.movementLeft > 1.0E-5F;
        }
    }
}
