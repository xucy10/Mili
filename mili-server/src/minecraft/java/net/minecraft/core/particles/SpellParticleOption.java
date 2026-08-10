package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;

public class SpellParticleOption implements ParticleOptions {
    private final ParticleType<SpellParticleOption> type;
    public final int color; // Paper - public
    private final float power;

    public static MapCodec<SpellParticleOption> codec(ParticleType<SpellParticleOption> type) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color", -1).forGetter(spellParticleOption -> spellParticleOption.color),
                    Codec.FLOAT.optionalFieldOf("power", 1.0F).forGetter(spellParticleOption -> spellParticleOption.power)
                )
                .apply(instance, (color, power) -> new SpellParticleOption(type, color, power))
        );
    }

    public static StreamCodec<? super ByteBuf, SpellParticleOption> streamCodec(ParticleType<SpellParticleOption> type) {
        return StreamCodec.composite(
            ByteBufCodecs.INT,
            spellParticleOption -> spellParticleOption.color,
            ByteBufCodecs.FLOAT,
            spellParticleOption -> spellParticleOption.power,
            (color, power) -> new SpellParticleOption(type, color, power)
        );
    }

    private SpellParticleOption(ParticleType<SpellParticleOption> type, int color, float power) {
        this.type = type;
        this.color = color;
        this.power = power;
    }

    @Override
    public ParticleType<SpellParticleOption> getType() {
        return this.type;
    }

    public float getRed() {
        return ARGB.red(this.color) / 255.0F;
    }

    public float getGreen() {
        return ARGB.green(this.color) / 255.0F;
    }

    public float getBlue() {
        return ARGB.blue(this.color) / 255.0F;
    }

    public float getPower() {
        return this.power;
    }

    public static SpellParticleOption create(ParticleType<SpellParticleOption> type, int color, float power) {
        return new SpellParticleOption(type, color, power);
    }

    public static SpellParticleOption create(ParticleType<SpellParticleOption> type, float red, float green, float blue, float power) {
        return create(type, ARGB.colorFromFloat(1.0F, red, green, blue), power);
    }
}
