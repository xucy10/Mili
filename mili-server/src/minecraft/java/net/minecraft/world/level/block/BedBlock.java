package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

public class BedBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BedBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(DyeColor.CODEC.fieldOf("color").forGetter(BedBlock::getColor), propertiesCodec()).apply(instance, BedBlock::new)
    );
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    private static final Map<Direction, VoxelShape> SHAPES = Util.make(() -> {
        VoxelShape voxelShape = Block.box(0.0, 0.0, 0.0, 3.0, 3.0, 3.0);
        VoxelShape voxelShape1 = Shapes.rotate(voxelShape, OctahedralGroup.BLOCK_ROT_Y_90);
        return Shapes.rotateHorizontal(Shapes.or(Block.column(16.0, 3.0, 9.0), voxelShape, voxelShape1));
    });
    private final DyeColor color;

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }

    public BedBlock(DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
    }

    public static @Nullable Direction getBedOrientation(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.getBlock() instanceof BedBlock ? blockState.getValue(FACING) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        } else {
            if (state.getValue(PART) != BedPart.HEAD) {
                pos = pos.relative(state.getValue(FACING));
                state = level.getBlockState(pos);
                if (!state.is(this)) {
                    return InteractionResult.CONSUME;
                }
            }

            BedRule bedRule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            if (false && bedRule.explodes()) { // CraftBukkit - moved world and biome check into Player
                bedRule.errorMessage().ifPresent(component -> player.displayClientMessage(component, true));
                level.removeBlock(pos, false);
                BlockPos blockPos = pos.relative(state.getValue(FACING).getOpposite());
                if (level.getBlockState(blockPos).is(this)) {
                    level.removeBlock(blockPos, false);
                }

                Vec3 center = pos.getCenter();
                level.explode(null, level.damageSources().badRespawnPointExplosion(center), null, center, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS_SERVER;
            } else if (state.getValue(OCCUPIED)) {
                if (bedRule.explodes()) return this.explodeBed(state, level, pos); // Paper - check explode first
                if (!this.kickVillagerOutOfBed(level, pos)) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
                }

                return InteractionResult.SUCCESS_SERVER;
            } else {
                // CraftBukkit start
                final BlockState finalBlockState = state;
                final BlockPos finalBlockPos = pos;
                // CraftBukkit end
                player.startSleepInBed(pos).ifLeft(bedSleepingProblem -> {
                    // Paper start - PlayerBedFailEnterEvent
                    if (false && bedSleepingProblem.message() != null) { // Moved down
                        player.displayClientMessage(bedSleepingProblem.message(), true);
                    }
                    if (bedSleepingProblem != null) {
                        io.papermc.paper.event.player.PlayerBedFailEnterEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedFailEnterEvent(player, finalBlockPos, bedSleepingProblem);
                        if (event.isCancelled()) {
                            return;
                        }
                        // Paper end - PlayerBedFailEnterEvent
                        // CraftBukkit start - handling bed explosion from below here
                        if (event.getWillExplode()) { // Paper - PlayerBedFailEnterEvent
                            this.explodeBed(finalBlockState, level, finalBlockPos);
                        }
                        // CraftBukkit end
                        // Paper start - PlayerBedFailEnterEvent
                        final net.kyori.adventure.text.Component message = event.getMessage();
                        if (message != null) {
                            player.displayClientMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message), true);
                        }
                    }
                    // Paper end - PlayerBedFailEnterEvent
                });
                return InteractionResult.SUCCESS_SERVER;
            }
        }
    }

    // CraftBukkit start - Copied from the above method
    private InteractionResult explodeBed(BlockState state, Level level, BlockPos pos) {
        org.bukkit.block.BlockState blockState = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos).getState(); // CraftBukkit - capture BlockState before remove block
        level.removeBlock(pos, false);
        BlockPos blockPos = pos.relative(state.getValue(FACING).getOpposite());
        if (level.getBlockState(blockPos).is(this)) {
            level.removeBlock(blockPos, false);
        }

        Vec3 center = pos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(center).causingBlockSnapshot(blockState), null, center, 5.0F, true, Level.ExplosionInteraction.BLOCK); // CraftBukkit - add state
        return InteractionResult.SUCCESS_SERVER;
    }
    // CraftBukkit end

    private boolean kickVillagerOutOfBed(Level level, BlockPos pos) {
        List<Villager> entitiesOfClass = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        if (entitiesOfClass.isEmpty()) {
            return false;
        } else {
            entitiesOfClass.get(0).stopSleeping();
            return true;
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance * 0.5);
    }

    @Override
    public void updateEntityMovementAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityMovementAfterFallOn(level, entity);
        } else {
            this.bounceUp(entity);
        }
    }

    private void bounceUp(Entity entity) {
        Vec3 deltaMovement = entity.getDeltaMovement();
        if (deltaMovement.y < 0.0) {
            double d = entity instanceof LivingEntity ? 1.0 : 0.8;
            entity.setDeltaMovement(deltaMovement.x, -deltaMovement.y * 0.66F * d, deltaMovement.z);
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTickAccess,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random
    ) {
        if (direction == getNeighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            return neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART)
                ? state.setValue(OCCUPIED, neighborState.getValue(OCCUPIED))
                : Blocks.AIR.defaultBlockState();
        } else {
            return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        }
    }

    private static Direction getNeighbourDirection(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            BedPart bedPart = state.getValue(PART);
            if (bedPart == BedPart.FOOT) {
                BlockPos blockPos = pos.relative(getNeighbourDirection(bedPart, state.getValue(FACING)));
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.is(this) && blockState.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, Block.getId(blockState));
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction horizontalDirection = context.getHorizontalDirection();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos blockPos = clickedPos.relative(horizontalDirection);
        Level level = context.getLevel();
        return level.getBlockState(blockPos).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(blockPos)
            ? this.defaultBlockState().setValue(FACING, horizontalDirection)
            : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(getConnectedDirection(state).getOpposite());
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction direction = state.getValue(FACING);
        return state.getValue(PART) == BedPart.HEAD ? direction.getOpposite() : direction;
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        BedPart bedPart = state.getValue(PART);
        return bedPart == BedPart.HEAD ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
    }

    private static boolean isBunkBed(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BedBlock;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, Direction direction, float yRot) {
        Direction clockWise = direction.getClockWise();
        Direction direction1 = clockWise.isFacingAngle(yRot) ? clockWise.getOpposite() : clockWise;
        if (isBunkBed(collisionGetter, pos)) {
            return findBunkBedStandUpPosition(entityType, collisionGetter, pos, direction, direction1);
        } else {
            int[][] ints = bedStandUpOffsets(direction, direction1);
            Optional<Vec3> optional = findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints, true);
            return optional.isPresent() ? optional : findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints, false);
        }
    }

    private static Optional<Vec3> findBunkBedStandUpPosition(
        EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, Direction stateFacing, Direction entityFacing
    ) {
        int[][] ints = bedSurroundStandUpOffsets(stateFacing, entityFacing);
        Optional<Vec3> optional = findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints, true);
        if (optional.isPresent()) {
            return optional;
        } else {
            BlockPos blockPos = pos.below();
            Optional<Vec3> optional1 = findStandUpPositionAtOffset(entityType, collisionGetter, blockPos, ints, true);
            if (optional1.isPresent()) {
                return optional1;
            } else {
                int[][] ints1 = bedAboveStandUpOffsets(stateFacing);
                Optional<Vec3> optional2 = findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints1, true);
                if (optional2.isPresent()) {
                    return optional2;
                } else {
                    Optional<Vec3> optional3 = findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints, false);
                    if (optional3.isPresent()) {
                        return optional3;
                    } else {
                        Optional<Vec3> optional4 = findStandUpPositionAtOffset(entityType, collisionGetter, blockPos, ints, false);
                        return optional4.isPresent() ? optional4 : findStandUpPositionAtOffset(entityType, collisionGetter, pos, ints1, false);
                    }
                }
            }
        }
    }

    private static Optional<Vec3> findStandUpPositionAtOffset(
        EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, int[][] offsets, boolean simulate
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int[] ints : offsets) {
            mutableBlockPos.set(pos.getX() + ints[0], pos.getY(), pos.getZ() + ints[1]);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(entityType, collisionGetter, mutableBlockPos, simulate);
            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BedBlockEntity(pos, state, this.color);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockPos blockPos = pos.relative(state.getValue(FACING));
            level.setBlock(blockPos, state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            // CraftBukkit start - SPIGOT-7315: Don't updated if we capture block states
            if (level.getCurrentWorldData().captureBlockStates) { // Folia - region threading
                return;
            }
            // CraftBukkit end
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    public DyeColor getColor() {
        return this.color;
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        BlockPos blockPos = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
        return Mth.getSeed(blockPos.getX(), pos.getY(), blockPos.getZ());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private static int[][] bedStandUpOffsets(Direction firstDir, Direction secondDir) {
        return ArrayUtils.addAll((int[][])bedSurroundStandUpOffsets(firstDir, secondDir), (int[][])bedAboveStandUpOffsets(firstDir));
    }

    private static int[][] bedSurroundStandUpOffsets(Direction firstDir, Direction secondDir) {
        return new int[][]{
            {secondDir.getStepX(), secondDir.getStepZ()},
            {secondDir.getStepX() - firstDir.getStepX(), secondDir.getStepZ() - firstDir.getStepZ()},
            {secondDir.getStepX() - firstDir.getStepX() * 2, secondDir.getStepZ() - firstDir.getStepZ() * 2},
            {-firstDir.getStepX() * 2, -firstDir.getStepZ() * 2},
            {-secondDir.getStepX() - firstDir.getStepX() * 2, -secondDir.getStepZ() - firstDir.getStepZ() * 2},
            {-secondDir.getStepX() - firstDir.getStepX(), -secondDir.getStepZ() - firstDir.getStepZ()},
            {-secondDir.getStepX(), -secondDir.getStepZ()},
            {-secondDir.getStepX() + firstDir.getStepX(), -secondDir.getStepZ() + firstDir.getStepZ()},
            {firstDir.getStepX(), firstDir.getStepZ()},
            {secondDir.getStepX() + firstDir.getStepX(), secondDir.getStepZ() + firstDir.getStepZ()}
        };
    }

    private static int[][] bedAboveStandUpOffsets(Direction dir) {
        return new int[][]{{0, 0}, {-dir.getStepX(), -dir.getStepZ()}};
    }
}
