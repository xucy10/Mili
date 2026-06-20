package net.minecraft.world.level.storage.loot.providers.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.nbt.Tag;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import org.jspecify.annotations.Nullable;

public class ContextNbtProvider implements NbtProvider {
    private static final Codec<LootContextArg<Tag>> GETTER_CODEC = LootContextArg.createArgCodec(
        argCodecBuilder -> argCodecBuilder.anyBlockEntity(ContextNbtProvider.BlockEntitySource::new).anyEntity(ContextNbtProvider.EntitySource::new)
    );
    public static final MapCodec<ContextNbtProvider> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(GETTER_CODEC.fieldOf("target").forGetter(contextNbtProvider -> contextNbtProvider.source))
            .apply(instance, ContextNbtProvider::new)
    );
    public static final Codec<ContextNbtProvider> INLINE_CODEC = GETTER_CODEC.xmap(ContextNbtProvider::new, contextNbtProvider -> contextNbtProvider.source);
    private final LootContextArg<Tag> source;

    private ContextNbtProvider(LootContextArg<Tag> source) {
        this.source = source;
    }

    @Override
    public LootNbtProviderType getType() {
        return NbtProviders.CONTEXT;
    }

    @Override
    public @Nullable Tag get(LootContext lootContext) {
        return this.source.get(lootContext);
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(this.source.contextParam());
    }

    public static NbtProvider forContextEntity(LootContext.EntityTarget entityTarget) {
        return new ContextNbtProvider(new ContextNbtProvider.EntitySource(entityTarget.contextParam()));
    }

    record BlockEntitySource(@Override ContextKey<? extends BlockEntity> contextParam) implements LootContextArg.Getter<BlockEntity, Tag> {
        @Override
        public Tag get(BlockEntity value) {
            return value.saveWithFullMetadata(value.getLevel().registryAccess());
        }
    }

    record EntitySource(@Override ContextKey<? extends Entity> contextParam) implements LootContextArg.Getter<Entity, Tag> {
        @Override
        public Tag get(Entity value) {
            return NbtPredicate.getEntityTagToCompare(value);
        }
    }
}
