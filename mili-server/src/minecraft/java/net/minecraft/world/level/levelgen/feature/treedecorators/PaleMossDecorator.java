package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;

public class PaleMossDecorator extends TreeDecorator {
    public static final MapCodec<PaleMossDecorator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("leaves_probability").forGetter(decorator -> decorator.leavesProbability),
                Codec.floatRange(0.0F, 1.0F).fieldOf("trunk_probability").forGetter(decorator -> decorator.trunkProbability),
                Codec.floatRange(0.0F, 1.0F).fieldOf("ground_probability").forGetter(decorator -> decorator.groundProbability)
            )
            .apply(instance, PaleMossDecorator::new)
    );
    private final float leavesProbability;
    private final float trunkProbability;
    private final float groundProbability;

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.PALE_MOSS;
    }

    public PaleMossDecorator(float leavesProbability, float trunkProbability, float groundProbability) {
        this.leavesProbability = leavesProbability;
        this.trunkProbability = trunkProbability;
        this.groundProbability = groundProbability;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = (WorldGenLevel)context.level();
        List<BlockPos> list = Util.shuffledCopy(context.logs(), randomSource);
        if (!list.isEmpty()) {
            BlockPos blockPos = Collections.min(list, Comparator.comparingInt(Vec3i::getY));
            if (randomSource.nextFloat() < this.groundProbability) {
                worldGenLevel.registryAccess()
                    .lookup(Registries.CONFIGURED_FEATURE)
                    .flatMap(registry -> registry.get(VegetationFeatures.PALE_MOSS_PATCH))
                    .ifPresent(
                        reference -> reference.value()
                            .place(worldGenLevel, worldGenLevel.getLevel().getChunkSource().getGenerator(), randomSource, blockPos.above())
                    );
            }

            context.logs().forEach(pos -> {
                if (randomSource.nextFloat() < this.trunkProbability) {
                    BlockPos blockPos1 = pos.below();
                    if (context.isAir(blockPos1)) {
                        addMossHanger(blockPos1, context);
                    }
                }
            });
            context.leaves().forEach(pos -> {
                if (randomSource.nextFloat() < this.leavesProbability) {
                    BlockPos blockPos1 = pos.below();
                    if (context.isAir(blockPos1)) {
                        addMossHanger(blockPos1, context);
                    }
                }
            });
        }
    }

    private static void addMossHanger(BlockPos pos, TreeDecorator.Context context) {
        while (context.isAir(pos.below()) && !(context.random().nextFloat() < 0.5)) {
            context.setBlock(pos, Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, false));
            pos = pos.below();
        }

        context.setBlock(pos, Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, true));
    }
}
