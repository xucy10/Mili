package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class CherryFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<CherryFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(
                instance.group(
                    IntProvider.codec(4, 16).fieldOf("height").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.height),
                    Codec.floatRange(0.0F, 1.0F)
                        .fieldOf("wide_bottom_layer_hole_chance")
                        .forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.wideBottomLayerHoleChance),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("corner_hole_chance").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.wideBottomLayerHoleChance),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_chance").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.hangingLeavesChance),
                    Codec.floatRange(0.0F, 1.0F)
                        .fieldOf("hanging_leaves_extension_chance")
                        .forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.hangingLeavesExtensionChance)
                )
            )
            .apply(instance, CherryFoliagePlacer::new)
    );
    private final IntProvider height;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(
        IntProvider radius,
        IntProvider offset,
        IntProvider height,
        float wideBottomLayerHoleChance,
        float cornerHoleChance,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        super(radius, offset);
        this.height = height;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.CHERRY_FOLIAGE_PLACER;
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
        int i = foliageRadius + attachment.radiusOffset() - 1;
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, i - 2, foliageHeight - 3, doubleTrunk);
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, i - 1, foliageHeight - 4, doubleTrunk);

        for (int i1 = foliageHeight - 5; i1 >= 0; i1--) {
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, i, i1, doubleTrunk);
        }

        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockPos, i, -1, doubleTrunk, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockPos, i - 1, -2, doubleTrunk, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        if (localY == -1 && (localX == range || localZ == range) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        } else {
            boolean flag = localX == range && localZ == range;
            boolean flag1 = range > 2;
            return flag1
                ? flag || localX + localZ > range * 2 - 2 && random.nextFloat() < this.cornerHoleChance
                : flag && random.nextFloat() < this.cornerHoleChance;
        }
    }
}
