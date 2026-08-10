package net.minecraft.world.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RemoveBlockGoal extends MoveToBlockGoal {
    private final Block blockToRemove;
    private final Mob removerMob;
    private int ticksSinceReachedGoal;
    private static final int WAIT_AFTER_BLOCK_FOUND = 20;

    public RemoveBlockGoal(Block blockToRemove, PathfinderMob removerMob, double speedModifier, int searchRange) {
        super(removerMob, speedModifier, 24, searchRange);
        this.blockToRemove = blockToRemove;
        this.removerMob = removerMob;
    }

    @Override
    public boolean canUse() {
        if (!getServerLevel(this.removerMob).getGameRules().get(GameRules.MOB_GRIEFING)) {
            return false;
        } else if (this.nextStartTick > 0) {
            this.nextStartTick--;
            return false;
        } else if (this.findNearestBlock()) {
            this.nextStartTick = reducedTickDelay(20);
            return true;
        } else {
            this.nextStartTick = this.nextStartTick(this.mob);
            return false;
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.removerMob.fallDistance = 1.0;
    }

    @Override
    public void start() {
        super.start();
        this.ticksSinceReachedGoal = 0;
    }

    public void playDestroyProgressSound(LevelAccessor level, BlockPos pos) {
    }

    public void playBreakSound(Level level, BlockPos pos) {
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.removerMob.level();
        BlockPos blockPos = this.removerMob.blockPosition();
        BlockPos posWithBlock = this.getPosWithBlock(blockPos, level);
        RandomSource random = this.removerMob.getRandom();
        if (this.isReachedTarget() && posWithBlock != null) {
            if (this.ticksSinceReachedGoal > 0) {
                Vec3 deltaMovement = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(deltaMovement.x, 0.3, deltaMovement.z);
                if (!level.isClientSide()) {
                    double d = 0.08;
                    ((ServerLevel)level)
                        .sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG)),
                            posWithBlock.getX() + 0.5,
                            posWithBlock.getY() + 0.7,
                            posWithBlock.getZ() + 0.5,
                            3,
                            (random.nextFloat() - 0.5) * 0.08,
                            (random.nextFloat() - 0.5) * 0.08,
                            (random.nextFloat() - 0.5) * 0.08,
                            0.15F
                        );
                }
            }

            if (this.ticksSinceReachedGoal % 2 == 0) {
                Vec3 deltaMovement = this.removerMob.getDeltaMovement();
                this.removerMob.setDeltaMovement(deltaMovement.x, -0.3, deltaMovement.z);
                if (this.ticksSinceReachedGoal % 6 == 0) {
                    this.playDestroyProgressSound(level, this.blockPos);
                }
            }

            if (this.ticksSinceReachedGoal > 60) {
                // CraftBukkit start - Step on eggs
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityInteractEvent(this.removerMob, org.bukkit.craftbukkit.block.CraftBlock.at(level, posWithBlock))) {
                    return;
                }
                // CraftBukkit end
                level.removeBlock(posWithBlock, false);
                if (!level.isClientSide()) {
                    for (int i = 0; i < 20; i++) {
                        double d = random.nextGaussian() * 0.02;
                        double d1 = random.nextGaussian() * 0.02;
                        double d2 = random.nextGaussian() * 0.02;
                        ((ServerLevel)level)
                            .sendParticles(ParticleTypes.POOF, posWithBlock.getX() + 0.5, posWithBlock.getY(), posWithBlock.getZ() + 0.5, 1, d, d1, d2, 0.15F);
                    }

                    this.playBreakSound(level, posWithBlock);
                }
            }

            this.ticksSinceReachedGoal++;
        }
    }

    private @Nullable BlockPos getPosWithBlock(BlockPos pos, BlockGetter level) {
        net.minecraft.world.level.block.state.BlockState block = level.getBlockStateIfLoaded(pos); // Paper - Prevent AI rules from loading chunks
        if (block == null) return null; // Paper - Prevent AI rules from loading chunks
        if (block.is(this.blockToRemove)) { // Paper - Prevent AI rules from loading chunks
            return pos;
        } else {
            BlockPos[] blockPoss = new BlockPos[]{pos.below(), pos.west(), pos.east(), pos.north(), pos.south(), pos.below().below()};

            for (BlockPos blockPos : blockPoss) {
                net.minecraft.world.level.block.state.BlockState block2 = level.getBlockStateIfLoaded(blockPos); // Paper - Prevent AI rules from loading chunks
                if (block2 != null && block2.is(this.blockToRemove)) { // Paper - Prevent AI rules from loading chunks
                    return blockPos;
                }
            }

            return null;
        }
    }

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4); // Paper - Prevent AI rules from loading chunks
        return chunk != null
            && chunk.getBlockState(pos).is(this.blockToRemove)
            && chunk.getBlockState(pos.above()).isAir()
            && chunk.getBlockState(pos.above(2)).isAir();
    }
}
