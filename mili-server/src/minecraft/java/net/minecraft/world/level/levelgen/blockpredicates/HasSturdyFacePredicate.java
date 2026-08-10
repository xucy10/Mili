package net.minecraft.world.level.levelgen.blockpredicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;

public class HasSturdyFacePredicate implements BlockPredicate {
    private final Vec3i offset;
    private final Direction direction;
    public static final MapCodec<HasSturdyFacePredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Vec3i.offsetCodec(16).optionalFieldOf("offset", Vec3i.ZERO).forGetter(hasSturdyFacePredicate -> hasSturdyFacePredicate.offset),
                Direction.CODEC.fieldOf("direction").forGetter(hasSturdyFacePredicate -> hasSturdyFacePredicate.direction)
            )
            .apply(instance, HasSturdyFacePredicate::new)
    );

    public HasSturdyFacePredicate(Vec3i offset, Direction direction) {
        this.offset = offset;
        this.direction = direction;
    }

    @Override
    public boolean test(WorldGenLevel level, BlockPos pos) {
        BlockPos blockPos = pos.offset(this.offset);
        return level.getBlockState(blockPos).isFaceSturdy(level, blockPos, this.direction);
    }

    @Override
    public BlockPredicateType<?> type() {
        return BlockPredicateType.HAS_STURDY_FACE;
    }
}
