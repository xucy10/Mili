package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public record AmbientMoodSettings(Holder<SoundEvent> soundEvent, int tickDelay, int blockSearchExtent, double soundPositionOffset) {
    public static final Codec<AmbientMoodSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                SoundEvent.CODEC.fieldOf("sound").forGetter(settings -> settings.soundEvent),
                Codec.INT.fieldOf("tick_delay").forGetter(settings -> settings.tickDelay),
                Codec.INT.fieldOf("block_search_extent").forGetter(settings -> settings.blockSearchExtent),
                Codec.DOUBLE.fieldOf("offset").forGetter(settings -> settings.soundPositionOffset)
            )
            .apply(instance, AmbientMoodSettings::new)
    );
    public static final AmbientMoodSettings LEGACY_CAVE_SETTINGS = new AmbientMoodSettings(SoundEvents.AMBIENT_CAVE, 6000, 8, 2.0);
}
