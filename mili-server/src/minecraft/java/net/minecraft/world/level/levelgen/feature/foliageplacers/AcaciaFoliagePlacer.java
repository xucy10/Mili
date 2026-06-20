package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class AcaciaFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<AcaciaFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance).apply(instance, AcaciaFoliagePlacer::new)
    );

    public AcaciaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.ACACIA_FOLIAGE_PLACER;
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
        boolean doubleTrunk = attachment.doubleTrunk();
        BlockPos blockPos = attachment.pos().above(offset);
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + attachment.radiusOffset(), -1 - foliageHeight, doubleTrunk);
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius - 1, -foliageHeight, doubleTrunk);
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + attachment.radiusOffset() - 1, 0, doubleTrunk);
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localY == 0 ? (localX > 1 || localZ > 1) && localX != 0 && localZ != 0 : localX == range && localZ == range && range > 0;
    }
}
