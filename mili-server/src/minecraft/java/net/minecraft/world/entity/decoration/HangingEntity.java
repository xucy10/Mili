package net.minecraft.world.entity.decoration;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;

public abstract class HangingEntity extends BlockAttachedEntity {
    private static final EntityDataAccessor<Direction> DATA_DIRECTION = SynchedEntityData.defineId(HangingEntity.class, EntityDataSerializers.DIRECTION);
    private static final Direction DEFAULT_DIRECTION = Direction.SOUTH;

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    protected HangingEntity(EntityType<? extends HangingEntity> type, Level level, BlockPos pos) {
        this(type, level);
        this.pos = pos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DIRECTION, DEFAULT_DIRECTION);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_DIRECTION)) {
            this.setDirection(this.getDirection());
        }
    }

    @Override
    public Direction getDirection() {
        return this.entityData.get(DATA_DIRECTION);
    }

    protected void setDirectionRaw(Direction direction) {
        this.entityData.set(DATA_DIRECTION, direction);
    }

    public void setDirection(Direction facingDirection) {
        Objects.requireNonNull(facingDirection);
        Validate.isTrue(facingDirection.getAxis().isHorizontal());
        this.setDirectionRaw(facingDirection);
        this.setYRot(facingDirection.get2DDataValue() * 90);
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.getDirection() != null) {
            AABB aabb = this.calculateBoundingBox(this.pos, this.getDirection());
            Vec3 center = aabb.getCenter();
            this.setPosRaw(center.x, center.y, center.z);
            this.setBoundingBox(aabb);
        }
    }

    protected abstract AABB calculateBoundingBox(BlockPos pos, Direction direction);

    @Override
    public boolean survives() {
        if (this.hasLevelCollision(this.getPopBox())) {
            return false;
        } else {
            boolean flag = BlockPos.betweenClosedStream(this.calculateSupportBox()).allMatch(blockPos -> {
                BlockState blockState = this.level().getBlockState(blockPos);
                return blockState.isSolid() || DiodeBlock.isDiode(blockState);
            });
            return flag && this.canCoexist(false);
        }
    }

    protected AABB calculateSupportBox() {
        return this.getBoundingBox().move(this.getDirection().step().mul(-0.5F)).deflate(1.0E-7);
    }

    protected boolean canCoexist(boolean ignoreType) {
        Predicate<HangingEntity> predicate = hangingEntity -> {
            boolean flag = !ignoreType && hangingEntity.getType() == this.getType();
            boolean flag1 = hangingEntity.getDirection() == this.getDirection();
            return hangingEntity != this && (flag || flag1);
        };
        return !this.level().hasEntities(EntityTypeTest.forClass(HangingEntity.class), this.getPopBox(), predicate);
    }

    protected boolean hasLevelCollision(AABB aabb) {
        Level level = this.level();
        return !level.noBlockCollision(this, aabb) || !level.noBorderCollision(this, aabb);
    }

    protected AABB getPopBox() {
        return this.getBoundingBox();
    }

    public abstract void playPlacementSound();

    @Override
    public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack, float yOffset) {
        ItemEntity itemEntity = new ItemEntity(
            this.level(),
            this.getX() + this.getDirection().getStepX() * 0.15F,
            this.getY() + yOffset,
            this.getZ() + this.getDirection().getStepZ() * 0.15F,
            stack
        );
        itemEntity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itemEntity);
        return itemEntity;
    }

    @Override
    public float rotate(Rotation transformRotation) {
        Direction direction = this.getDirection();
        if (direction.getAxis() != Direction.Axis.Y) {
            switch (transformRotation) {
                case CLOCKWISE_180:
                    direction = direction.getOpposite();
                    break;
                case COUNTERCLOCKWISE_90:
                    direction = direction.getCounterClockWise();
                    break;
                case CLOCKWISE_90:
                    direction = direction.getClockWise();
            }

            this.setDirection(direction);
        }

        float f = Mth.wrapDegrees(this.getYRot());

        return switch (transformRotation) {
            case CLOCKWISE_180 -> f + 180.0F;
            case COUNTERCLOCKWISE_90 -> f + 90.0F;
            case CLOCKWISE_90 -> f + 270.0F;
            default -> f;
        };
    }

    @Override
    public float mirror(Mirror transformMirror) {
        return this.rotate(transformMirror.getRotation(this.getDirection()));
    }
}
