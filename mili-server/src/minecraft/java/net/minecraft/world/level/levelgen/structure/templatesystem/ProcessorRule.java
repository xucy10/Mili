package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.Passthrough;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.RuleBlockEntityModifier;
import org.jspecify.annotations.Nullable;

public class ProcessorRule {
    public static final Passthrough DEFAULT_BLOCK_ENTITY_MODIFIER = Passthrough.INSTANCE;
    public static final Codec<ProcessorRule> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                RuleTest.CODEC.fieldOf("input_predicate").forGetter(processorRule -> processorRule.inputPredicate),
                RuleTest.CODEC.fieldOf("location_predicate").forGetter(processorRule -> processorRule.locPredicate),
                PosRuleTest.CODEC
                    .lenientOptionalFieldOf("position_predicate", PosAlwaysTrueTest.INSTANCE)
                    .forGetter(processorRule -> processorRule.posPredicate),
                BlockState.CODEC.fieldOf("output_state").forGetter(processorRule -> processorRule.outputState),
                RuleBlockEntityModifier.CODEC
                    .lenientOptionalFieldOf("block_entity_modifier", DEFAULT_BLOCK_ENTITY_MODIFIER)
                    .forGetter(processorRule -> processorRule.blockEntityModifier)
            )
            .apply(instance, ProcessorRule::new)
    );
    private final RuleTest inputPredicate;
    private final RuleTest locPredicate;
    private final PosRuleTest posPredicate;
    private final BlockState outputState;
    private final RuleBlockEntityModifier blockEntityModifier;

    public ProcessorRule(RuleTest inputPredicate, RuleTest locPredicate, BlockState outputState) {
        this(inputPredicate, locPredicate, PosAlwaysTrueTest.INSTANCE, outputState);
    }

    public ProcessorRule(RuleTest inputPredicate, RuleTest locPredicate, PosRuleTest posPredicate, BlockState outputState) {
        this(inputPredicate, locPredicate, posPredicate, outputState, DEFAULT_BLOCK_ENTITY_MODIFIER);
    }

    public ProcessorRule(
        RuleTest inputPredicate, RuleTest locPredicate, PosRuleTest posPredicate, BlockState outputState, RuleBlockEntityModifier blockEntityModifier
    ) {
        this.inputPredicate = inputPredicate;
        this.locPredicate = locPredicate;
        this.posPredicate = posPredicate;
        this.outputState = outputState;
        this.blockEntityModifier = blockEntityModifier;
    }

    public boolean test(BlockState inputState, BlockState existingState, BlockPos localPos, BlockPos relativePos, BlockPos structurePos, RandomSource random) {
        return this.inputPredicate.test(inputState, random)
            && this.locPredicate.test(existingState, random)
            && this.posPredicate.test(localPos, relativePos, structurePos, random);
    }

    public BlockState getOutputState() {
        return this.outputState;
    }

    public @Nullable CompoundTag getOutputTag(RandomSource random, @Nullable CompoundTag tag) {
        return this.blockEntityModifier.apply(random, tag);
    }
}
