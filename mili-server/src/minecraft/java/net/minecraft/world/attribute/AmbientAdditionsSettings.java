package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public record AmbientAdditionsSettings(Holder<SoundEvent> soundEvent, double tickChance) {
    public static final Codec<AmbientAdditionsSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                SoundEvent.CODEC.fieldOf("sound").forGetter(settings -> settings.soundEvent),
                Codec.DOUBLE.fieldOf("tick_chance").forGetter(settings -> settings.tickChance)
            )
            .apply(instance, AmbientAdditionsSettings::new)
    );
}
