package net.minecraft.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.attribute.LerpFunction;

public class KeyframeTrackSampler<T> {
    private final Optional<Integer> periodTicks;
    private final LerpFunction<T> lerp;
    private final List<KeyframeTrackSampler.Segment<T>> segments;

    KeyframeTrackSampler(KeyframeTrack<T> track, Optional<Integer> periodTicks, LerpFunction<T> lerp) {
        this.periodTicks = periodTicks;
        this.lerp = lerp;
        this.segments = bakeSegments(track, periodTicks);
    }

    private static <T> List<KeyframeTrackSampler.Segment<T>> bakeSegments(KeyframeTrack<T> track, Optional<Integer> periodTicks) {
        List<Keyframe<T>> list = track.keyframes();
        if (list.size() == 1) {
            T object = list.getFirst().value();
            return List.of(new KeyframeTrackSampler.Segment<>(EasingType.CONSTANT, object, 0, object, 0));
        } else {
            List<KeyframeTrackSampler.Segment<T>> list1 = new ArrayList<>();
            if (periodTicks.isPresent()) {
                Keyframe<T> keyframe = list.getFirst();
                Keyframe<T> keyframe1 = list.getLast();
                list1.add(new KeyframeTrackSampler.Segment<>(track, keyframe1, keyframe1.ticks() - periodTicks.get(), keyframe, keyframe.ticks()));
                addSegmentsFromKeyframes(track, list, list1);
                list1.add(new KeyframeTrackSampler.Segment<>(track, keyframe1, keyframe1.ticks(), keyframe, keyframe.ticks() + periodTicks.get()));
            } else {
                addSegmentsFromKeyframes(track, list, list1);
            }

            return List.copyOf(list1);
        }
    }

    private static <T> void addSegmentsFromKeyframes(KeyframeTrack<T> track, List<Keyframe<T>> keyframes, List<KeyframeTrackSampler.Segment<T>> output) {
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe<T> keyframe = keyframes.get(i);
            Keyframe<T> keyframe1 = keyframes.get(i + 1);
            output.add(new KeyframeTrackSampler.Segment<>(track, keyframe, keyframe.ticks(), keyframe1, keyframe1.ticks()));
        }
    }

    public T sample(long ticks) {
        long l = this.loopTicks(ticks);
        KeyframeTrackSampler.Segment<T> segmentAt = this.getSegmentAt(l);
        if (l <= segmentAt.fromTicks) {
            return segmentAt.fromValue;
        } else if (l >= segmentAt.toTicks) {
            return segmentAt.toValue;
        } else {
            float f = (float)(l - segmentAt.fromTicks) / (segmentAt.toTicks - segmentAt.fromTicks);
            float f1 = segmentAt.easing.apply(f);
            return this.lerp.apply(f1, segmentAt.fromValue, segmentAt.toValue);
        }
    }

    private KeyframeTrackSampler.Segment<T> getSegmentAt(long currentTicks) {
        for (KeyframeTrackSampler.Segment<T> segment : this.segments) {
            if (currentTicks < segment.toTicks) {
                return segment;
            }
        }

        return this.segments.getLast();
    }

    private long loopTicks(long ticks) {
        return this.periodTicks.isPresent() ? Math.floorMod(ticks, this.periodTicks.get()) : ticks;
    }

    record Segment<T>(EasingType easing, T fromValue, int fromTicks, T toValue, int toTicks) {
        public Segment(KeyframeTrack<T> track, Keyframe<T> from, int fromTicks, Keyframe<T> to, int toTicks) {
            this(track.easingType(), from.value(), fromTicks, to.value(), toTicks);
        }
    }
}
