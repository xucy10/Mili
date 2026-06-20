package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import org.jspecify.annotations.Nullable;

public class SpikeConfiguration implements FeatureConfiguration {
    public static final Codec<SpikeConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.BOOL.fieldOf("crystal_invulnerable").orElse(false).forGetter(config -> config.crystalInvulnerable),
                SpikeFeature.EndSpike.CODEC.listOf().fieldOf("spikes").forGetter(config -> config.spikes),
                BlockPos.CODEC.optionalFieldOf("crystal_beam_target").forGetter(config -> Optional.ofNullable(config.crystalBeamTarget))
            )
            .apply(instance, SpikeConfiguration::new)
    );
    private final boolean crystalInvulnerable;
    private final List<SpikeFeature.EndSpike> spikes;
    private final @Nullable BlockPos crystalBeamTarget;

    public SpikeConfiguration(boolean crystalInvulnerable, List<SpikeFeature.EndSpike> spikes, @Nullable BlockPos crystalBeamTarget) {
        this(crystalInvulnerable, spikes, Optional.ofNullable(crystalBeamTarget));
    }

    private SpikeConfiguration(boolean crystalInvulnerable, List<SpikeFeature.EndSpike> spikes, Optional<BlockPos> crystalBeamTarget) {
        this.crystalInvulnerable = crystalInvulnerable;
        this.spikes = spikes;
        this.crystalBeamTarget = crystalBeamTarget.orElse(null);
    }

    public boolean isCrystalInvulnerable() {
        return this.crystalInvulnerable;
    }

    public List<SpikeFeature.EndSpike> getSpikes() {
        return this.spikes;
    }

    public @Nullable BlockPos getCrystalBeamTarget() {
        return this.crystalBeamTarget;
    }
}
