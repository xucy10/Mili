package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

class MatchingBlocksPredicate extends StateTestingPredicate {
    private final HolderSet<Block> blocks;
    public static final MapCodec<MatchingBlocksPredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> stateTestingCodec(instance)
            .and(RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(matchingBlocksPredicate -> matchingBlocksPredicate.blocks))
            .apply(instance, MatchingBlocksPredicate::new)
    );

    public MatchingBlocksPredicate(Vec3i offset, HolderSet<Block> blocks) {
        super(offset);
        this.blocks = blocks;
    }

    @Override
    protected boolean test(BlockState state) {
        return state.is(this.blocks);
    }

    @Override
    public BlockPredicateType<?> type() {
        return BlockPredicateType.MATCHING_BLOCKS;
    }
}
