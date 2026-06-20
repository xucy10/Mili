package net.minecraft.server.dialog.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

public record NumberRangeInput(int width, Component label, String labelFormat, NumberRangeInput.RangeInfo rangeInfo) implements InputControl {
    public static final MapCodec<NumberRangeInput> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(NumberRangeInput::width),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(NumberRangeInput::label),
                Codec.STRING.optionalFieldOf("label_format", "options.generic_value").forGetter(NumberRangeInput::labelFormat),
                NumberRangeInput.RangeInfo.MAP_CODEC.forGetter(NumberRangeInput::rangeInfo)
            )
            .apply(instance, NumberRangeInput::new)
    );

    @Override
    public MapCodec<NumberRangeInput> mapCodec() {
        return MAP_CODEC;
    }

    public Component computeLabel(String value) {
        return Component.translatable(this.labelFormat, this.label, value);
    }

    public record RangeInfo(float start, float end, Optional<Float> initial, Optional<Float> step) {
        public static final MapCodec<NumberRangeInput.RangeInfo> MAP_CODEC = RecordCodecBuilder.<NumberRangeInput.RangeInfo>mapCodec(
                instance -> instance.group(
                        Codec.FLOAT.fieldOf("start").forGetter(NumberRangeInput.RangeInfo::start),
                        Codec.FLOAT.fieldOf("end").forGetter(NumberRangeInput.RangeInfo::end),
                        Codec.FLOAT.optionalFieldOf("initial").forGetter(NumberRangeInput.RangeInfo::initial),
                        ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("step").forGetter(NumberRangeInput.RangeInfo::step)
                    )
                    .apply(instance, NumberRangeInput.RangeInfo::new)
            )
            .validate(rangeInfo -> {
                if (rangeInfo.initial.isPresent()) {
                    double d = rangeInfo.initial.get().floatValue();
                    double d1 = Math.min(rangeInfo.start, rangeInfo.end);
                    double d2 = Math.max(rangeInfo.start, rangeInfo.end);
                    if (d < d1 || d > d2) {
                        return DataResult.error(() -> "Initial value " + d + " is outside of range [" + d1 + ", " + d2 + "]");
                    }
                }

                return DataResult.success(rangeInfo);
            });

        public float computeScaledValue(float value) {
            float f = Mth.lerp(value, this.start, this.end);
            if (this.step.isEmpty()) {
                return f;
            } else {
                float f1 = this.step.get();
                float f2 = this.initialScaledValue();
                float f3 = f - f2;
                int rounded = Math.round(f3 / f1);
                float f4 = f2 + rounded * f1;
                if (!this.isOutOfRange(f4)) {
                    return f4;
                } else {
                    int i = rounded - Mth.sign(rounded);
                    return f2 + i * f1;
                }
            }
        }

        private boolean isOutOfRange(float value) {
            float f = this.scaledValueToSlider(value);
            return f < 0.0 || f > 1.0;
        }

        private float initialScaledValue() {
            return this.initial.isPresent() ? this.initial.get() : (this.start + this.end) / 2.0F;
        }

        public float initialSliderValue() {
            float f = this.initialScaledValue();
            return this.scaledValueToSlider(f);
        }

        private float scaledValueToSlider(float value) {
            return this.start == this.end ? 0.5F : Mth.inverseLerp(value, this.start, this.end);
        }
    }
}
