package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SculkChargeParticleOptions(float roll) implements ParticleOptions {
    public static final MapCodec<SculkChargeParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.FLOAT.fieldOf("roll").forGetter(sculkOptions -> sculkOptions.roll)).apply(instance, SculkChargeParticleOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SculkChargeParticleOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, particleOptions -> particleOptions.roll, SculkChargeParticleOptions::new
    );

    @Override
    public ParticleType<SculkChargeParticleOptions> getType() {
        return ParticleTypes.SCULK_CHARGE;
    }
}
