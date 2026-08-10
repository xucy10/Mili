package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WaterlilyBlock extends VegetationBlock {
    public static final MapCodec<WaterlilyBlock> CODEC = simpleCodec(WaterlilyBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 1.5);

    @Override
    public MapCodec<WaterlilyBlock> codec() {
        return CODEC;
    }

    protected WaterlilyBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        super.entityInside(state, level, pos, entity, effectApplier, pastEdges);
        if (level instanceof ServerLevel && entity instanceof AbstractBoat) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                return;
            }
            // CraftBukkit end
            level.destroyBlock(new BlockPos(pos), true, entity);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        FluidState fluidState1 = level.getFluidState(pos.above());
        return (fluidState.getType() == Fluids.WATER || state.getBlock() instanceof IceBlock) && fluidState1.getType() == Fluids.EMPTY;
    }
}
