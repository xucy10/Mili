package net.minecraft.world.level.levelgen.material;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.jspecify.annotations.Nullable;

public interface WorldGenMaterialRule {
    @Nullable BlockState apply(NoiseChunk chunk, int x, int y, int z);
}
