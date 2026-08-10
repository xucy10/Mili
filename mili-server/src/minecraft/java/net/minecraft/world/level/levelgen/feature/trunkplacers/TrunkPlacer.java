package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public abstract class TrunkPlacer {
    public static final Codec<TrunkPlacer> CODEC = BuiltInRegistries.TRUNK_PLACER_TYPE.byNameCodec().dispatch(TrunkPlacer::type, TrunkPlacerType::codec);
    private static final int MAX_BASE_HEIGHT = 32;
    private static final int MAX_RAND = 24;
    public static final int MAX_HEIGHT = 80;
    protected final int baseHeight;
    protected final int heightRandA;
    protected final int heightRandB;

    protected static <P extends TrunkPlacer> P3<Mu<P>, Integer, Integer, Integer> trunkPlacerParts(Instance<P> instance) {
        return instance.group(
            Codec.intRange(0, 32).fieldOf("base_height").forGetter(trunkPlacer -> trunkPlacer.baseHeight),
            Codec.intRange(0, 24).fieldOf("height_rand_a").forGetter(trunkPlacer -> trunkPlacer.heightRandA),
            Codec.intRange(0, 24).fieldOf("height_rand_b").forGetter(trunkPlacer -> trunkPlacer.heightRandB)
        );
    }

    public TrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        this.baseHeight = baseHeight;
        this.heightRandA = heightRandA;
        this.heightRandB = heightRandB;
    }

    protected abstract TrunkPlacerType<?> type();

    public abstract List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config
    );

    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }

    private static boolean isDirt(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> Feature.isDirt(state) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.MYCELIUM));
    }

    protected static void setDirtAt(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos pos, TreeConfiguration config
    ) {
        if (config.forceDirt || !isDirt(level, pos)) {
            blockSetter.accept(pos, config.dirtProvider.getState(random, pos));
        }
    }

    protected boolean placeLog(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos pos, TreeConfiguration config
    ) {
        return this.placeLog(level, blockSetter, random, pos, config, Function.identity());
    }

    protected boolean placeLog(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos pos,
        TreeConfiguration config,
        Function<BlockState, BlockState> propertySetter
    ) {
        if (this.validTreePos(level, pos)) {
            blockSetter.accept(pos, propertySetter.apply(config.trunkProvider.getState(random, pos)));
            return true;
        } else {
            return false;
        }
    }

    protected void placeLogIfFree(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos.MutableBlockPos pos, TreeConfiguration config
    ) {
        if (this.isFree(level, pos)) {
            this.placeLog(level, blockSetter, random, pos, config);
        }
    }

    protected boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos);
    }

    public boolean isFree(LevelSimulatedReader level, BlockPos pos) {
        return this.validTreePos(level, pos) || level.isStateAtPosition(pos, blockState -> blockState.is(BlockTags.LOGS));
    }
}
