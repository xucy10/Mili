package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class PineFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<PineFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(IntProvider.codec(0, 24).fieldOf("height").forGetter(pineFoliagePlacer -> pineFoliagePlacer.height))
            .apply(instance, PineFoliagePlacer::new)
    );
    private final IntProvider height;

    public PineFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.PINE_FOLIAGE_PLACER;
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
        int i = 0;

        for (int i1 = offset; i1 >= offset - foliageHeight; i1--) {
            this.placeLeavesRow(level, blockSetter, random, config, attachment.pos(), i, i1, attachment.doubleTrunk());
            if (i >= 1 && i1 == offset - foliageHeight + 1) {
                i--;
            } else if (i < foliageRadius + attachment.radiusOffset()) {
                i++;
            }
        }
    }

    @Override
    public int foliageRadius(RandomSource random, int radius) {
        return super.foliageRadius(random, radius) + random.nextInt(Math.max(radius + 1, 1));
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localX == range && localZ == range && range > 0;
    }
}
