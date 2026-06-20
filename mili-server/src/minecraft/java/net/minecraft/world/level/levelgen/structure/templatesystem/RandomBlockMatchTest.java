package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RandomBlockMatchTest extends RuleTest {
    public static final MapCodec<RandomBlockMatchTest> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(randomBlockMatchTest -> randomBlockMatchTest.block),
                Codec.FLOAT.fieldOf("probability").forGetter(randomBlockMatchTest -> randomBlockMatchTest.probability)
            )
            .apply(instance, RandomBlockMatchTest::new)
    );
    private final Block block;
    private final float probability;

    public RandomBlockMatchTest(Block block, float probability) {
        this.block = block;
        this.probability = probability;
    }

    @Override
    public boolean test(BlockState state, RandomSource random) {
        return state.is(this.block) && random.nextFloat() < this.probability;
    }

    @Override
    protected RuleTestType<?> getType() {
        return RuleTestType.RANDOM_BLOCK_TEST;
    }
}
