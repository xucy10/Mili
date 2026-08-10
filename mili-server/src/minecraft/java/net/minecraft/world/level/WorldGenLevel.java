package net.minecraft.world.level;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public interface WorldGenLevel extends ServerLevelAccessor {
    long getSeed();

    default boolean ensureCanWrite(BlockPos pos) {
        return true;
    }

    default void setCurrentlyGenerating(@Nullable Supplier<String> currentlyGenerating) {
    }
}
