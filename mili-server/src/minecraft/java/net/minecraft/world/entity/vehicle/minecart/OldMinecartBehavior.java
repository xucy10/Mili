package net.minecraft.world.entity.vehicle.minecart;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class OldMinecartBehavior extends MinecartBehavior {
    private static final double MINECART_RIDABLE_THRESHOLD = 0.01;
    private static final double MAX_SPEED_IN_WATER = 0.2;
    private static final double MAX_SPEED_ON_LAND = 0.4;
    private static final double ABSOLUTE_MAX_SPEED = 0.4;
    private final InterpolationHandler interpolation;
    private Vec3 targetDeltaMovement = Vec3.ZERO;

    public OldMinecartBehavior(AbstractMinecart minecart) {
        super(minecart);
        this.interpolation = new InterpolationHandler(minecart, this::onInterpolation);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    public void onInterpolation(InterpolationHandler handler) {
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public void lerpMotion(Vec3 movement) {
        this.targetDeltaMovement = movement;
        this.setDeltaMovement(this.targetDeltaMovement);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.minecart.applyGravity();
            BlockPos var11 = this.minecart.getCurrentBlockPosOrRailBelow();
            BlockState blockState = this.level().getBlockState(var11);
            boolean isRail = BaseRailBlock.isRail(blockState);
            this.minecart.setOnRails(isRail);
            if (isRail) {
                this.moveAlongTrack(serverLevel);
                if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
                    this.minecart.activateMinecart(serverLevel, var11.getX(), var11.getY(), var11.getZ(), blockState.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.minecart.comeOffTrack(serverLevel);
            }

            this.minecart.applyEffectsFromBlocks();
            this.setXRot(0.0F);
            double d = this.minecart.xo - this.getX();
            double d1 = this.minecart.zo - this.getZ();
            if (d * d + d1 * d1 > 0.001) {
                this.setYRot((float)(Mth.atan2(d1, d) * 180.0 / Math.PI));
                if (this.minecart.isFlipped()) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double d2 = Mth.wrapDegrees(this.getYRot() - this.minecart.yRotO);
            if (d2 < -170.0 || d2 >= 170.0) {
                this.setYRot(this.getYRot() + 180.0F);
                this.minecart.setFlipped(!this.minecart.isFlipped());
            }

            this.setXRot(this.getXRot() % 360.0F);
            this.setYRot(this.getYRot() % 360.0F);
            this.pushAndPickupEntities();
        } else {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            } else {
                this.minecart.reapplyPosition();
                this.setXRot(this.getXRot() % 360.0F);
                this.setYRot(this.getYRot() % 360.0F);
            }
        }
    }

    @Override
    public void moveAlongTrack(ServerLevel level) {
        BlockPos currentBlockPosOrRailBelow = this.minecart.getCurrentBlockPosOrRailBelow();
        BlockState blockState = this.level().getBlockState(currentBlockPosOrRailBelow);
        this.minecart.resetFallDistance();
        double x = this.minecart.getX();
        double y = this.minecart.getY();
        double z = this.minecart.getZ();
        Vec3 pos = this.getPos(x, y, z);
        y = currentBlockPosOrRailBelow.getY();
        boolean flag = false;
        boolean flag1 = false;
        if (blockState.is(Blocks.POWERED_RAIL)) {
            flag = blockState.getValue(PoweredRailBlock.POWERED);
            flag1 = !flag;
        }

        double d = 0.0078125;
        if (this.minecart.isInWater()) {
            d *= 0.2;
        }

        Vec3 deltaMovement = this.getDeltaMovement();
        RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
        switch (railShape) {
            case ASCENDING_EAST:
                this.setDeltaMovement(deltaMovement.add(-d, 0.0, 0.0));
                y++;
                break;
            case ASCENDING_WEST:
                this.setDeltaMovement(deltaMovement.add(d, 0.0, 0.0));
                y++;
                break;
            case ASCENDING_NORTH:
                this.setDeltaMovement(deltaMovement.add(0.0, 0.0, d));
                y++;
                break;
            case ASCENDING_SOUTH:
                this.setDeltaMovement(deltaMovement.add(0.0, 0.0, -d));
                y++;
        }

        deltaMovement = this.getDeltaMovement();
        Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
        Vec3i vec3i = pair.getFirst();
        Vec3i vec3i1 = pair.getSecond();
        double d1 = vec3i1.getX() - vec3i.getX();
        double d2 = vec3i1.getZ() - vec3i.getZ();
        double squareRoot = Math.sqrt(d1 * d1 + d2 * d2);
        double d3 = deltaMovement.x * d1 + deltaMovement.z * d2;
        if (d3 < 0.0) {
            d1 = -d1;
            d2 = -d2;
        }

        double min = Math.min(2.0, deltaMovement.horizontalDistance());
        deltaMovement = new Vec3(min * d1 / squareRoot, deltaMovement.y, min * d2 / squareRoot);
        this.setDeltaMovement(deltaMovement);
        Entity firstPassenger = this.minecart.getFirstPassenger();
        Vec3 lastClientMoveIntent;
        if (this.minecart.getFirstPassenger() instanceof ServerPlayer serverPlayer) {
            lastClientMoveIntent = serverPlayer.getLastClientMoveIntent();
        } else {
            lastClientMoveIntent = Vec3.ZERO;
        }

        if (firstPassenger instanceof Player && lastClientMoveIntent.lengthSqr() > 0.0) {
            Vec3 vec3 = lastClientMoveIntent.normalize();
            double d4 = this.getDeltaMovement().horizontalDistanceSqr();
            if (vec3.lengthSqr() > 0.0 && d4 < 0.01) {
                this.setDeltaMovement(this.getDeltaMovement().add(lastClientMoveIntent.x * 0.001, 0.0, lastClientMoveIntent.z * 0.001));
                flag1 = false;
            }
        }

        if (flag1) {
            double d5 = this.getDeltaMovement().horizontalDistance();
            if (d5 < 0.03) {
                this.setDeltaMovement(Vec3.ZERO);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5));
            }
        }

        double d5 = currentBlockPosOrRailBelow.getX() + 0.5 + vec3i.getX() * 0.5;
        double d6 = currentBlockPosOrRailBelow.getZ() + 0.5 + vec3i.getZ() * 0.5;
        double d7 = currentBlockPosOrRailBelow.getX() + 0.5 + vec3i1.getX() * 0.5;
        double d8 = currentBlockPosOrRailBelow.getZ() + 0.5 + vec3i1.getZ() * 0.5;
        d1 = d7 - d5;
        d2 = d8 - d6;
        double d9;
        if (d1 == 0.0) {
            d9 = z - currentBlockPosOrRailBelow.getZ();
        } else if (d2 == 0.0) {
            d9 = x - currentBlockPosOrRailBelow.getX();
        } else {
            double d10 = x - d5;
            double d11 = z - d6;
            d9 = (d10 * d1 + d11 * d2) * 2.0;
        }

        x = d5 + d1 * d9;
        z = d6 + d2 * d9;
        this.setPos(x, y, z);
        double d10 = this.minecart.isVehicle() ? 0.75 : 1.0;
        double d11 = this.minecart.getMaxSpeed(level);
        deltaMovement = this.getDeltaMovement();
        this.minecart.move(MoverType.SELF, new Vec3(Mth.clamp(d10 * deltaMovement.x, -d11, d11), 0.0, Mth.clamp(d10 * deltaMovement.z, -d11, d11)));
        if (vec3i.getY() != 0
            && Mth.floor(this.minecart.getX()) - currentBlockPosOrRailBelow.getX() == vec3i.getX()
            && Mth.floor(this.minecart.getZ()) - currentBlockPosOrRailBelow.getZ() == vec3i.getZ()) {
            this.setPos(this.minecart.getX(), this.minecart.getY() + vec3i.getY(), this.minecart.getZ());
        } else if (vec3i1.getY() != 0
            && Mth.floor(this.minecart.getX()) - currentBlockPosOrRailBelow.getX() == vec3i1.getX()
            && Mth.floor(this.minecart.getZ()) - currentBlockPosOrRailBelow.getZ() == vec3i1.getZ()) {
            this.setPos(this.minecart.getX(), this.minecart.getY() + vec3i1.getY(), this.minecart.getZ());
        }

        this.setDeltaMovement(this.minecart.applyNaturalSlowdown(this.getDeltaMovement()));
        Vec3 pos1 = this.getPos(this.minecart.getX(), this.minecart.getY(), this.minecart.getZ());
        if (pos1 != null && pos != null) {
            double d12 = (pos.y - pos1.y) * 0.05;
            Vec3 deltaMovement1 = this.getDeltaMovement();
            double d13 = deltaMovement1.horizontalDistance();
            if (d13 > 0.0) {
                this.setDeltaMovement(deltaMovement1.multiply((d13 + d12) / d13, 1.0, (d13 + d12) / d13));
            }

            this.setPos(this.minecart.getX(), pos1.y, this.minecart.getZ());
        }

        int floor = Mth.floor(this.minecart.getX());
        int floor1 = Mth.floor(this.minecart.getZ());
        if (floor != currentBlockPosOrRailBelow.getX() || floor1 != currentBlockPosOrRailBelow.getZ()) {
            Vec3 deltaMovement1 = this.getDeltaMovement();
            double d13 = deltaMovement1.horizontalDistance();
            this.setDeltaMovement(d13 * (floor - currentBlockPosOrRailBelow.getX()), deltaMovement1.y, d13 * (floor1 - currentBlockPosOrRailBelow.getZ()));
        }

        if (flag) {
            Vec3 deltaMovement1 = this.getDeltaMovement();
            double d13 = deltaMovement1.horizontalDistance();
            if (d13 > 0.01) {
                double d14 = 0.06;
                this.setDeltaMovement(deltaMovement1.add(deltaMovement1.x / d13 * 0.06, 0.0, deltaMovement1.z / d13 * 0.06));
            } else {
                Vec3 deltaMovement2 = this.getDeltaMovement();
                double d15 = deltaMovement2.x;
                double d16 = deltaMovement2.z;
                if (railShape == RailShape.EAST_WEST) {
                    if (this.minecart.isRedstoneConductor(currentBlockPosOrRailBelow.west())) {
                        d15 = 0.02;
                    } else if (this.minecart.isRedstoneConductor(currentBlockPosOrRailBelow.east())) {
                        d15 = -0.02;
                    }
                } else {
                    if (railShape != RailShape.NORTH_SOUTH) {
                        return;
                    }

                    if (this.minecart.isRedstoneConductor(currentBlockPosOrRailBelow.north())) {
                        d16 = 0.02;
                    } else if (this.minecart.isRedstoneConductor(currentBlockPosOrRailBelow.south())) {
                        d16 = -0.02;
                    }
                }

                this.setDeltaMovement(d15, deltaMovement2.y, d16);
            }
        }
    }

    public @Nullable Vec3 getPosOffs(double x, double y, double z, double scale) {
        int floor = Mth.floor(x);
        int floor1 = Mth.floor(y);
        int floor2 = Mth.floor(z);
        if (this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2)).is(BlockTags.RAILS)) {
            floor1--;
        }

        BlockState blockState = this.level().getBlockState(new BlockPos(floor, floor1, floor2));
        if (BaseRailBlock.isRail(blockState)) {
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            y = floor1;
            if (railShape.isSlope()) {
                y = floor1 + 1;
            }

            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i1 = pair.getSecond();
            double d = vec3i1.getX() - vec3i.getX();
            double d1 = vec3i1.getZ() - vec3i.getZ();
            double squareRoot = Math.sqrt(d * d + d1 * d1);
            d /= squareRoot;
            d1 /= squareRoot;
            x += d * scale;
            z += d1 * scale;
            if (vec3i.getY() != 0 && Mth.floor(x) - floor == vec3i.getX() && Mth.floor(z) - floor2 == vec3i.getZ()) {
                y += vec3i.getY();
            } else if (vec3i1.getY() != 0 && Mth.floor(x) - floor == vec3i1.getX() && Mth.floor(z) - floor2 == vec3i1.getZ()) {
                y += vec3i1.getY();
            }

            return this.getPos(x, y, z);
        } else {
            return null;
        }
    }

    public @Nullable Vec3 getPos(double x, double y, double z) {
        int floor = Mth.floor(x);
        int floor1 = Mth.floor(y);
        int floor2 = Mth.floor(z);
        if (this.level().getBlockState(new BlockPos(floor, floor1 - 1, floor2)).is(BlockTags.RAILS)) {
            floor1--;
        }

        BlockState blockState = this.level().getBlockState(new BlockPos(floor, floor1, floor2));
        if (BaseRailBlock.isRail(blockState)) {
            RailShape railShape = blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty());
            Pair<Vec3i, Vec3i> pair = AbstractMinecart.exits(railShape);
            Vec3i vec3i = pair.getFirst();
            Vec3i vec3i1 = pair.getSecond();
            double d = floor + 0.5 + vec3i.getX() * 0.5;
            double d1 = floor1 + 0.0625 + vec3i.getY() * 0.5;
            double d2 = floor2 + 0.5 + vec3i.getZ() * 0.5;
            double d3 = floor + 0.5 + vec3i1.getX() * 0.5;
            double d4 = floor1 + 0.0625 + vec3i1.getY() * 0.5;
            double d5 = floor2 + 0.5 + vec3i1.getZ() * 0.5;
            double d6 = d3 - d;
            double d7 = (d4 - d1) * 2.0;
            double d8 = d5 - d2;
            double d9;
            if (d6 == 0.0) {
                d9 = z - floor2;
            } else if (d8 == 0.0) {
                d9 = x - floor;
            } else {
                double d10 = x - d;
                double d11 = z - d2;
                d9 = (d10 * d6 + d11 * d8) * 2.0;
            }

            x = d + d6 * d9;
            y = d1 + d7 * d9;
            z = d2 + d8 * d9;
            if (d7 < 0.0) {
                y++;
            } else if (d7 > 0.0) {
                y += 0.5;
            }

            return new Vec3(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    public double stepAlongTrack(BlockPos pos, RailShape railShape, double speed) {
        return 0.0;
    }

    @Override
    public boolean pushAndPickupEntities() {
        AABB aabb = this.minecart.getBoundingBox().inflate(0.2F, 0.0, 0.2F);
        if (this.minecart.isRideable() && this.getDeltaMovement().horizontalDistanceSqr() >= 0.01) {
            List<Entity> entities = this.level().getEntities(this.minecart, aabb, EntitySelector.pushableBy(this.minecart));
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    if (!(entity instanceof Player)
                        && !(entity instanceof IronGolem)
                        && !(entity instanceof AbstractMinecart)
                        && !this.minecart.isVehicle()
                        && !entity.isPassenger()) {
                        // CraftBukkit start
                        org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                            (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(), entity.getBukkitEntity()
                        );
                        if (!collisionEvent.callEvent()) continue;
                        // CraftBukkit end
                        entity.startRiding(this.minecart);
                    } else {
                        // CraftBukkit start
                        if (!this.minecart.isPassengerOfSameVehicle(entity)) {
                            org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                                (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(), entity.getBukkitEntity()
                            );
                            if (!collisionEvent.callEvent()) continue;
                        }
                        // CraftBukkit end
                        entity.push(this.minecart);
                    }
                }
            }
        } else {
            for (Entity entity1 : this.level().getEntities(this.minecart, aabb)) {
                if (!this.minecart.hasPassenger(entity1) && entity1.isPushable() && entity1 instanceof AbstractMinecart) {
                    // CraftBukkit start
                    org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
                        (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(), entity1.getBukkitEntity()
                    );
                    if (!collisionEvent.callEvent()) continue;
                    // CraftBukkit end
                    entity1.push(this.minecart);
                }
            }
        }

        return false;
    }

    @Override
    public Direction getMotionDirection() {
        return this.minecart.isFlipped() ? this.minecart.getDirection().getOpposite().getClockWise() : this.minecart.getDirection().getClockWise();
    }

    @Override
    public Vec3 getKnownMovement(Vec3 movement) {
        return !Double.isNaN(movement.x) && !Double.isNaN(movement.y) && !Double.isNaN(movement.z)
            ? new Vec3(Mth.clamp(movement.x, -0.4, 0.4), movement.y, Mth.clamp(movement.z, -0.4, 0.4))
            : Vec3.ZERO;
    }

    @Override
    public double getMaxSpeed(ServerLevel level) {
        // CraftBukkit start
        Double maxSpeed = this.minecart.maxSpeed;
        if (maxSpeed != null) {
            return this.minecart.isInWater() ? maxSpeed / 2.0D : maxSpeed;
        }
        // CraftBukkit end
        return this.minecart.isInWater() ? 0.2 : 0.4;
    }

    @Override
    public double getSlowdownFactor() {
        if (this.minecart.frictionState == net.kyori.adventure.util.TriState.FALSE) return 1; // Paper
        return this.minecart.isVehicle() || !this.minecart.slowWhenEmpty ? 0.997 : 0.96; // CraftBukkit - add !this.slowWhenEmpty
    }
}
