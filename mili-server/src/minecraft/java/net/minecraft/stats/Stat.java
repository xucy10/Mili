package net.minecraft.stats;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

public class Stat<T> extends ObjectiveCriteria {
    public static final StreamCodec<RegistryFriendlyByteBuf, Stat<?>> STREAM_CODEC = ByteBufCodecs.registry(Registries.STAT_TYPE)
        .dispatch(Stat::getType, StatType::streamCodec);
    private final StatFormatter formatter;
    private final T value;
    private final StatType<T> type;

    protected Stat(StatType<T> type, T value, StatFormatter formatter) {
        super(buildName(type, value));
        this.type = type;
        this.formatter = formatter;
        this.value = value;
    }

    public static <T> String buildName(StatType<T> type, T value) {
        return locationToKey(BuiltInRegistries.STAT_TYPE.getKey(type)) + ":" + locationToKey(type.getRegistry().getKey(value));
    }

    private static String locationToKey(@Nullable Identifier location) {
        return location.toString().replace(':', '.');
    }

    public StatType<T> getType() {
        return this.type;
    }

    public T getValue() {
        return this.value;
    }

    public String format(int value) {
        return this.formatter.format(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Stat && Objects.equals(this.getName(), ((Stat)other).getName());
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

    @Override
    public String toString() {
        return "Stat{name=" + this.getName() + ", formatter=" + this.formatter + "}";
    }
}
