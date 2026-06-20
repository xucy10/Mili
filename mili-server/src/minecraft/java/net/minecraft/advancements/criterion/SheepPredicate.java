package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record SheepPredicate(Optional<Boolean> sheared) implements EntitySubPredicate {
    public static final MapCodec<SheepPredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.optionalFieldOf("sheared").forGetter(SheepPredicate::sheared)).apply(instance, SheepPredicate::new)
    );

    @Override
    public MapCodec<SheepPredicate> codec() {
        return EntitySubPredicates.SHEEP;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return entity instanceof Sheep sheep && (!this.sheared.isPresent() || sheep.isSheared() == this.sheared.get());
    }

    public static SheepPredicate hasWool() {
        return new SheepPredicate(Optional.of(false));
    }
}
