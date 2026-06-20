package net.minecraft.world.level.block.piston;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonMovingBlockEntity extends BlockEntity {
    private static final int TICKS_TO_EXTEND = 2;
    private static final double PUSH_OFFSET = 0.01;
    public static final double TICK_MOVEMENT = 0.51;
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
    private static final float DEFAULT_PROGRESS = 0.0F;
    private static final boolean DEFAULT_EXTENDING = false;
    private static final boolean DEFAULT_SOURCE = false;
    private BlockState movedState = DEFAULT_BLOCK_STATE;
    private Direction direction = Direction.DOWN; // Paper - default to first value to avoid NPE
    private boolean extending = false;
    private boolean isSourcePiston = false;
    private static final ThreadLocal<Direction> NOCLIP = ThreadLocal.withInitial(() -> null);
    private float progress = 0.0F;
    private float progressO = 0.0F;
    private long lastTicked = Long.MIN_VALUE; // Folia - region threading
    private int deathTicks;

    // Folia start - region threading
    @Override
    public void updateTicks(long fromTickOffset, long fromRedstoneTimeOffset) {
        super.updateTicks(fromTickOffset, fromRedstoneTimeOffset);
        if (this.lastTicked != Long.MIN_VALUE) {
            this.lastTicked += fromRedstoneTimeOffset;
        }
    }
    // Folia end - region threading

    public PistonMovingBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.PISTON, pos, blockState);
    }

    public PistonMovingBlockEntity(BlockPos pos, BlockState blockState, BlockState movedState, Direction direction, boolean extending, boolean isSourcePiston) {
        this(pos, blockState);
        this.movedState = movedState;
        this.direction = direction;
        this.extending = extending;
        this.isSourcePiston = isSourcePiston;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public boolean isExtending() {
        return this.extending;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public boolean isSourcePiston() {
        return this.isSourcePiston;
    }

    public float getProgress(float partialTick) {
        if (partialTick > 1.0F) {
            partialTick = 1.0F;
        }

        return Mth.lerp(partialTick, this.progressO, this.progress);
    }

    public float getXOff(float partialTick) {
        return this.direction.getStepX() * this.getExtendedProgress(this.getProgress(partialTick));
    }

    public float getYOff(float partialTick) {
        return this.direction.getStepY() * this.getExtendedProgress(this.getProgress(partialTick));
    }

    public float getZOff(float partialTick) {
        return this.direction.getStepZ() * this.getExtendedProgress(this.getProgress(partialTick));
    }

    private float getExtendedProgress(float progress) {
        return this.extending ? progress - 1.0F : 1.0F - progress;
    }

    private BlockState getCollisionRelatedBlockState() {
        return !this.isExtending() && this.isSourcePiston() && this.movedState.getBlock() instanceof PistonBaseBlock
            ? Blocks.PISTON_HEAD
                .defaultBlockState()
                .setValue(PistonHeadBlock.SHORT, this.progress > 0.25F)
                .setValue(PistonHeadBlock.TYPE, this.movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT)
                .setValue(PistonHeadBlock.FACING, this.movedState.getValue(PistonBaseBlock.FACING))
            : this.movedState;
    }

    private static void moveCollidedEntities(Level level, BlockPos pos, float partialTick, PistonMovingBlockEntity piston) {
        Direction movementDirection = piston.getMovementDirection();
        double d = partialTick - piston.progress;
        VoxelShape collisionShape = piston.getCollisionRelatedBlockState().getCollisionShape(level, pos);
        if (!collisionShape.isEmpty()) {
            AABB aabb = moveByPositionAndProgress(pos, collisionShape.bounds(), piston);
            List<Entity> entities = level.getEntities(null, PistonMath.getMovementArea(aabb, movementDirection, d).minmax(aabb));
            if (!entities.isEmpty()) {
                List<AABB> list = collisionShape.toAabbs();
                boolean isSlimeBlock = piston.movedState.is(Blocks.SLIME_BLOCK);
                Iterator var12 = entities.iterator();

                while (true) {
                    Entity entity;
                    while (true) {
                        if (!var12.hasNext()) {
                            return;
                        }

                        entity = (Entity)var12.next();
                        if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                            if (!isSlimeBlock) {
                                break;
                            }

                            if (!(entity instanceof ServerPlayer)) {
                                Vec3 deltaMovement = entity.getDeltaMovement();
                                double d1 = deltaMovement.x;
                                double d2 = deltaMovement.y;
                                double d3 = deltaMovement.z;
                                switch (movementDirection.getAxis()) {
                                    case X:
                                        d1 = movementDirection.getStepX();
                                        break;
                                    case Y:
                                        d2 = movementDirection.getStepY();
                                        break;
                                    case Z:
                                        d3 = movementDirection.getStepZ();
                                }

                                entity.setDeltaMovement(d1, d2, d3);
                                // Paper - EAR items stuck in slime pushed by a piston
                                entity.activatedTick = Math.max(entity.activatedTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 10); // Folia - region threading
                                entity.activatedImmunityTick = Math.max(entity.activatedImmunityTick, io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + 10); // Folia - region threading
                                // Paper end
                                break;
                            }
                        }
                    }

                    double d4 = 0.0;

                    for (AABB aabb1 : list) {
                        AABB movementArea = PistonMath.getMovementArea(moveByPositionAndProgress(pos, aabb1, piston), movementDirection, d);
                        AABB boundingBox = entity.getBoundingBox();
                        if (movementArea.intersects(boundingBox)) {
                            d4 = Math.max(d4, getMovement(movementArea, movementDirection, boundingBox));
                            if (d4 >= d) {
                                break;
                            }
                        }
                    }

                    if (!(d4 <= 0.0)) {
                        d4 = Math.min(d4, d) + 0.01;
                        moveEntityByPiston(movementDirection, entity, d4, movementDirection);
                        if (!piston.extending && piston.isSourcePiston) {
                            fixEntityWithinPistonBase(pos, entity, movementDirection, d);
                        }
                    }
                }
            }
        }
    }

    private static void moveEntityByPiston(Direction noClipDirection, Entity entity, double progress, Direction direction) {
        NOCLIP.set(noClipDirection);
        Vec3 vec3 = entity.position();
        entity.move(MoverType.PISTON, new Vec3(progress * direction.getStepX(), progress * direction.getStepY(), progress * direction.getStepZ()));
        entity.applyEffectsFromBlocks(vec3, entity.position());
        entity.removeLatestMovementRecording();
        NOCLIP.set(null);
    }

    private static void moveStuckEntities(Level level, BlockPos pos, float partialTick, PistonMovingBlockEntity piston) {
        if (piston.isStickyForEntities()) {
            Direction movementDirection = piston.getMovementDirection();
            if (movementDirection.getAxis().isHorizontal()) {
                double d = piston.movedState.getCollisionShape(level, pos).max(Direction.Axis.Y);
                AABB aabb = moveByPositionAndProgress(pos, new AABB(0.0, d, 0.0, 1.0, 1.5000010000000001, 1.0), piston);
                double d1 = partialTick - piston.progress;

                for (Entity entity : level.getEntities((Entity)null, aabb, collidedEntity -> matchesStickyCritera(aabb, collidedEntity, pos))) {
                    moveEntityByPiston(movementDirection, entity, d1, movementDirection);
                }
            }
        }
    }

    private static boolean matchesStickyCritera(AABB box, Entity entity, BlockPos pos) {
        return entity.getPistonPushReaction() == PushReaction.NORMAL
            && entity.onGround()
            && (entity.isSupportedBy(pos) || entity.getX() >= box.minX && entity.getX() <= box.maxX && entity.getZ() >= box.minZ && entity.getZ() <= box.maxZ);
    }

    private boolean isStickyForEntities() {
        return this.movedState.is(Blocks.HONEY_BLOCK);
    }

    public Direction getMovementDirection() {
        return this.extending ? this.direction : this.direction.getOpposite();
    }

    private static double getMovement(AABB headShape, Direction direction, AABB facing) {
        switch (direction) {
            case EAST:
                return headShape.maxX - facing.minX;
            case WEST:
                return facing.maxX - headShape.minX;
            case UP:
            default:
                return headShape.maxY - facing.minY;
            case DOWN:
                return facing.maxY - headShape.minY;
            case SOUTH:
                return headShape.maxZ - facing.minZ;
            case NORTH:
                return facing.maxZ - headShape.minZ;
        }
    }

    private static AABB moveByPositionAndProgress(BlockPos pos, AABB aabb, PistonMovingBlockEntity pistonMovingBlockEntity) {
        double d = pistonMovingBlockEntity.getExtendedProgress(pistonMovingBlockEntity.progress);
        return aabb.move(
            pos.getX() + d * pistonMovingBlockEntity.direction.getStepX(),
            pos.getY() + d * pistonMovingBlockEntity.direction.getStepY(),
            pos.getZ() + d * pistonMovingBlockEntity.direction.getStepZ()
        );
    }

    private static void fixEntityWithinPistonBase(BlockPos pos, Entity entity, Direction dir, double progress) {
        AABB boundingBox = entity.getBoundingBox();
        AABB aabb = Shapes.block().bounds().move(pos);
        if (boundingBox.intersects(aabb)) {
            Direction opposite = dir.getOpposite();
            double d = getMovement(aabb, opposite, boundingBox) + 0.01;
            double d1 = getMovement(aabb, opposite, boundingBox.intersect(aabb)) + 0.01;
            if (Math.abs(d - d1) < 0.01) {
                d = Math.min(d, progress) + 0.01;
                moveEntityByPiston(dir, entity, d, opposite);
            }
        }
    }

    public BlockState getMovedState() {
        return this.movedState;
    }

    public void finalTick() {
        if (this.level != null && (this.progressO < 1.0F || this.level.isClientSide())) {
            this.progress = 1.0F;
            this.progressO = this.progress;
            this.level.removeBlockEntity(this.worldPosition);
            this.setRemoved();
            if (this.level.getBlockState(this.worldPosition).is(Blocks.MOVING_PISTON)) {
                BlockState blockState;
                if (this.isSourcePiston) {
                    blockState = Blocks.AIR.defaultBlockState();
                } else {
                    blockState = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
                }

                this.level.setBlock(this.worldPosition, blockState, Block.UPDATE_ALL);
                this.level
                    .neighborChanged(
                        this.worldPosition, blockState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(this.level, this.getPushDirection(), null)
                    );
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.finalTick();
    }

    public Direction getPushDirection() {
        return this.extending ? this.direction : this.direction.getOpposite();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity blockEntity) {
        blockEntity.lastTicked = level.getRedstoneGameTime(); // Folia - region threading
        blockEntity.progressO = blockEntity.progress;
        if (blockEntity.progressO >= 1.0F) {
            if (level.isClientSide() && blockEntity.deathTicks < 5) {
                blockEntity.deathTicks++;
            } else {
                level.removeBlockEntity(pos);
                blockEntity.setRemoved();
                if (level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
                    BlockState blockState = Block.updateFromNeighbourShapes(blockEntity.movedState, level, pos);
                    if (blockState.isAir()) {
                        level.setBlock(pos, blockEntity.movedState, Block.UPDATE_NONE | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE | (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowPistonDuplication ? 0 : Block.UPDATE_CLIENTS)); // Paper - fix a variety of piston desync dupes; force notify (flag 2), it's possible the set type by the piston block (which doesn't notify) set this block to air
                        Block.updateOrDestroy(blockEntity.movedState, blockState, level, pos, Block.UPDATE_ALL);
                    } else {
                        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED) && blockState.getValue(BlockStateProperties.WATERLOGGED)) {
                            blockState = blockState.setValue(BlockStateProperties.WATERLOGGED, false);
                        }

                        level.setBlock(pos, blockState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
                        level.neighborChanged(
                            pos, blockState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(level, blockEntity.getPushDirection(), null)
                        );
                    }
                }
            }
        } else {
            float f = blockEntity.progress + 0.5F;
            moveCollidedEntities(level, pos, f, blockEntity);
            moveStuckEntities(level, pos, f, blockEntity);
            blockEntity.progress = f;
            if (blockEntity.progress >= 1.0F) {
                blockEntity.progress = 1.0F;
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.movedState = input.read("blockState", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE);
        this.direction = input.read("facing", Direction.LEGACY_ID_CODEC).orElse(Direction.DOWN);
        this.progress = input.getFloatOr("progress", 0.0F);
        this.progressO = this.progress;
        this.extending = input.getBooleanOr("extending", false);
        this.isSourcePiston = input.getBooleanOr("source", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("blockState", BlockState.CODEC, this.movedState);
        output.store("facing", Direction.LEGACY_ID_CODEC, this.direction);
        output.putFloat("progress", this.progressO);
        output.putBoolean("extending", this.extending);
        output.putBoolean("source", this.isSourcePiston);
    }

    public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
        VoxelShape collisionShape;
        if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
            collisionShape = this.movedState.setValue(PistonBaseBlock.EXTENDED, true).getCollisionShape(level, pos);
        } else {
            collisionShape = Shapes.empty();
        }

        Direction direction = NOCLIP.get();
        if (this.progress < 1.0 && direction == this.getMovementDirection()) {
            return collisionShape;
        } else {
            BlockState blockState;
            if (this.isSourcePiston()) {
                blockState = Blocks.PISTON_HEAD
                    .defaultBlockState()
                    .setValue(PistonHeadBlock.FACING, this.direction)
                    .setValue(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
            } else {
                blockState = this.movedState;
            }

            float extendedProgress = this.getExtendedProgress(this.progress);
            double d = this.direction.getStepX() * extendedProgress;
            double d1 = this.direction.getStepY() * extendedProgress;
            double d2 = this.direction.getStepZ() * extendedProgress;
            return Shapes.or(collisionShape, blockState.getCollisionShape(level, pos).move(d, d1, d2));
        }
    }

    public long getLastTicked() {
        return this.lastTicked;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.holderLookup(Registries.BLOCK).get(this.movedState.getBlock().builtInRegistryHolder().key()).isEmpty()) {
            this.movedState = Blocks.AIR.defaultBlockState();
        }
    }
}
