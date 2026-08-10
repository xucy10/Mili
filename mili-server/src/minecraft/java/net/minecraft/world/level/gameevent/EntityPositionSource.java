package net.minecraft.world.level.gameevent;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityPositionSource implements PositionSource {
    public static final MapCodec<EntityPositionSource> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("source_entity").forGetter(EntityPositionSource::getUuid),
                Codec.FLOAT.fieldOf("y_offset").orElse(0.0F).forGetter(positionSource -> positionSource.yOffset)
            )
            .apply(instance, (sourceUuid, yOffset) -> new EntityPositionSource(Either.right(Either.left(sourceUuid)), yOffset))
    );
    public static final StreamCodec<ByteBuf, EntityPositionSource> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        EntityPositionSource::getId,
        ByteBufCodecs.FLOAT,
        entityPositionSource -> entityPositionSource.yOffset,
        (integer, _float) -> new EntityPositionSource(Either.right(Either.right(integer)), _float)
    );
    private Either<Entity, Either<UUID, Integer>> entityOrUuidOrId;
    private final float yOffset;

    public EntityPositionSource(Entity entity, float yOffset) {
        this(Either.left(entity), yOffset);
    }

    private EntityPositionSource(Either<Entity, Either<UUID, Integer>> entityOrUuidOrId, float yOffset) {
        this.entityOrUuidOrId = entityOrUuidOrId;
        this.yOffset = yOffset;
    }

    @Override
    public Optional<Vec3> getPosition(Level level) {
        if (this.entityOrUuidOrId.left().isEmpty()) {
            this.resolveEntity(level);
        }

        return this.entityOrUuidOrId.left().map(entity -> entity.position().add(0.0, this.yOffset, 0.0));
    }

    private void resolveEntity(Level level) {
        this.entityOrUuidOrId
            .map(
                Optional::of,
                either -> Optional.ofNullable(
                    either.map(uuid -> level instanceof ServerLevel serverLevel ? serverLevel.getEntity(uuid) : null, level::getEntity)
                )
            )
            .ifPresent(entity -> this.entityOrUuidOrId = Either.left(entity));
    }

    public UUID getUuid() {
        return this.entityOrUuidOrId.map(Entity::getUUID, either -> either.map(Function.identity(), integer -> {
            throw new RuntimeException("Unable to get entityId from uuid");
        }));
    }

    private int getId() {
        return this.entityOrUuidOrId.map(Entity::getId, either -> either.map(uuid -> {
            throw new IllegalStateException("Unable to get entityId from uuid");
        }, Function.identity()));
    }

    @Override
    public PositionSourceType<EntityPositionSource> getType() {
        return PositionSourceType.ENTITY;
    }

    public static class Type implements PositionSourceType<EntityPositionSource> {
        @Override
        public MapCodec<EntityPositionSource> codec() {
            return EntityPositionSource.CODEC;
        }

        @Override
        public StreamCodec<ByteBuf, EntityPositionSource> streamCodec() {
            return EntityPositionSource.STREAM_CODEC;
        }
    }
}
