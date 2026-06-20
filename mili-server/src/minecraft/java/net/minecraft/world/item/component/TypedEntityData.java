package net.minecraft.world.item.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;

public final class TypedEntityData<IdType> implements TooltipProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TYPE_TAG = "id";
    final IdType type;
    final CompoundTag tag;

    public static <T> Codec<TypedEntityData<T>> codec(final Codec<T> idCodec) {
        return new Codec<TypedEntityData<T>>() {
            @Override
            public <V> DataResult<Pair<TypedEntityData<T>, V>> decode(DynamicOps<V> ops, V input) {
                return CustomData.COMPOUND_TAG_CODEC
                    .decode(ops, input)
                    .flatMap(
                        pair -> {
                            CompoundTag compoundTag = pair.getFirst().copy();
                            Tag tag = compoundTag.remove("id");
                            return tag == null
                                ? DataResult.error(() -> "Expected 'id' field in " + input)
                                : idCodec.parse(asNbtOps((DynamicOps<T>)ops), tag)
                                    .map(object1 -> Pair.of((TypedEntityData<T>)(new TypedEntityData<>(object1, compoundTag)), (V)pair.getSecond()));
                        }
                    );
            }

            @Override
            public <V> DataResult<V> encode(TypedEntityData<T> input, DynamicOps<V> ops, V prefix) {
                return idCodec.encodeStart(asNbtOps((DynamicOps<T>)ops), input.type).flatMap(tag -> {
                    CompoundTag compoundTag = input.tag.copy();
                    compoundTag.put("id", tag);
                    return CustomData.COMPOUND_TAG_CODEC.encode(compoundTag, ops, prefix);
                });
            }

            private static <T> DynamicOps<Tag> asNbtOps(DynamicOps<T> ops) {
                return (DynamicOps<Tag>)(ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE);
            }
        };
    }

    public static <B extends ByteBuf, T> StreamCodec<B, TypedEntityData<T>> streamCodec(StreamCodec<B, T> idCodec) {
        return StreamCodec.composite(
            idCodec,
            (Function<TypedEntityData<T>, T>)(TypedEntityData::type),
            ByteBufCodecs.COMPOUND_TAG,
            TypedEntityData::tag,
            (BiFunction<T, CompoundTag, TypedEntityData<T>>)(TypedEntityData::new)
        );
    }

    TypedEntityData(IdType type, CompoundTag tag) {
        this.type = type;
        this.tag = stripId(tag);
    }

    // Paper start - utils for item meta
    public static <IdType> TypedEntityData<IdType> decode(final Codec<IdType> idTypeCodec, final CompoundTag tag) {
        return codec(idTypeCodec).decode(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElseThrow().getFirst();
    }

    public static TypedEntityData<EntityType<?>> decodeEntity(final CompoundTag tag) {
        return decode(net.minecraft.world.entity.EntityType.CODEC, tag);
    }

    public static TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> decodeBlockEntity(final CompoundTag tag) {
        return decode(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec(), tag);
    }

    public CompoundTag copyTagWithEntityId() {
        final CompoundTag tag = this.tag.copy();
        tag.putString("id", EntityType.getKey((EntityType<?>) this.type).toString());
        return tag;
    }

    public CompoundTag copyTagWithBlockEntityId() {
        final CompoundTag tag = this.tag.copy();
        tag.putString("id", net.minecraft.world.level.block.entity.BlockEntityType.getKey((net.minecraft.world.level.block.entity.BlockEntityType<?>) this.type).toString());
        return tag;
    }
    // Paper end - utils for item meta

    public static <T> TypedEntityData<T> of(T type, CompoundTag tag) {
        return new TypedEntityData<>(type, tag);
    }

    private static CompoundTag stripId(CompoundTag tag) {
        if (tag.contains("id")) {
            CompoundTag compoundTag = tag.copy();
            compoundTag.remove("id");
            return compoundTag;
        } else {
            return tag;
        }
    }

    public IdType type() {
        return this.type;
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    @Override
    public boolean equals(Object other) {
        return other == this
            || other instanceof TypedEntityData<?> typedEntityData && this.type == typedEntityData.type && this.tag.equals(typedEntityData.tag);
    }

    @Override
    public int hashCode() {
        return 31 * this.type.hashCode() + this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.type + " " + this.tag;
    }

    public void loadInto(Entity entity) {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
            entity.saveWithoutId(tagValueOutput);
            CompoundTag compoundTag = tagValueOutput.buildResult();
            UUID uuid = entity.getUUID();
            compoundTag.merge(this.getUnsafe());
            entity.load(TagValueInput.create(scopedCollector, entity.registryAccess(), compoundTag));
            entity.setUUID(uuid);
        }
    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider registries) {
        boolean exception;
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            blockEntity.saveCustomOnly(tagValueOutput);
            CompoundTag compoundTag = tagValueOutput.buildResult();
            CompoundTag compoundTag1 = compoundTag.copy();
            compoundTag.merge(this.getUnsafe());
            if (!compoundTag.equals(compoundTag1)) {
                try {
                    blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector, registries, compoundTag));
                    blockEntity.setChanged();
                    return true;
                } catch (Exception var11) {
                    LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), var11);

                    try {
                        blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector.forChild(() -> "(rollback)"), registries, compoundTag1));
                    } catch (Exception var10) {
                        LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), var10);
                    }
                }
            }

            exception = false;
        }

        return exception;
    }

    private CompoundTag tag() {
        return this.tag;
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }

    public CompoundTag copyTagWithoutId() {
        return this.tag.copy();
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter componentGetter) {
        if (this.type.getClass() == EntityType.class) {
            EntityType<?> entityType = (EntityType<?>)this.type;
            if (context.isPeaceful() && !entityType.isTypeAllowedInPeaceful()) { // Paper - do not check entity data for peaceful override - match client as close as possible for compute tooltip api
                tooltipAdder.accept(Component.translatable("item.spawn_egg.peaceful").withStyle(ChatFormatting.RED));
            }
        }
    }
}
