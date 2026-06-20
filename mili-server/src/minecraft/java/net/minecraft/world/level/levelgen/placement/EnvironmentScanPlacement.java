package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public class EnvironmentScanPlacement extends PlacementModifier {
    private final Direction directionOfSearch;
    private final BlockPredicate targetCondition;
    private final BlockPredicate allowedSearchCondition;
    private final int maxSteps;
    public static final MapCodec<EnvironmentScanPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Direction.VERTICAL_CODEC.fieldOf("direction_of_search").forGetter(placement -> placement.directionOfSearch),
                BlockPredicate.CODEC.fieldOf("target_condition").forGetter(placement -> placement.targetCondition),
                BlockPredicate.CODEC
                    .optionalFieldOf("allowed_search_condition", BlockPredicate.alwaysTrue())
                    .forGetter(placement -> placement.allowedSearchCondition),
                Codec.intRange(1, 32).fieldOf("max_steps").forGetter(placement -> placement.maxSteps)
            )
            .apply(instance, EnvironmentScanPlacement::new)
    );

    private EnvironmentScanPlacement(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        this.directionOfSearch = directionOfSearch;
        this.targetCondition = targetCondition;
        this.allowedSearchCondition = allowedSearchCondition;
        this.maxSteps = maxSteps;
    }

    public static EnvironmentScanPlacement scanningFor(
        Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps
    ) {
        return new EnvironmentScanPlacement(directionOfSearch, targetCondition, allowedSearchCondition, maxSteps);
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, int maxSteps) {
        return scanningFor(directionOfSearch, targetCondition, BlockPredicate.alwaysTrue(), maxSteps);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        WorldGenLevel level = context.getLevel();
        if (!this.allowedSearchCondition.test(level, mutableBlockPos)) {
            return Stream.of();
        } else {
            for (int i = 0; i < this.maxSteps; i++) {
                if (this.targetCondition.test(level, mutableBlockPos)) {
                    return Stream.of(mutableBlockPos);
                }

                mutableBlockPos.move(this.directionOfSearch);
                if (level.isOutsideBuildHeight(mutableBlockPos.getY())) {
                    return Stream.of();
                }

                if (!this.allowedSearchCondition.test(level, mutableBlockPos)) {
                    break;
                }
            }

            return this.targetCondition.test(level, mutableBlockPos) ? Stream.of(mutableBlockPos) : Stream.of();
        }
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.ENVIRONMENT_SCAN;
    }
}
