package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class LinearPosTest extends PosRuleTest {
    public static final MapCodec<LinearPosTest> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.FLOAT.fieldOf("min_chance").orElse(0.0F).forGetter(linearPosTest -> linearPosTest.minChance),
                Codec.FLOAT.fieldOf("max_chance").orElse(0.0F).forGetter(linearPosTest -> linearPosTest.maxChance),
                Codec.INT.fieldOf("min_dist").orElse(0).forGetter(linearPosTest -> linearPosTest.minDist),
                Codec.INT.fieldOf("max_dist").orElse(0).forGetter(linearPosTest -> linearPosTest.maxDist)
            )
            .apply(instance, LinearPosTest::new)
    );
    private final float minChance;
    private final float maxChance;
    private final int minDist;
    private final int maxDist;

    public LinearPosTest(float minChance, float maxChance, int minDist, int maxDist) {
        if (minDist >= maxDist) {
            throw new IllegalArgumentException("Invalid range: [" + minDist + "," + maxDist + "]");
        } else {
            this.minChance = minChance;
            this.maxChance = maxChance;
            this.minDist = minDist;
            this.maxDist = maxDist;
        }
    }

    @Override
    public boolean test(BlockPos localPos, BlockPos relativePos, BlockPos structurePos, RandomSource random) {
        int i = relativePos.distManhattan(structurePos);
        float randomFloat = random.nextFloat();
        return randomFloat <= Mth.clampedLerp(Mth.inverseLerp((float)i, (float)this.minDist, (float)this.maxDist), this.minChance, this.maxChance);
    }

    @Override
    protected PosRuleTestType<?> getType() {
        return PosRuleTestType.LINEAR_POS_TEST;
    }
}
