package net.minecraft.world.level.levelgen.material;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.jspecify.annotations.Nullable;

public record MaterialRuleList(NoiseChunk.BlockStateFiller[] materialRuleList) implements NoiseChunk.BlockStateFiller {
    @Override
    public @Nullable BlockState calculate(DensityFunction.FunctionContext context) {
        // Leaf start - Reduce worldgen allocations
        // Avoid iterator allocation
        BlockState blockState = null;
        int length = this.materialRuleList.length;

        for (int i = 0; blockState == null && i < length; i++) {
            NoiseChunk.BlockStateFiller blockStateFiller = this.materialRuleList[i];
            blockState = blockStateFiller.calculate(context);
        }

        return blockState;
        // Leaf end - Reduce worldgen allocations
    }
}
