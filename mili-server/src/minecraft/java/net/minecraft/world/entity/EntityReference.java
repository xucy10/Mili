package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.UUIDLookup;
import net.minecraft.world.level.entity.UniquelyIdentifyable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class EntityReference<StoredEntityType extends Entity> { // Folia - region threading - force Entity Type
    private static final Codec<? extends EntityReference<?>> CODEC = UUIDUtil.CODEC.xmap(EntityReference::new, EntityReference::getUUID);
    private static final StreamCodec<ByteBuf, ? extends EntityReference<?>> STREAM_CODEC = UUIDUtil.STREAM_CODEC
        .map(EntityReference::new, EntityReference::getUUID);
    private Either<UUID, org.bukkit.craftbukkit.entity.CraftEntity> entity; // Folia - region threading - force usage of CraftEntity

    public static <Type extends Entity> Codec<EntityReference<Type>> codec() { // Folia - region threading - force Entity Type
        return (Codec<EntityReference<Type>>)CODEC;
    }

    public static <Type extends Entity> StreamCodec<ByteBuf, EntityReference<Type>> streamCodec() { // Folia - region threading - force Entity Type
        return (StreamCodec<ByteBuf, EntityReference<Type>>)STREAM_CODEC;
    }

    private EntityReference(StoredEntityType entity) {
        this.entity = Either.right(entity.getBukkitEntity()); // Folia - region threading - force usage of CraftEntity
    }

    private EntityReference(UUID uuid) {
        this.entity = Either.left(uuid);
    }

    public static <T extends Entity> @Nullable EntityReference<T> of(@Nullable T entity) { // Folia - region threading - force Entity Type
        return entity != null ? new EntityReference<>(entity) : null;
    }

    public static <T extends Entity> EntityReference<T> of(UUID uuid) { // Folia - region threading - force Entity Type
        return new EntityReference<>(uuid);
    }

    public UUID getUUID() {
        return this.entity.map(uuid -> (UUID)uuid, org.bukkit.craftbukkit.entity.CraftEntity::getUniqueId); // Folia - region threading - force usage of CraftEntity
    }

    public @Nullable StoredEntityType getEntity(UUIDLookup<? extends UniquelyIdentifyable> uuidLookup, Class<StoredEntityType> entityClass) {
        Optional<org.bukkit.craftbukkit.entity.CraftEntity> optional = this.entity.right(); // Folia - region threading - force usage of CraftEntity
        if (optional.isPresent()) {
            StoredEntityType uniquelyIdentifyable = (StoredEntityType)optional.get().getHandleRaw(); // Folia - region threading - force usage of CraftEntity
            if (!uniquelyIdentifyable.isRemoved()) {
                return uniquelyIdentifyable;
            }

            this.entity = Either.left(uniquelyIdentifyable.getUUID());
        }

        Optional<UUID> optional1 = this.entity.left();
        if (optional1.isPresent()) {
            StoredEntityType uniquelyIdentifyable1 = this.resolve(uuidLookup.lookup(optional1.get()), entityClass);
            if (uniquelyIdentifyable1 != null && !uniquelyIdentifyable1.isRemoved()) {
                this.entity = Either.right(uniquelyIdentifyable1.getBukkitEntity()); // Folia - region threading - force usage of CraftEntity
                return uniquelyIdentifyable1;
            }
        }

        return null;
    }

    public @Nullable StoredEntityType getEntity(Level level, Class<StoredEntityType> entityClass) {
        return Player.class.isAssignableFrom(entityClass)
            ? this.getEntity(level::getPlayerInAnyDimension, entityClass)
            : this.getEntity(level::getEntityInAnyDimension, entityClass);
    }

    private @Nullable StoredEntityType resolve(@Nullable UniquelyIdentifyable entity, Class<StoredEntityType> entityClass) {
        return entity != null && entityClass.isAssignableFrom(entity.getClass()) ? entityClass.cast(entity) : null;
    }

    public boolean matches(StoredEntityType entity) {
        return this.getUUID().equals(entity.getUUID());
    }

    public void store(ValueOutput output, String key) {
        output.store(key, UUIDUtil.CODEC, this.getUUID());
    }

    public static void store(@Nullable EntityReference<?> key, ValueOutput output, String uuid) {
        if (key != null) {
            key.store(output, uuid);
        }
    }

    public static <StoredEntityType extends Entity> @Nullable StoredEntityType get( // Folia - region threading - force Entity Type
        @Nullable EntityReference<StoredEntityType> reference, Level level, Class<StoredEntityType> entityClass
    ) {
        return reference != null ? reference.getEntity(level, entityClass) : null;
    }

    public static @Nullable Entity getEntity(@Nullable EntityReference<Entity> reference, Level level) {
        return get(reference, level, Entity.class);
    }

    public static @Nullable LivingEntity getLivingEntity(@Nullable EntityReference<LivingEntity> reference, Level level) {
        return get(reference, level, LivingEntity.class);
    }

    public static @Nullable Player getPlayer(@Nullable EntityReference<Player> reference, Level level) {
        return get(reference, level, Player.class);
    }

    public static <StoredEntityType extends Entity> @Nullable EntityReference<StoredEntityType> read(ValueInput input, String key) { // Folia - region threading - force Entity Type
        return input.read(key, EntityReference.<StoredEntityType>codec()).orElse(null);
    }

    public static <StoredEntityType extends Entity> @Nullable EntityReference<StoredEntityType> readWithOldOwnerConversion( // Folia - region threading - force Entity Type
        ValueInput input, String key, Level level
    ) {
        Optional<UUID> optional = input.read(key, UUIDUtil.CODEC);
        return optional.isPresent()
            ? of(optional.get())
            : input.getString(key)
                .map(string -> OldUsersConverter.convertMobOwnerIfNecessary(level.getServer(), string))
                .map(EntityReference<StoredEntityType>::new)
                .orElse(null);
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof EntityReference<?> entityReference && this.getUUID().equals(entityReference.getUUID());
    }

    @Override
    public int hashCode() {
        return this.getUUID().hashCode();
    }

    // Paper start - utility for reading an entity from a nullable entity reference via a UUIDLookup
    @Nullable
    public static <StoredEntityType extends Entity> StoredEntityType getEntity( // Folia - region threading - force Entity Type
        final @Nullable EntityReference<StoredEntityType> ref,
        final UUIDLookup<? extends UniquelyIdentifyable> uuidLookup,
        final Class<StoredEntityType> entityClass
    ) {
        return ref == null ? null : ref.getEntity(uuidLookup, entityClass);
    }
    // Paper end - utility for reading an entity from a nullable entity reference via a UUIDLookup
}
