package net.minecraft.world.level.chunk;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

public interface LightChunkGetter {
    @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ);

    default void onLightUpdate(LightLayer lightLayer, SectionPos pos) {
    }

    BlockGetter getLevel();
}
