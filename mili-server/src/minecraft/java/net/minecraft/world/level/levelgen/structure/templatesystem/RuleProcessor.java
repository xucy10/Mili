package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class RuleProcessor extends StructureProcessor {
    public static final MapCodec<RuleProcessor> CODEC = ProcessorRule.CODEC
        .listOf()
        .fieldOf("rules")
        .xmap(RuleProcessor::new, ruleProcessor -> ruleProcessor.rules);
    private final ImmutableList<ProcessorRule> rules;

    public RuleProcessor(List<? extends ProcessorRule> rules) {
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(
        LevelReader level,
        BlockPos offset,
        BlockPos pos,
        StructureTemplate.StructureBlockInfo blockInfo,
        StructureTemplate.StructureBlockInfo relativeBlockInfo,
        StructurePlaceSettings settings
    ) {
        RandomSource randomSource = RandomSource.create(Mth.getSeed(relativeBlockInfo.pos()));
        BlockState blockState = level.getBlockState(relativeBlockInfo.pos());

        for (ProcessorRule processorRule : this.rules) {
            if (processorRule.test(relativeBlockInfo.state(), blockState, blockInfo.pos(), relativeBlockInfo.pos(), pos, randomSource)) {
                return new StructureTemplate.StructureBlockInfo(
                    relativeBlockInfo.pos(), processorRule.getOutputState(), processorRule.getOutputTag(randomSource, relativeBlockInfo.nbt())
                );
            }
        }

        return relativeBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.RULE;
    }
}
