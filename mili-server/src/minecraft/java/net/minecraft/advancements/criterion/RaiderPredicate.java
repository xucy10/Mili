package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record RaiderPredicate(boolean hasRaid, boolean isCaptain) implements EntitySubPredicate {
    public static final MapCodec<RaiderPredicate> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.BOOL.optionalFieldOf("has_raid", false).forGetter(RaiderPredicate::hasRaid),
                Codec.BOOL.optionalFieldOf("is_captain", false).forGetter(RaiderPredicate::isCaptain)
            )
            .apply(instance, RaiderPredicate::new)
    );
    public static final RaiderPredicate CAPTAIN_WITHOUT_RAID = new RaiderPredicate(false, true);

    @Override
    public MapCodec<RaiderPredicate> codec() {
        return EntitySubPredicates.RAIDER;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return entity instanceof Raider raider && raider.hasRaid() == this.hasRaid && raider.isCaptain() == this.isCaptain;
    }
}
