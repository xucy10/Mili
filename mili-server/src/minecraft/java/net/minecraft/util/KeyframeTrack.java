package net.minecraft.util;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.attribute.LerpFunction;

public record KeyframeTrack<T>(List<Keyframe<T>> keyframes, EasingType easingType) {
    public KeyframeTrack(List<Keyframe<T>> keyframes, EasingType easingType) {
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("Track has no keyframes");
        } else {
            this.keyframes = keyframes;
            this.easingType = easingType;
        }
    }

    public static <T> MapCodec<KeyframeTrack<T>> mapCodec(Codec<T> valueCodec) {
        Codec<List<Keyframe<T>>> codec = Keyframe.codec(valueCodec).listOf().validate(KeyframeTrack::validateKeyframes);
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    codec.fieldOf("keyframes").forGetter(KeyframeTrack::keyframes),
                    EasingType.CODEC.optionalFieldOf("ease", EasingType.LINEAR).forGetter(KeyframeTrack::easingType)
                )
                .apply(instance, KeyframeTrack::new)
        );
    }

    static <T> DataResult<List<Keyframe<T>>> validateKeyframes(List<Keyframe<T>> keyframes) {
        if (keyframes.isEmpty()) {
            return DataResult.error(() -> "Keyframes must not be empty");
        } else if (!Comparators.isInOrder(keyframes, Comparator.comparingInt(Keyframe::ticks))) {
            return DataResult.error(() -> "Keyframes must be ordered by ticks field");
        } else {
            if (keyframes.size() > 1) {
                int i = 0;
                int ticks = keyframes.getLast().ticks();

                for (Keyframe<T> keyframe : keyframes) {
                    if (keyframe.ticks() == ticks) {
                        if (++i > 2) {
                            return DataResult.error(() -> "More than 2 keyframes on same tick: " + keyframe.ticks());
                        }
                    } else {
                        i = 0;
                    }

                    ticks = keyframe.ticks();
                }
            }

            return DataResult.success(keyframes);
        }
    }

    public static DataResult<KeyframeTrack<?>> validatePeriod(KeyframeTrack<?> track, int periodTicks) {
        for (Keyframe<?> keyframe : track.keyframes()) {
            int ticks = keyframe.ticks();
            if (ticks < 0 || ticks > periodTicks) {
                return DataResult.error(() -> "Keyframe at tick " + keyframe.ticks() + " must be in range [0; " + periodTicks + "]");
            }
        }

        return DataResult.success(track);
    }

    public KeyframeTrackSampler<T> bakeSampler(Optional<Integer> periodTicks, LerpFunction<T> lerp) {
        return new KeyframeTrackSampler<>(this, periodTicks, lerp);
    }

    public static class Builder<T> {
        private final ImmutableList.Builder<Keyframe<T>> keyframes = ImmutableList.builder();
        private EasingType easing = EasingType.LINEAR;

        public KeyframeTrack.Builder<T> addKeyframe(int ticks, T value) {
            this.keyframes.add(new Keyframe<>(ticks, value));
            return this;
        }

        public KeyframeTrack.Builder<T> setEasing(EasingType easing) {
            this.easing = easing;
            return this;
        }

        public KeyframeTrack<T> build() {
            List<Keyframe<T>> list = KeyframeTrack.validateKeyframes(this.keyframes.build()).getOrThrow();
            return new KeyframeTrack<>(list, this.easing);
        }
    }
}
