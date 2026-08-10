package net.minecraft.world.level.block.state.predicate;

import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class BlockPredicate implements Predicate<BlockState> {
    private final Block block;

    public BlockPredicate(Block block) {
        this.block = block;
    }

    public static BlockPredicate forBlock(Block block) {
        return new BlockPredicate(block);
    }

    @Override
    public boolean test(@Nullable BlockState state) {
        return state != null && state.is(this.block);
    }
}
