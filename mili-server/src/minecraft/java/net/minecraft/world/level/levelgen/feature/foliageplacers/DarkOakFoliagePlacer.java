package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class DarkOakFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<DarkOakFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance).apply(instance, DarkOakFoliagePlacer::new)
    );

    public DarkOakFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.DARK_OAK_FOLIAGE_PLACER;
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
        BlockPos blockPos = attachment.pos().above(offset);
        boolean doubleTrunk = attachment.doubleTrunk();
        if (doubleTrunk) {
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + 2, -1, doubleTrunk);
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + 3, 0, doubleTrunk);
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + 2, 1, doubleTrunk);
            if (random.nextBoolean()) {
                this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius, 2, doubleTrunk);
            }
        } else {
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + 2, -1, doubleTrunk);
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, foliageRadius + 1, 0, doubleTrunk);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 4;
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localY == 0 && large && (localX == -range || localX >= range) && (localZ == -range || localZ >= range)
            || super.shouldSkipLocationSigned(random, localX, localY, localZ, range, large);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localY == -1 && !large ? localX == range && localZ == range : localY == 1 && localX + localZ > range * 2 - 2;
    }
}
