package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class CauldronBlock extends AbstractCauldronBlock {
    public static final MapCodec<CauldronBlock> CODEC = simpleCodec(CauldronBlock::new);
    private static final float RAIN_FILL_CHANCE = 0.05F;
    private static final float POWDER_SNOW_FILL_CHANCE = 0.1F;

    @Override
    public MapCodec<CauldronBlock> codec() {
        return CODEC;
    }

    public CauldronBlock(BlockBehaviour.Properties properties) {
        super(properties, CauldronInteraction.EMPTY);
    }

    @Override
    public boolean isFull(BlockState state) {
        return false;
    }

    protected static boolean shouldHandlePrecipitation(Level level, Biome.Precipitation precipitation) {
        return precipitation == Biome.Precipitation.RAIN
            ? level.getRandom().nextFloat() < 0.05F
            : precipitation == Biome.Precipitation.SNOW && level.getRandom().nextFloat() < 0.1F;
    }

    @Override
    public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
        if (shouldHandlePrecipitation(level, precipitation)) {
            if (precipitation == Biome.Precipitation.RAIN) {
                // Paper start - Call CauldronLevelChangeEvent
                if (!LayeredCauldronBlock.changeLevel(level, pos, Blocks.WATER_CAULDRON.defaultBlockState(), null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, false)) { // avoid duplicate game event
                    return;
                }
                // Paper end - Call CauldronLevelChangeEvent
                level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState());
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
            } else if (precipitation == Biome.Precipitation.SNOW) {
                // Paper start - Call CauldronLevelChangeEvent
                if (!LayeredCauldronBlock.changeLevel(level, pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState(), null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, false)) { // avoid duplicate game event
                    return;
                }
                // Paper end - Call CauldronLevelChangeEvent
                level.setBlockAndUpdate(pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
            }
        }
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return true;
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid) {
        if (fluid == Fluids.WATER) {
            BlockState blockState = Blocks.WATER_CAULDRON.defaultBlockState();
            // Paper start - Call CauldronLevelChangeEvent; don't send level event or game event if cancelled
            if (!LayeredCauldronBlock.changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) { // CraftBukkit
                return;
            }
            // Paper end - Call CauldronLevelChangeEvent
            level.levelEvent(LevelEvent.SOUND_DRIP_WATER_INTO_CAULDRON, pos, 0);
        } else if (fluid == Fluids.LAVA) {
            BlockState blockState = Blocks.LAVA_CAULDRON.defaultBlockState();
            // Paper start - Call CauldronLevelChangeEvent; don't send level event or game event if cancelled
            if (!LayeredCauldronBlock.changeLevel(level, pos, blockState, null, org.bukkit.event.block.CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) { // CraftBukkit
                return;
            }
            // Paper end - Call CauldronLevelChangeEvent
            level.levelEvent(LevelEvent.SOUND_DRIP_LAVA_INTO_CAULDRON, pos, 0);
        }
    }
}
