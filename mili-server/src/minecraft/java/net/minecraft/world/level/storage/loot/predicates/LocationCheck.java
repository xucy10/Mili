package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.criterion.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public record LocationCheck(Optional<LocationPredicate> predicate, BlockPos offset) implements LootItemCondition {
    private static final MapCodec<BlockPos> OFFSET_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.INT.optionalFieldOf("offsetX", 0).forGetter(Vec3i::getX),
                Codec.INT.optionalFieldOf("offsetY", 0).forGetter(Vec3i::getY),
                Codec.INT.optionalFieldOf("offsetZ", 0).forGetter(Vec3i::getZ)
            )
            .apply(instance, BlockPos::new)
    );
    public static final MapCodec<LocationCheck> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                LocationPredicate.CODEC.optionalFieldOf("predicate").forGetter(LocationCheck::predicate), OFFSET_CODEC.forGetter(LocationCheck::offset)
            )
            .apply(instance, LocationCheck::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.LOCATION_CHECK;
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 vec3 = context.getOptionalParameter(LootContextParams.ORIGIN);
        return vec3 != null
            && (
                this.predicate.isEmpty()
                    || this.predicate
                        .get()
                        .matches(context.getLevel(), vec3.x() + this.offset.getX(), vec3.y() + this.offset.getY(), vec3.z() + this.offset.getZ())
            );
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    public static LootItemCondition.Builder checkLocation(LocationPredicate.Builder locationPredicateBuilder) {
        return () -> new LocationCheck(Optional.of(locationPredicateBuilder.build()), BlockPos.ZERO);
    }

    public static LootItemCondition.Builder checkLocation(LocationPredicate.Builder locationPredicateBuilder, BlockPos offset) {
        return () -> new LocationCheck(Optional.of(locationPredicateBuilder.build()), offset);
    }
}
