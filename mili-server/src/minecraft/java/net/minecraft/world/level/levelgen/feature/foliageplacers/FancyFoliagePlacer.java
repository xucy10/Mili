package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class FancyFoliagePlacer extends BlobFoliagePlacer {
    public static final MapCodec<FancyFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> blobParts(instance).apply(instance, FancyFoliagePlacer::new)
    );

    public FancyFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset, height);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.FANCY_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius,
        int offset
    ) {
        for (int i = offset; i >= offset - foliageHeight; i--) {
            int i1 = foliageRadius + (i != offset && i != offset - foliageHeight ? 1 : 0);
            this.placeLeavesRow(level, blockSetter, random, config, attachment.pos(), i1, i, attachment.doubleTrunk());
        }
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return Mth.square(localX + 0.5F) + Mth.square(localZ + 0.5F) > range * range;
    }
}
