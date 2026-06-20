package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ConcretePowderBlock extends FallingBlock {
    public static final MapCodec<ConcretePowderBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("concrete").forGetter(concretePowderBlock -> concretePowderBlock.concrete), propertiesCodec()
            )
            .apply(instance, ConcretePowderBlock::new)
    );
    private final Block concrete;

    @Override
    public MapCodec<ConcretePowderBlock> codec() {
        return CODEC;
    }

    public ConcretePowderBlock(Block concrete, BlockBehaviour.Properties properties) {
        super(properties);
        this.concrete = concrete;
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaceableState, FallingBlockEntity fallingBlock) {
        if (shouldSolidify(level, pos, replaceableState)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(level, pos, this.concrete.defaultBlockState(), Block.UPDATE_ALL); // CraftBukkit
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel(); // Paper
        BlockPos clickedPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(clickedPos);
        // CraftBukkit start
        if (!ConcretePowderBlock.shouldSolidify(level, clickedPos, blockState)) {
            return super.getStateForPlacement(context);
        }

        // TODO: An event factory call for methods like this
        org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState((net.minecraft.world.level.LevelAccessor) level, clickedPos);
        craftBlockState.setData(this.concrete.defaultBlockState());

        org.bukkit.event.block.BlockFormEvent event = new org.bukkit.event.block.BlockFormEvent(craftBlockState.getBlock(), craftBlockState);
        level.getServer().server.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return craftBlockState.getHandle();
        }

        return super.getStateForPlacement(context);
        // CraftBukkit end
    }

    private static boolean shouldSolidify(BlockGetter level, BlockPos pos, BlockState state) {
        return canSolidify(state) || touchesLiquid(level, pos);
    }

    private static boolean touchesLiquid(BlockGetter level, BlockPos pos) {
        boolean flag = false;
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (Direction direction : Direction.values()) {
            BlockState blockState = level.getBlockState(mutableBlockPos);
            if (direction != Direction.DOWN || canSolidify(blockState)) {
                mutableBlockPos.setWithOffset(pos, direction);
                blockState = level.getBlockState(mutableBlockPos);
                if (canSolidify(blockState) && !blockState.isFaceSturdy(level, pos, direction.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
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
        // CraftBukkit start
        if (ConcretePowderBlock.touchesLiquid(level, pos)) {
            // Suppress during worldgen
            if (!(level instanceof Level world1)) {
                return this.concrete.defaultBlockState();
            }
            org.bukkit.craftbukkit.block.CraftBlockState blockState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(world1, pos);
            blockState.setData(this.concrete.defaultBlockState());

            org.bukkit.event.block.BlockFormEvent event = new org.bukkit.event.block.BlockFormEvent(blockState.getBlock(), blockState);
            world1.getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }

        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        // CraftBukkit end
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getMapColor(level, pos).col;
    }
}
