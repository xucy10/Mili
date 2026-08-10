package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LevelLightEngine;

public interface BlockAndTintGetter extends BlockGetter {
    float getShade(Direction direction, boolean shade);

    LevelLightEngine getLightEngine();

    int getBlockTint(BlockPos pos, ColorResolver colorResolver);

    default int getBrightness(LightLayer lightLayer, BlockPos pos) {
        return this.getLightEngine().getLayerListener(lightLayer).getLightValue(pos);
    }

    default int getRawBrightness(BlockPos pos, int amount) {
        return this.getLightEngine().getRawBrightness(pos, amount);
    }

    default boolean canSeeSky(BlockPos pos) {
        return this.getBrightness(LightLayer.SKY, pos) >= 15;
    }
}
