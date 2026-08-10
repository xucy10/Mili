package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Brightness(int block, int sky) {
    public static final Codec<Integer> LIGHT_VALUE_CODEC = ExtraCodecs.intRange(0, 15);
    public static final Codec<Brightness> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(LIGHT_VALUE_CODEC.fieldOf("block").forGetter(Brightness::block), LIGHT_VALUE_CODEC.fieldOf("sky").forGetter(Brightness::sky))
            .apply(instance, Brightness::new)
    );
    public static final Brightness FULL_BRIGHT = new Brightness(15, 15);

    public static int pack(int block, int sky) {
        return block << 4 | sky << 20;
    }

    public int pack() {
        return pack(this.block, this.sky);
    }

    public static int block(int packed) {
        return packed >> 4 & 65535;
    }

    public static int sky(int packed) {
        return packed >> 20 & 65535;
    }

    public static Brightness unpack(int packedBrightness) {
        return new Brightness(block(packedBrightness), sky(packedBrightness));
    }
}
