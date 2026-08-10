package net.minecraft.advancements.criterion;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record SlimePredicate(MinMaxBounds.Ints size) implements EntitySubPredicate {
    public static final MapCodec<SlimePredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("size", MinMaxBounds.Ints.ANY).forGetter(SlimePredicate::size))
            .apply(instance, SlimePredicate::new)
    );

    public static SlimePredicate sized(MinMaxBounds.Ints size) {
        return new SlimePredicate(size);
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return entity instanceof Slime slime && this.size.matches(slime.getSize());
    }

    @Override
    public MapCodec<SlimePredicate> codec() {
        return EntitySubPredicates.SLIME;
    }
}
