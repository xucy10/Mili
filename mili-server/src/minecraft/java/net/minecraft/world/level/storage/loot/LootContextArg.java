package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface LootContextArg<R> {
    Codec<LootContextArg<Object>> ENTITY_OR_BLOCK = createArgCodec(
        argCodecBuilder -> argCodecBuilder.anyOf(LootContext.EntityTarget.values()).anyOf(LootContext.BlockEntityTarget.values())
    );

    @Nullable R get(LootContext context);

    ContextKey<?> contextParam();

    static <U> LootContextArg<U> cast(LootContextArg<? extends U> arg) {
        return (LootContextArg<U>)arg;
    }

    static <R> Codec<LootContextArg<R>> createArgCodec(UnaryOperator<LootContextArg.ArgCodecBuilder<R>> builder) {
        return builder.apply(new LootContextArg.ArgCodecBuilder<>()).build();
    }

    public static final class ArgCodecBuilder<R> {
        private final ExtraCodecs.LateBoundIdMapper<String, LootContextArg<R>> sources = new ExtraCodecs.LateBoundIdMapper<>();

        ArgCodecBuilder() {
        }

        public <T> LootContextArg.ArgCodecBuilder<R> anyOf(T[] values, Function<T, String> nameGetter, Function<T, ? extends LootContextArg<R>> factory) {
            for (T object : values) {
                this.sources.put(nameGetter.apply(object), (LootContextArg<R>)factory.apply(object));
            }

            return this;
        }

        public <T extends StringRepresentable> LootContextArg.ArgCodecBuilder<R> anyOf(T[] values, Function<T, ? extends LootContextArg<R>> factory) {
            return this.anyOf(values, StringRepresentable::getSerializedName, factory);
        }

        public <T extends StringRepresentable & LootContextArg<? extends R>> LootContextArg.ArgCodecBuilder<R> anyOf(T[] values) {
            return this.anyOf(values, object -> LootContextArg.cast((LootContextArg<? extends R>)object));
        }

        public LootContextArg.ArgCodecBuilder<R> anyEntity(Function<? super ContextKey<? extends Entity>, ? extends LootContextArg<R>> factory) {
            return this.anyOf(LootContext.EntityTarget.values(), entityTarget -> factory.apply(entityTarget.contextParam()));
        }

        public LootContextArg.ArgCodecBuilder<R> anyBlockEntity(Function<? super ContextKey<? extends BlockEntity>, ? extends LootContextArg<R>> factory) {
            return this.anyOf(LootContext.BlockEntityTarget.values(), blockEntityTarget -> factory.apply(blockEntityTarget.contextParam()));
        }

        public LootContextArg.ArgCodecBuilder<R> anyItemStack(Function<? super ContextKey<? extends ItemStack>, ? extends LootContextArg<R>> factory) {
            return this.anyOf(LootContext.ItemStackTarget.values(), itemStackTarget -> factory.apply(itemStackTarget.contextParam()));
        }

        Codec<LootContextArg<R>> build() {
            return this.sources.codec(Codec.STRING);
        }
    }

    public interface Getter<T, R> extends LootContextArg<R> {
        @Nullable R get(T value);

        @Override
        ContextKey<? extends T> contextParam();

        @Override
        default @Nullable R get(LootContext context) {
            T optionalParameter = context.getOptionalParameter((ContextKey<T>)this.contextParam());
            return optionalParameter != null ? this.get(optionalParameter) : null;
        }
    }

    public interface SimpleGetter<T> extends LootContextArg<T> {
        @Override
        ContextKey<? extends T> contextParam();

        @Override
        default @Nullable T get(LootContext context) {
            return context.getOptionalParameter((ContextKey<T>)this.contextParam());
        }
    }
}
