package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

class AllOfPredicate extends CombiningPredicate {
    public static final MapCodec<AllOfPredicate> CODEC = codec(AllOfPredicate::new);

    public AllOfPredicate(List<BlockPredicate> predicates) {
        super(predicates);
    }

    @Override
    public boolean test(WorldGenLevel level, BlockPos pos) {
        for (BlockPredicate blockPredicate : this.predicates) {
            if (!blockPredicate.test(level, pos)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public BlockPredicateType<?> type() {
        return BlockPredicateType.ALL_OF;
    }
}
