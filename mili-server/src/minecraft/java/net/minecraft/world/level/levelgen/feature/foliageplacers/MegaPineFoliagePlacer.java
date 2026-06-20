package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaPineFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaPineFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(IntProvider.codec(0, 24).fieldOf("crown_height").forGetter(megaPineFoliagePlacer -> megaPineFoliagePlacer.crownHeight))
            .apply(instance, MegaPineFoliagePlacer::new)
    );
    private final IntProvider crownHeight;

    public MegaPineFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider crownHeight) {
        super(radius, offset);
        this.crownHeight = crownHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_PINE_FOLIAGE_PLACER;
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
        int i = 0;

        for (int i1 = blockPos.getY() - foliageHeight + offset; i1 <= blockPos.getY() + offset; i1++) {
            int i2 = blockPos.getY() - i1;
            int i3 = foliageRadius + attachment.radiusOffset() + Mth.floor((float)i2 / foliageHeight * 3.5F);
            int i4;
            if (i2 > 0 && i3 == i && (i1 & 1) == 0) {
                i4 = i3 + 1;
            } else {
                i4 = i3;
            }

            this.placeLeavesRow(level, blockSetter, random, config, new BlockPos(blockPos.getX(), i1, blockPos.getZ()), i4, 0, attachment.doubleTrunk());
            i = i3;
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.crownHeight.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return localX + localZ >= 7 || localX * localX + localZ * localZ > range * range;
    }
}
