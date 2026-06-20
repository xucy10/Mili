package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TargetBlock extends Block {
    public static final MapCodec<TargetBlock> CODEC = simpleCodec(TargetBlock::new);
    private static final IntegerProperty OUTPUT_POWER = BlockStateProperties.POWER;
    private static final int ACTIVATION_TICKS_ARROWS = 20;
    private static final int ACTIVATION_TICKS_OTHER = 8;

    @Override
    public MapCodec<TargetBlock> codec() {
        return CODEC;
    }

    public TargetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OUTPUT_POWER, 0));
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        int i = updateRedstoneOutput(level, state, hit, projectile);
        // Paper start - Add TargetHitEvent
    }
    private static void awardTargetHitCriteria(Projectile projectile, BlockHitResult hit, int i) {
        // Paper end - Add TargetHitEvent
        if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.TARGET_HIT);
            CriteriaTriggers.TARGET_BLOCK_HIT.trigger(serverPlayer, projectile, hit.getLocation(), i);
        }
    }

    private static int updateRedstoneOutput(LevelAccessor level, BlockState state, BlockHitResult hit, Entity projectile) {
        int redstoneStrength = getRedstoneStrength(hit, hit.getLocation());
        int i = projectile instanceof AbstractArrow ? 20 : 8;
        // Paper start - Add TargetHitEvent
        boolean shouldAward = false;
        if (projectile instanceof Projectile) {
            final org.bukkit.craftbukkit.block.CraftBlock craftBlock = org.bukkit.craftbukkit.block.CraftBlock.at(level, hit.getBlockPos());
            final org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(hit.getDirection());
            final io.papermc.paper.event.block.TargetHitEvent targetHitEvent = new io.papermc.paper.event.block.TargetHitEvent((org.bukkit.entity.Projectile) projectile.getBukkitEntity(), craftBlock, blockFace, redstoneStrength);
            if (targetHitEvent.callEvent()) {
                redstoneStrength = targetHitEvent.getSignalStrength();
                shouldAward = true;
            } else {
                return redstoneStrength;
            }
        }
        // Paper end - Add TargetHitEvent
        if (!level.getBlockTicks().hasScheduledTick(hit.getBlockPos(), state.getBlock())) {
            setOutputPower(level, state, redstoneStrength, hit.getBlockPos(), i);
        }

        // Paper start - Award Hit Criteria after Block Update
        if (shouldAward) {
            awardTargetHitCriteria((Projectile) projectile, hit, redstoneStrength);
        }
        // Paper end - Award Hit Criteria after Block Update

        return redstoneStrength;
    }

    private static int getRedstoneStrength(BlockHitResult hit, Vec3 hitLocation) {
        Direction direction = hit.getDirection();
        double abs = Math.abs(Mth.frac(hitLocation.x) - 0.5);
        double abs1 = Math.abs(Mth.frac(hitLocation.y) - 0.5);
        double abs2 = Math.abs(Mth.frac(hitLocation.z) - 0.5);
        Direction.Axis axis = direction.getAxis();
        double max;
        if (axis == Direction.Axis.Y) {
            max = Math.max(abs, abs2);
        } else if (axis == Direction.Axis.Z) {
            max = Math.max(abs, abs1);
        } else {
            max = Math.max(abs1, abs2);
        }

        return Math.max(1, Mth.ceil(15.0 * Mth.clamp((0.5 - max) / 0.5, 0.0, 1.0)));
    }

    private static void setOutputPower(LevelAccessor level, BlockState state, int power, BlockPos pos, int waitTime) {
        level.setBlock(pos, state.setValue(OUTPUT_POWER, power), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), waitTime);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OUTPUT_POWER) != 0) {
            level.setBlock(pos, state.setValue(OUTPUT_POWER, 0), Block.UPDATE_ALL);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(OUTPUT_POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT_POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            if (state.getValue(OUTPUT_POWER) > 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.setBlock(pos, state.setValue(OUTPUT_POWER, 0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
