package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;

abstract class CombiningPredicate implements BlockPredicate {
    protected final List<BlockPredicate> predicates;

    protected CombiningPredicate(List<BlockPredicate> predicates) {
        this.predicates = predicates;
    }

    public static <T extends CombiningPredicate> MapCodec<T> codec(Function<List<BlockPredicate>, T> factory) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(BlockPredicate.CODEC.listOf().fieldOf("predicates").forGetter(combiningPredicate -> combiningPredicate.predicates))
                .apply(instance, factory)
        );
    }
}
