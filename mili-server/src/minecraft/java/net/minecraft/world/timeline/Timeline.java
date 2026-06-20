package net.minecraft.world.timeline;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.level.Level;

public class Timeline {
    public static final Codec<Holder<Timeline>> CODEC = RegistryFixedCodec.create(Registries.TIMELINE);
    private static final Codec<Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>>> TRACKS_CODEC = Codec.dispatchedMap(
        EnvironmentAttributes.CODEC, Util.memoize(AttributeTrack::createCodec)
    );
    public static final Codec<Timeline> DIRECT_CODEC = RecordCodecBuilder.<Timeline>create(
            instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("period_ticks").forGetter(timeline -> timeline.periodTicks),
                    TRACKS_CODEC.optionalFieldOf("tracks", Map.of()).forGetter(timeline -> timeline.tracks)
                )
                .apply(instance, Timeline::new)
        )
        .validate(Timeline::validateInternal);
    public static final Codec<Timeline> NETWORK_CODEC = DIRECT_CODEC.xmap(Timeline::filterSyncableTracks, Timeline::filterSyncableTracks);
    private final Optional<Integer> periodTicks;
    private final Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks;

    private static Timeline filterSyncableTracks(Timeline timeline) {
        Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> map = Map.copyOf(Maps.filterKeys(timeline.tracks, EnvironmentAttribute::isSyncable));
        return new Timeline(timeline.periodTicks, map);
    }

    Timeline(Optional<Integer> periodTicks, Map<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks) {
        this.periodTicks = periodTicks;
        this.tracks = tracks;
    }

    private static DataResult<Timeline> validateInternal(Timeline timeline) {
        if (timeline.periodTicks.isEmpty()) {
            return DataResult.success(timeline);
        } else {
            int i = timeline.periodTicks.get();
            DataResult<Timeline> dataResult = DataResult.success(timeline);

            for (AttributeTrack<?, ?> attributeTrack : timeline.tracks.values()) {
                dataResult = dataResult.apply2stable((timeline1, attributeTrack1) -> timeline1, AttributeTrack.validatePeriod(attributeTrack, i));
            }

            return dataResult;
        }
    }

    public static Timeline.Builder builder() {
        return new Timeline.Builder();
    }

    public long getCurrentTicks(Level level) {
        long totalTicks = this.getTotalTicks(level);
        return this.periodTicks.isEmpty() ? totalTicks : totalTicks % this.periodTicks.get().intValue();
    }

    public long getTotalTicks(Level level) {
        return level.getDayTime();
    }

    public Optional<Integer> periodTicks() {
        return this.periodTicks;
    }

    public Set<EnvironmentAttribute<?>> attributes() {
        return this.tracks.keySet();
    }

    public <Value> AttributeTrackSampler<Value, ?> createTrackSampler(EnvironmentAttribute<Value> attribute, LongSupplier dayTimeGetter) {
        AttributeTrack<Value, ?> attributeTrack = (AttributeTrack<Value, ?>)this.tracks.get(attribute);
        if (attributeTrack == null) {
            throw new IllegalStateException("Timeline has no track for " + attribute);
        } else {
            return attributeTrack.bakeSampler(attribute, this.periodTicks, dayTimeGetter);
        }
    }

    public static class Builder {
        private Optional<Integer> periodTicks = Optional.empty();
        private final ImmutableMap.Builder<EnvironmentAttribute<?>, AttributeTrack<?, ?>> tracks = ImmutableMap.builder();

        Builder() {
        }

        public Timeline.Builder setPeriodTicks(int periodTicks) {
            this.periodTicks = Optional.of(periodTicks);
            return this;
        }

        public <Value, Argument> Timeline.Builder addModifierTrack(
            EnvironmentAttribute<Value> attribute, AttributeModifier<Value, Argument> modifier, Consumer<KeyframeTrack.Builder<Argument>> builder
        ) {
            attribute.type().checkAllowedModifier(modifier);
            KeyframeTrack.Builder<Argument> builder1 = new KeyframeTrack.Builder<>();
            builder.accept(builder1);
            this.tracks.put(attribute, new AttributeTrack<>(modifier, builder1.build()));
            return this;
        }

        public <Value> Timeline.Builder addTrack(EnvironmentAttribute<Value> attribute, Consumer<KeyframeTrack.Builder<Value>> builder) {
            return this.addModifierTrack(attribute, AttributeModifier.override(), builder);
        }

        public Timeline build() {
            return new Timeline(this.periodTicks, this.tracks.build());
        }
    }
}
