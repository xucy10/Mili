package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record VibrationInfo(
    Holder<GameEvent> gameEvent, float distance, Vec3 pos, @Nullable UUID uuid, @Nullable UUID projectileOwnerUuid, @Nullable Entity entity
) {
    public static final Codec<VibrationInfo> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                GameEvent.CODEC.fieldOf("game_event").forGetter(VibrationInfo::gameEvent),
                Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("distance").forGetter(VibrationInfo::distance),
                Vec3.CODEC.fieldOf("pos").forGetter(VibrationInfo::pos),
                UUIDUtil.CODEC.lenientOptionalFieldOf("source").forGetter(info -> Optional.ofNullable(info.uuid())),
                UUIDUtil.CODEC.lenientOptionalFieldOf("projectile_owner").forGetter(info -> Optional.ofNullable(info.projectileOwnerUuid()))
            )
            .apply(instance, (gameEvent, distance, pos, uuid, entity) -> new VibrationInfo(gameEvent, distance, pos, uuid.orElse(null), entity.orElse(null)))
    );

    public VibrationInfo(Holder<GameEvent> gameEvent, float distance, Vec3 pos, @Nullable UUID uuid, @Nullable UUID projectileOwnerUuid) {
        this(gameEvent, distance, pos, uuid, projectileOwnerUuid, null);
    }

    public VibrationInfo(Holder<GameEvent> gameEvent, float distance, Vec3 pos, @Nullable Entity entity) {
        this(gameEvent, distance, pos, entity == null ? null : entity.getUUID(), getProjectileOwner(entity), entity);
    }

    private static @Nullable UUID getProjectileOwner(@Nullable Entity entity) {
        return entity instanceof Projectile projectile && projectile.getOwner() != null ? projectile.getOwner().getUUID() : null;
    }

    public Optional<Entity> getEntity(ServerLevel level) {
        return Optional.ofNullable(this.entity).or(() -> Optional.ofNullable(this.uuid).map(level::getEntity));
    }

    public Optional<Entity> getProjectileOwner(ServerLevel level) {
        return this.getEntity(level)
            .filter(entity -> entity instanceof Projectile)
            .map(entity -> (Projectile)entity)
            .map(Projectile::getOwner)
            .or(() -> Optional.ofNullable(this.projectileOwnerUuid).map(level::getEntity));
    }
}
