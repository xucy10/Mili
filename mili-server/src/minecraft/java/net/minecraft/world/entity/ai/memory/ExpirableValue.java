package net.minecraft.world.entity.ai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.VisibleForDebug;

public class ExpirableValue<T> {
    private final T value;
    private long timeToLive;

    public ExpirableValue(T value, long timeToLive) {
        this.value = value;
        this.timeToLive = timeToLive;
    }

    public void tick() {
        if (this.canExpire()) {
            this.timeToLive--;
        }
    }

    public static <T> ExpirableValue<T> of(T value) {
        return new ExpirableValue<>(value, Long.MAX_VALUE);
    }

    public static <T> ExpirableValue<T> of(T value, long timeToLive) {
        return new ExpirableValue<>(value, timeToLive);
    }

    public long getTimeToLive() {
        return this.timeToLive;
    }

    public T getValue() {
        return this.value;
    }

    public boolean hasExpired() {
        return this.timeToLive <= 0L;
    }

    @Override
    public String toString() {
        return this.value + (this.canExpire() ? " (ttl: " + this.timeToLive + ")" : "");
    }

    @VisibleForDebug
    public boolean canExpire() {
        return this.timeToLive != Long.MAX_VALUE;
    }

    public static <T> Codec<ExpirableValue<T>> codec(Codec<T> valueCodec) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    valueCodec.fieldOf("value").forGetter(expirableValue -> expirableValue.value),
                    Codec.LONG
                        .lenientOptionalFieldOf("ttl")
                        .forGetter(expirable -> expirable.canExpire() ? Optional.of(expirable.timeToLive) : Optional.empty())
                )
                .apply(instance, (value, timeToLive) -> new ExpirableValue<>(value, timeToLive.orElse(Long.MAX_VALUE)))
        );
    }
}
