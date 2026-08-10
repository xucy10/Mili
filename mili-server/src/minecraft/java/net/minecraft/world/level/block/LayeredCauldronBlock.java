package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LayeredCauldronBlock extends AbstractCauldronBlock {
    public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Biome.Precipitation.CODEC.fieldOf("precipitation").forGetter(layeredCauldronBlock -> layeredCauldronBlock.precipitationType),
                CauldronInteraction.CODEC.fieldOf("interactions").forGetter(layeredCauldronBlock -> layeredCauldronBlock.interactions),
                propertiesCodec()
            )
            .apply(instance, LayeredCauldronBlock::new)
    );
    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0;
    private static final VoxelShape[] FILLED_SHAPES = Util.make(
        () -> Block.boxes(2, i -> Shapes.or(AbstractCauldronBlock.SHAPE, Block.column(12.0, 4.0, getPixelContentHeight(i + 1))))
    );
    private final Biome.Precipitation precipitationType;

    @Override
    public MapCodec<LayeredCauldronBlock> codec() {
        return CODEC;
    }

    public LayeredCauldronBlock(Biome.Precipitation precipitationType, CauldronInteraction.InteractionMap interactions, BlockBehaviour.Properties properties) {
        super(properties, interactions);
        this.precipitationType = precipitationType;
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    public boolean isFull(BlockState state) {
        return state.getValue(LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return fluid == Fluids.WATER && this.precipitationType == Biome.Precipitation.RAIN;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return getPixelContentHeight(state.getValue(LEVEL)) / 16.0;
    }

    private static double getPixelContentHeight(int level) {
        return 6.0 + level * 3.0;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return FILLED_SHAPES[state.getValue(LEVEL) - 1];
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean pastEdges) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (level instanceof ServerLevel serverLevel) {
            BlockPos blockPos = pos.immutable();
            effectApplier.runBefore(InsideBlockEffectType.EXTINGUISH, entity1 -> {
                if (entity1.isOnFire() && (entity instanceof net.minecraft.world.entity.player.Player || serverLevel.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)) && entity1.mayInteract(serverLevel, blockPos)) { // Paper - Fixes MC-248588
                    // Paper start - cauldron level change event
                    if (this.handleEntityOnFireInside(state, level, blockPos, entity1)) { // Paper - track entity
                        InsideBlockEffectType.EXTINGUISH.effect().affect(entity1, blockPos); // apply extinguishing if event was not cancelled.
                    }
                    // Paper end - cauldron level change event
                }
            });
        }

        // effectApplier.apply(InsideBlockEffectType.EXTINGUISH); // Paper - manually applied above - cauldron level change event - delay to not extinguish when event is cancelled
    }

    // CraftBukkit start
    private boolean handleEntityOnFireInside(BlockState state, Level level, BlockPos pos, @javax.annotation.Nullable Entity entity) {
        if (this.precipitationType == Biome.Precipitation.SNOW) {
            return lowerFillLevel(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LEVEL, state.getValue(LEVEL)), level, pos, entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.EXTINGUISH); // CraftBukkit
        } else {
            return lowerFillLevel(state, level, pos, entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.EXTINGUISH); // CraftBukkit
        }
    }

    public static void lowerFillLevel(BlockState state, Level level, BlockPos pos) {
        // Paper start
        lowerFillLevel(state, level, pos, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }
    public static boolean lowerFillLevel(BlockState state, Level level, BlockPos pos, @javax.annotation.Nullable Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason) {
        // Paper end
        int i = state.getValue(LEVEL) - 1;
        BlockState blockState = i == 0 ? Blocks.CAULDRON.defaultBlockState() : state.setValue(LEVEL, i);
        return changeLevel(level, pos, blockState, entity, reason); // Paper
    }
    // CraftBukkit start
    // Paper start - Call CauldronLevelChangeEvent
    public static boolean changeLevel(Level level, BlockPos pos, BlockState newBlock, @javax.annotation.Nullable Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason) { // Paper - entity is nullable
        return changeLevel(level, pos, newBlock, entity, reason, true);
    }

    public static boolean changeLevel(Level level, BlockPos pos, BlockState newBlock, @javax.annotation.Nullable Entity entity, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason reason, boolean sendGameEvent) { // Paper - entity is nullable
        // Paper end - Call CauldronLevelChangeEvent
        org.bukkit.craftbukkit.block.CraftBlockState newState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(level, pos);
        newState.setData(newBlock);

        org.bukkit.event.block.CauldronLevelChangeEvent event = new org.bukkit.event.block.CauldronLevelChangeEvent(
            org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
            (entity == null) ? null : entity.getBukkitEntity(), reason, newState
        );
        if (!event.callEvent()) {
            return false;
        }
        newState.place(Block.UPDATE_ALL);
        if (sendGameEvent) level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(newBlock)); // Paper - Call CauldronLevelChangeEvent
        return true;
    }
    // CraftBukkit end

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(level, precipitation) && state.getValue(LEVEL) != 3 && precipitation == this.precipitationType) {
            BlockState blockState = state.cycle(LEVEL);
            changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL); // CraftBukkit
        }
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {
        if (!this.isFull(state)) {
            BlockState blockState = state.setValue(LEVEL, state.getValue(LEVEL) + 1);
            // CraftBukkit start
            if (!changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) {
                return;
            }
            // CraftBukkit end
            level.levelEvent(LevelEvent.SOUND_DRIP_WATER_INTO_CAULDRON, pos, 0);
        }
    }
}
