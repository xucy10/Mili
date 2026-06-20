package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OreVeinifier {
    private static final float VEININESS_THRESHOLD = 0.4F;
    private static final int EDGE_ROUNDOFF_BEGIN = 20;
    private static final double MAX_EDGE_ROUNDOFF = 0.2;
    private static final float VEIN_SOLIDNESS = 0.7F;
    private static final float MIN_RICHNESS = 0.1F;
    private static final float MAX_RICHNESS = 0.3F;
    private static final float MAX_RICHNESS_THRESHOLD = 0.6F;
    private static final float CHANCE_OF_RAW_ORE_BLOCK = 0.02F;
    private static final float SKIP_ORE_IF_GAP_NOISE_IS_BELOW = -0.3F;

    private OreVeinifier() {
    }

    protected static NoiseChunk.BlockStateFiller create(
        DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap, PositionalRandomFactory random
    ) {
        BlockState blockState = SharedConstants.DEBUG_ORE_VEINS ? Blocks.AIR.defaultBlockState() : null;
        return context -> {
            double d = veinToggle.compute(context);
            int i = context.blockY();
            OreVeinifier.VeinType veinType = d > 0.0 ? OreVeinifier.VeinType.COPPER : OreVeinifier.VeinType.IRON;
            double abs = Math.abs(d);
            int i1 = veinType.maxY - i;
            int i2 = i - veinType.minY;
            if (i2 >= 0 && i1 >= 0) {
                int min = Math.min(i1, i2);
                double d1 = Mth.clampedMap((double)min, 0.0, 20.0, -0.2, 0.0);
                if (abs + d1 < 0.4F) {
                    return blockState;
                } else {
                    RandomSource randomSource = random.at(context.blockX(), i, context.blockZ());
                    if (randomSource.nextFloat() > 0.7F) {
                        return blockState;
                    } else if (veinRidged.compute(context) >= 0.0) {
                        return blockState;
                    } else {
                        double d2 = Mth.clampedMap(abs, 0.4F, 0.6F, 0.1F, 0.3F);
                        if (randomSource.nextFloat() < d2 && veinGap.compute(context) > -0.3F) {
                            return randomSource.nextFloat() < 0.02F ? veinType.rawOreBlock : veinType.ore;
                        } else {
                            return SharedConstants.DEBUG_ORE_VEINS ? Blocks.OAK_BUTTON.defaultBlockState() : veinType.filler;
                        }
                    }
                }
            } else {
                return blockState;
            }
        };
    }

    protected static enum VeinType {
        COPPER(Blocks.COPPER_ORE.defaultBlockState(), Blocks.RAW_COPPER_BLOCK.defaultBlockState(), Blocks.GRANITE.defaultBlockState(), 0, 50),
        IRON(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(), Blocks.TUFF.defaultBlockState(), -60, -8);

        final BlockState ore;
        final BlockState rawOreBlock;
        final BlockState filler;
        protected final int minY;
        protected final int maxY;

        private VeinType(final BlockState ore, final BlockState rawOreBlock, final BlockState filler, final int minY, final int maxY) {
            this.ore = ore;
            this.rawOreBlock = rawOreBlock;
            this.filler = filler;
            this.minY = minY;
            this.maxY = maxY;
        }
    }
}
