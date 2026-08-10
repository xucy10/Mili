package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SporeBlossomBlock extends Block {
    public static final MapCodec<SporeBlossomBlock> CODEC = simpleCodec(SporeBlossomBlock::new);
    private static final VoxelShape SHAPE = Block.column(12.0, 13.0, 16.0);
    private static final int ADD_PARTICLE_ATTEMPTS = 14;
    private static final int PARTICLE_XZ_RADIUS = 10;
    private static final int PARTICLE_Y_MAX = 10;

    @Override
    public MapCodec<SporeBlossomBlock> codec() {
        return CODEC;
    }

    public SporeBlossomBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN) && !level.isWaterAt(pos);
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
        return direction == Direction.UP && !this.canSurvive(state, level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        double d = x + random.nextDouble();
        double d1 = y + 0.7;
        double d2 = z + random.nextDouble();
        level.addParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, d, d1, d2, 0.0, 0.0, 0.0);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 14; i++) {
            mutableBlockPos.set(x + Mth.nextInt(random, -10, 10), y - random.nextInt(10), z + Mth.nextInt(random, -10, 10));
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (!blockState.isCollisionShapeFullBlock(level, mutableBlockPos)) {
                level.addParticle(
                    ParticleTypes.SPORE_BLOSSOM_AIR,
                    mutableBlockPos.getX() + random.nextDouble(),
                    mutableBlockPos.getY() + random.nextDouble(),
                    mutableBlockPos.getZ() + random.nextDouble(),
                    0.0,
                    0.0,
                    0.0
                );
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
