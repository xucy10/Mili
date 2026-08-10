package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class AxisAlignedLinearPosTest extends PosRuleTest {
    public static final MapCodec<AxisAlignedLinearPosTest> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.FLOAT.fieldOf("min_chance").orElse(0.0F).forGetter(axisAlignedLinearPosTest -> axisAlignedLinearPosTest.minChance),
                Codec.FLOAT.fieldOf("max_chance").orElse(0.0F).forGetter(axisAlignedLinearPosTest -> axisAlignedLinearPosTest.maxChance),
                Codec.INT.fieldOf("min_dist").orElse(0).forGetter(axisAlignedLinearPosTest -> axisAlignedLinearPosTest.minDist),
                Codec.INT.fieldOf("max_dist").orElse(0).forGetter(axisAlignedLinearPosTest -> axisAlignedLinearPosTest.maxDist),
                Direction.Axis.CODEC.fieldOf("axis").orElse(Direction.Axis.Y).forGetter(axisAlignedLinearPosTest -> axisAlignedLinearPosTest.axis)
            )
            .apply(instance, AxisAlignedLinearPosTest::new)
    );
    private final float minChance;
    private final float maxChance;
    private final int minDist;
    private final int maxDist;
    private final Direction.Axis axis;

    public AxisAlignedLinearPosTest(float minChance, float maxChance, int minDist, int maxDist, Direction.Axis axis) {
        if (minDist >= maxDist) {
            throw new IllegalArgumentException("Invalid range: [" + minDist + "," + maxDist + "]");
        } else {
            this.minChance = minChance;
            this.maxChance = maxChance;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.axis = axis;
        }
    }

    @Override
    public boolean test(BlockPos localPos, BlockPos relativePos, BlockPos structurePos, RandomSource random) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, this.axis);
        float f = Math.abs((relativePos.getX() - structurePos.getX()) * direction.getStepX());
        float f1 = Math.abs((relativePos.getY() - structurePos.getY()) * direction.getStepY());
        float f2 = Math.abs((relativePos.getZ() - structurePos.getZ()) * direction.getStepZ());
        int i = (int)(f + f1 + f2);
        float randomFloat = random.nextFloat();
        return randomFloat <= Mth.clampedLerp(Mth.inverseLerp((float)i, (float)this.minDist, (float)this.maxDist), this.minChance, this.maxChance);
    }

    @Override
    protected PosRuleTestType<?> getType() {
        return PosRuleTestType.AXIS_ALIGNED_LINEAR_POS_TEST;
    }
}
