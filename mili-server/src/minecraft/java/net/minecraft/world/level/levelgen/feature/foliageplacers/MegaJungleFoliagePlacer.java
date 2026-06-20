package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaJungleFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaJungleFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(Codec.intRange(0, 16).fieldOf("height").forGetter(megaJungleFoliagePlacer -> megaJungleFoliagePlacer.height))
            .apply(instance, MegaJungleFoliagePlacer::new)
    );
    protected final int height;

    public MegaJungleFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_JUNGLE_FOLIAGE_PLACER;
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
        int i = attachment.doubleTrunk() ? foliageHeight : 1 + random.nextInt(2);

        for (int i1 = offset; i1 >= offset - i; i1--) {
            int i2 = foliageRadius + attachment.radiusOffset() + 1 - i1;
            this.placeLeavesRow(level, blockSetter, random, config, attachment.pos(), i2, i1, attachment.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localX + localZ >= 7 || localX * localX + localZ * localZ > range * range;
    }
}
