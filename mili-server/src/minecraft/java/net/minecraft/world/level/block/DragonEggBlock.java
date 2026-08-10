package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DragonEggBlock extends FallingBlock {
    public static final MapCodec<DragonEggBlock> CODEC = simpleCodec(DragonEggBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

    @Override
    public MapCodec<DragonEggBlock> codec() {
        return CODEC;
    }

    public DragonEggBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        this.teleport(state, level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        this.teleport(state, level, pos);
    }

    private void teleport(BlockState state, Level level, BlockPos pos) {
        WorldBorder worldBorder = level.getWorldBorder();

        for (int i = 0; i < 1000; i++) {
            BlockPos blockPos = pos.offset(
                level.random.nextInt(16) - level.random.nextInt(16),
                level.random.nextInt(8) - level.random.nextInt(8),
                level.random.nextInt(16) - level.random.nextInt(16)
            );
            if (level.getBlockState(blockPos).isAir() && worldBorder.isWithinBounds(blockPos) && !level.isOutsideBuildHeight(blockPos)) {
                // CraftBukkit start
                org.bukkit.block.Block from = org.bukkit.craftbukkit.block.CraftBlock.at(level, pos);
                org.bukkit.block.Block to = org.bukkit.craftbukkit.block.CraftBlock.at(level, blockPos);
                org.bukkit.event.block.BlockFromToEvent event = new org.bukkit.event.block.BlockFromToEvent(from, to);
                if (!event.callEvent()) {
                    return;
                }

                blockPos = new BlockPos(event.getToBlock().getX(), event.getToBlock().getY(), event.getToBlock().getZ());
                // CraftBukkit end
                if (level.isClientSide()) {
                    for (int i1 = 0; i1 < 128; i1++) {
                        double randomDouble = level.random.nextDouble();
                        float f = (level.random.nextFloat() - 0.5F) * 0.2F;
                        float f1 = (level.random.nextFloat() - 0.5F) * 0.2F;
                        float f2 = (level.random.nextFloat() - 0.5F) * 0.2F;
                        double d = Mth.lerp(randomDouble, (double)blockPos.getX(), (double)pos.getX()) + (level.random.nextDouble() - 0.5) + 0.5;
                        double d1 = Mth.lerp(randomDouble, (double)blockPos.getY(), (double)pos.getY()) + level.random.nextDouble() - 0.5;
                        double d2 = Mth.lerp(randomDouble, (double)blockPos.getZ(), (double)pos.getZ()) + (level.random.nextDouble() - 0.5) + 0.5;
                        level.addParticle(ParticleTypes.PORTAL, d, d1, d2, f, f1, f2);
                    }
                } else {
                    level.setBlock(blockPos, state, Block.UPDATE_CLIENTS);
                    level.removeBlock(pos, false);
                }

                return;
            }
        }
    }

    @Override
    protected int getDelayAfterPlace() {
        return 5;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return -16777216;
    }
}
