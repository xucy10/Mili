package net.minecraft.util.debug;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class DebugSubscription<T> {
    public static final int DOES_NOT_EXPIRE = 0;
    final @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> valueStreamCodec;
    private final int expireAfterTicks;

    public DebugSubscription(@Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> valueStreamCodec, int expireAfterTicks) {
        this.valueStreamCodec = valueStreamCodec;
        this.expireAfterTicks = expireAfterTicks;
    }

    public DebugSubscription(@Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> valueStreamCodec) {
        this(valueStreamCodec, 0);
    }

    public DebugSubscription.Update<T> packUpdate(@Nullable T value) {
        return new DebugSubscription.Update<>(this, Optional.ofNullable(value));
    }

    public DebugSubscription.Update<T> emptyUpdate() {
        return new DebugSubscription.Update<>(this, Optional.empty());
    }

    public DebugSubscription.Event<T> packEvent(T value) {
        return new DebugSubscription.Event<>(this, value);
    }

    @Override
    public String toString() {
        return Util.getRegisteredName(BuiltInRegistries.DEBUG_SUBSCRIPTION, this);
    }

    public @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> valueStreamCodec() {
        return this.valueStreamCodec;
    }

    public int expireAfterTicks() {
        return this.expireAfterTicks;
    }

    public record Event<T>(DebugSubscription<T> subscription, T value) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DebugSubscription.Event<?>> STREAM_CODEC = ByteBufCodecs.registry(
                Registries.DEBUG_SUBSCRIPTION
            )
            .dispatch(DebugSubscription.Event::subscription, DebugSubscription.Event::streamCodec);

        private static <T> StreamCodec<? super RegistryFriendlyByteBuf, DebugSubscription.Event<T>> streamCodec(DebugSubscription<T> subscription) {
            return Objects.requireNonNull(subscription.valueStreamCodec)
                .map(object -> new DebugSubscription.Event<>(subscription, (T)object), DebugSubscription.Event::value);
        }
    }

    public record Update<T>(DebugSubscription<T> subscription, Optional<T> value) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DebugSubscription.Update<?>> STREAM_CODEC = ByteBufCodecs.registry(
                Registries.DEBUG_SUBSCRIPTION
            )
            .dispatch(DebugSubscription.Update::subscription, DebugSubscription.Update::streamCodec);

        private static <T> StreamCodec<? super RegistryFriendlyByteBuf, DebugSubscription.Update<T>> streamCodec(DebugSubscription<T> subscription) {
            return ByteBufCodecs.optional(Objects.requireNonNull(subscription.valueStreamCodec))
                .map(optional -> new DebugSubscription.Update<>(subscription, (Optional<T>)optional), DebugSubscription.Update::value);
        }
    }
}
