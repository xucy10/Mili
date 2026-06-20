package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public class EndGatewayConfiguration implements FeatureConfiguration {
    public static final Codec<EndGatewayConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockPos.CODEC.optionalFieldOf("exit").forGetter(config -> config.exit), Codec.BOOL.fieldOf("exact").forGetter(config -> config.exact)
            )
            .apply(instance, EndGatewayConfiguration::new)
    );
    private final Optional<BlockPos> exit;
    private final boolean exact;

    private EndGatewayConfiguration(Optional<BlockPos> exit, boolean exact) {
        this.exit = exit;
        this.exact = exact;
    }

    public static EndGatewayConfiguration knownExit(BlockPos exit, boolean exact) {
        return new EndGatewayConfiguration(Optional.of(exit), exact);
    }

    public static EndGatewayConfiguration delayedExitSearch() {
        return new EndGatewayConfiguration(Optional.empty(), false);
    }

    public Optional<BlockPos> getExit() {
        return this.exit;
    }

    public boolean isExitExact() {
        return this.exact;
    }
}
