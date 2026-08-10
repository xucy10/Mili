package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class RandomSpreadFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<RandomSpreadFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(
                instance.group(
                    IntProvider.codec(1, 512).fieldOf("foliage_height").forGetter(randomSpreadFoliagePlacer -> randomSpreadFoliagePlacer.foliageHeight),
                    Codec.intRange(0, 256)
                        .fieldOf("leaf_placement_attempts")
                        .forGetter(randomSpreadFoliagePlacer -> randomSpreadFoliagePlacer.leafPlacementAttempts)
                )
            )
            .apply(instance, RandomSpreadFoliagePlacer::new)
    );
    private final IntProvider foliageHeight;
    private final int leafPlacementAttempts;

    public RandomSpreadFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider foliageHeight, int leafPlacementAttempts) {
        super(radius, offset);
        this.foliageHeight = foliageHeight;
        this.leafPlacementAttempts = leafPlacementAttempts;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.RANDOM_SPREAD_FOLIAGE_PLACER;
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
        BlockPos blockPos = attachment.pos();
        BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

        for (int i = 0; i < this.leafPlacementAttempts; i++) {
            mutableBlockPos.setWithOffset(
                blockPos,
                random.nextInt(foliageRadius) - random.nextInt(foliageRadius),
                random.nextInt(foliageHeight) - random.nextInt(foliageHeight),
                random.nextInt(foliageRadius) - random.nextInt(foliageRadius)
            );
            tryPlaceLeaf(level, blockSetter, random, config, mutableBlockPos);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.foliageHeight.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return false;
    }
}
