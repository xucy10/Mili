package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class SpruceFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<SpruceFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(IntProvider.codec(0, 24).fieldOf("trunk_height").forGetter(spruceFoliagePlacer -> spruceFoliagePlacer.trunkHeight))
            .apply(instance, SpruceFoliagePlacer::new)
    );
    private final IntProvider trunkHeight;

    public SpruceFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider trunkHeight) {
        super(radius, offset);
        this.trunkHeight = trunkHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.SPRUCE_FOLIAGE_PLACER;
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
        int randomInt = random.nextInt(2);
        int i = 1;
        int i1 = 0;

        for (int i2 = offset; i2 >= -foliageHeight; i2--) {
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, randomInt, i2, attachment.doubleTrunk());
            if (randomInt >= i) {
                randomInt = i1;
                i1 = 1;
                i = Math.min(i + 1, foliageRadius + attachment.radiusOffset());
            } else {
                randomInt++;
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return Math.max(4, height - this.trunkHeight.sample(random));
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localX == range && localZ == range && range > 0;
    }
}
