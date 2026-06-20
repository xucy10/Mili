package net.minecraft.world.entity.ai.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MoveControl implements Control {
    public static final float MIN_SPEED = 5.0E-4F;
    public static final float MIN_SPEED_SQR = 2.5000003E-7F;
    protected static final int MAX_TURN = 90;
    protected final Mob mob;
    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;
    protected double speedModifier;
    protected float strafeForwards;
    protected float strafeRight;
    protected MoveControl.Operation operation = MoveControl.Operation.WAIT;

    public MoveControl(Mob mob) {
        this.mob = mob;
    }

    public boolean hasWanted() {
        return this.operation == MoveControl.Operation.MOVE_TO;
    }

    public double getSpeedModifier() {
        return this.speedModifier;
    }

    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        this.wantedX = x;
        this.wantedY = y;
        this.wantedZ = z;
        this.speedModifier = speedModifier;
        if (this.operation != MoveControl.Operation.JUMPING) {
            this.operation = MoveControl.Operation.MOVE_TO;
        }
    }

    public void strafe(float forward, float strafe) {
        this.operation = MoveControl.Operation.STRAFE;
        this.strafeForwards = forward;
        this.strafeRight = strafe;
        this.speedModifier = 0.25;
    }

    public void tick() {
        if (this.operation == MoveControl.Operation.STRAFE) {
            float f = (float)this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float f1 = (float)this.speedModifier * f;
            float f2 = this.strafeForwards;
            float f3 = this.strafeRight;
            float squareRoot = Mth.sqrt(f2 * f2 + f3 * f3);
            if (squareRoot < 1.0F) {
                squareRoot = 1.0F;
            }

            squareRoot = f1 / squareRoot;
            f2 *= squareRoot;
            f3 *= squareRoot;
            float sin = Mth.sin(this.mob.getYRot() * (float) (Math.PI / 180.0));
            float cos = Mth.cos(this.mob.getYRot() * (float) (Math.PI / 180.0));
            float f4 = f2 * cos - f3 * sin;
            float f5 = f3 * cos + f2 * sin;
            if (!this.isWalkable(f4, f5)) {
                this.strafeForwards = 1.0F;
                this.strafeRight = 0.0F;
            }

            this.mob.setSpeed(f1);
            this.mob.setZza(this.strafeForwards);
            this.mob.setXxa(this.strafeRight);
            this.operation = MoveControl.Operation.WAIT;
        } else if (this.operation == MoveControl.Operation.MOVE_TO) {
            this.operation = MoveControl.Operation.WAIT;
            double d = this.wantedX - this.mob.getX();
            double d1 = this.wantedZ - this.mob.getZ();
            double d2 = this.wantedY - this.mob.getY();
            double d3 = d * d + d2 * d2 + d1 * d1;
            if (d3 < 2.5000003E-7F) {
                this.mob.setZza(0.0F);
                return;
            }

            float f5 = (float)(Mth.atan2(d1, d) * 180.0F / (float)Math.PI) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), f5, 90.0F));
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            BlockPos blockPos = this.mob.blockPosition();
            BlockState blockState = this.mob.level().getBlockState(blockPos);
            VoxelShape collisionShape = blockState.getCollisionShape(this.mob.level(), blockPos);
            if (d2 > this.mob.maxUpStep() && d * d + d1 * d1 < Math.max(1.0F, this.mob.getBbWidth())
                || !collisionShape.isEmpty()
                    && this.mob.getY() < collisionShape.max(Direction.Axis.Y) + blockPos.getY()
                    && !blockState.is(BlockTags.DOORS)
                    && !blockState.is(BlockTags.FENCES)) {
                this.mob.getJumpControl().jump();
                this.operation = MoveControl.Operation.JUMPING;
            }
        } else if (this.operation == MoveControl.Operation.JUMPING) {
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.mob.onGround() || this.mob.isInLiquid() && this.mob.isAffectedByFluids()) {
                this.operation = MoveControl.Operation.WAIT;
            }
        } else {
            this.mob.setZza(0.0F);
        }
    }

    private boolean isWalkable(float relativeX, float relativeZ) {
        PathNavigation navigation = this.mob.getNavigation();
        if (navigation != null) {
            NodeEvaluator nodeEvaluator = navigation.getNodeEvaluator();
            if (nodeEvaluator != null
                && nodeEvaluator.getPathType(this.mob, BlockPos.containing(this.mob.getX() + relativeX, this.mob.getBlockY(), this.mob.getZ() + relativeZ))
                    != PathType.WALKABLE) {
                return false;
            }
        }

        return true;
    }

    protected float rotlerp(float sourceAngle, float targetAngle, float maximumChange) {
        float f = Mth.wrapDegrees(targetAngle - sourceAngle);
        if (f > maximumChange) {
            f = maximumChange;
        }

        if (f < -maximumChange) {
            f = -maximumChange;
        }

        float f1 = sourceAngle + f;
        if (f1 < 0.0F) {
            f1 += 360.0F;
        } else if (f1 > 360.0F) {
            f1 -= 360.0F;
        }

        return f1;
    }

    public double getWantedX() {
        return this.wantedX;
    }

    public double getWantedY() {
        return this.wantedY;
    }

    public double getWantedZ() {
        return this.wantedZ;
    }

    public void setWait() {
        this.operation = MoveControl.Operation.WAIT;
    }

    public static enum Operation {
        WAIT,
        MOVE_TO,
        STRAFE,
        JUMPING;
    }
}
