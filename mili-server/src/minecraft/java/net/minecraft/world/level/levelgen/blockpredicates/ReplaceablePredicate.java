package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

class ReplaceablePredicate extends StateTestingPredicate {
    public static final MapCodec<ReplaceablePredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> stateTestingCodec(instance).apply(instance, ReplaceablePredicate::new)
    );

    public ReplaceablePredicate(Vec3i offset) {
        super(offset);
    }

    @Override
    protected boolean test(BlockState state) {
        return state.canBeReplaced();
    }

    @Override
    public BlockPredicateType<?> type() {
        return BlockPredicateType.REPLACEABLE;
    }
}
