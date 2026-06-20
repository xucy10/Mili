package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class CoralBlock extends Block {
    public static final MapCodec<Block> DEAD_CORAL_FIELD = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("dead");
    public static final MapCodec<CoralBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(DEAD_CORAL_FIELD.forGetter(coralBlock -> coralBlock.deadBlock), propertiesCodec()).apply(instance, CoralBlock::new)
    );
    private final Block deadBlock;

    public CoralBlock(Block deadBlock, BlockBehaviour.Properties properties) {
        super(properties);
        this.deadBlock = deadBlock;
    }

    @Override
    public MapCodec<CoralBlock> codec() {
        return CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.scanForWater(level, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(level, pos, this.deadBlock.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            level.setBlock(pos, this.deadBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
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
        if (!this.scanForWater(level, pos)) {
            scheduledTickAccess.scheduleTick(pos, this, 60 + random.nextInt(40));
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    protected boolean scanForWater(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            FluidState fluidState = level.getFluidState(pos.relative(direction));
            if (fluidState.is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!this.scanForWater(context.getLevel(), context.getClickedPos())) {
            context.getLevel().scheduleTick(context.getClickedPos(), this, 60 + context.getLevel().getRandom().nextInt(40));
        }

        return this.defaultBlockState();
    }
}
