package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public record EntityTypePredicate(HolderSet<EntityType<?>> types) {
    public static final Codec<EntityTypePredicate> CODEC = RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
        .xmap(EntityTypePredicate::new, EntityTypePredicate::types);

    public static EntityTypePredicate of(HolderGetter<EntityType<?>> entityTypeRegistry, EntityType<?> entityType) {
        return new EntityTypePredicate(HolderSet.direct(entityType.builtInRegistryHolder()));
    }

    public static EntityTypePredicate of(HolderGetter<EntityType<?>> entityTypeRegistry, TagKey<EntityType<?>> entityTypeTag) {
        return new EntityTypePredicate(entityTypeRegistry.getOrThrow(entityTypeTag));
    }

    public boolean matches(EntityType<?> type) {
        return type.is(this.types);
    }
}
