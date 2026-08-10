package net.minecraft.world.level.levelgen.feature.configurations;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;

public class TreeConfiguration implements FeatureConfiguration {
    public static final Codec<TreeConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("trunk_provider").forGetter(config -> config.trunkProvider),
                TrunkPlacer.CODEC.fieldOf("trunk_placer").forGetter(config -> config.trunkPlacer),
                BlockStateProvider.CODEC.fieldOf("foliage_provider").forGetter(config -> config.foliageProvider),
                FoliagePlacer.CODEC.fieldOf("foliage_placer").forGetter(config -> config.foliagePlacer),
                RootPlacer.CODEC.optionalFieldOf("root_placer").forGetter(config -> config.rootPlacer),
                BlockStateProvider.CODEC.fieldOf("dirt_provider").forGetter(config -> config.dirtProvider),
                FeatureSize.CODEC.fieldOf("minimum_size").forGetter(config -> config.minimumSize),
                TreeDecorator.CODEC.listOf().fieldOf("decorators").forGetter(config -> config.decorators),
                Codec.BOOL.fieldOf("ignore_vines").orElse(false).forGetter(config -> config.ignoreVines),
                Codec.BOOL.fieldOf("force_dirt").orElse(false).forGetter(config -> config.forceDirt)
            )
            .apply(instance, TreeConfiguration::new)
    );
    public final BlockStateProvider trunkProvider;
    public final BlockStateProvider dirtProvider;
    public final TrunkPlacer trunkPlacer;
    public final BlockStateProvider foliageProvider;
    public final FoliagePlacer foliagePlacer;
    public final Optional<RootPlacer> rootPlacer;
    public final FeatureSize minimumSize;
    public final List<TreeDecorator> decorators;
    public final boolean ignoreVines;
    public final boolean forceDirt;

    protected TreeConfiguration(
        BlockStateProvider trunkProvider,
        TrunkPlacer trunkPlacer,
        BlockStateProvider foliageProvider,
        FoliagePlacer foliagePlacer,
        Optional<RootPlacer> rootPlacer,
        BlockStateProvider dirtProvider,
        FeatureSize minimumSize,
        List<TreeDecorator> decorators,
        boolean ignoreVines,
        boolean forceDirt
    ) {
        this.trunkProvider = trunkProvider;
        this.trunkPlacer = trunkPlacer;
        this.foliageProvider = foliageProvider;
        this.foliagePlacer = foliagePlacer;
        this.rootPlacer = rootPlacer;
        this.dirtProvider = dirtProvider;
        this.minimumSize = minimumSize;
        this.decorators = decorators;
        this.ignoreVines = ignoreVines;
        this.forceDirt = forceDirt;
    }

    public static class TreeConfigurationBuilder {
        public final BlockStateProvider trunkProvider;
        private final TrunkPlacer trunkPlacer;
        public final BlockStateProvider foliageProvider;
        private final FoliagePlacer foliagePlacer;
        private final Optional<RootPlacer> rootPlacer;
        private BlockStateProvider dirtProvider;
        private final FeatureSize minimumSize;
        private List<TreeDecorator> decorators = ImmutableList.of();
        private boolean ignoreVines;
        private boolean forceDirt;

        public TreeConfigurationBuilder(
            BlockStateProvider trunkProvider,
            TrunkPlacer trunkPlacer,
            BlockStateProvider foliageProvider,
            FoliagePlacer foliagePlacer,
            Optional<RootPlacer> rootPlacer,
            FeatureSize minimumSize
        ) {
            this.trunkProvider = trunkProvider;
            this.trunkPlacer = trunkPlacer;
            this.foliageProvider = foliageProvider;
            this.dirtProvider = BlockStateProvider.simple(Blocks.DIRT);
            this.foliagePlacer = foliagePlacer;
            this.rootPlacer = rootPlacer;
            this.minimumSize = minimumSize;
        }

        public TreeConfigurationBuilder(
            BlockStateProvider trunkProvider, TrunkPlacer trunkPlacer, BlockStateProvider foliageProvider, FoliagePlacer foliagePlacer, FeatureSize minimumSize
        ) {
            this(trunkProvider, trunkPlacer, foliageProvider, foliagePlacer, Optional.empty(), minimumSize);
        }

        public TreeConfiguration.TreeConfigurationBuilder dirt(BlockStateProvider dirtProvider) {
            this.dirtProvider = dirtProvider;
            return this;
        }

        public TreeConfiguration.TreeConfigurationBuilder decorators(List<TreeDecorator> decorators) {
            this.decorators = decorators;
            return this;
        }

        public TreeConfiguration.TreeConfigurationBuilder ignoreVines() {
            this.ignoreVines = true;
            return this;
        }

        public TreeConfiguration.TreeConfigurationBuilder forceDirt() {
            this.forceDirt = true;
            return this;
        }

        public TreeConfiguration build() {
            return new TreeConfiguration(
                this.trunkProvider,
                this.trunkPlacer,
                this.foliageProvider,
                this.foliagePlacer,
                this.rootPlacer,
                this.dirtProvider,
                this.minimumSize,
                this.decorators,
                this.ignoreVines,
                this.forceDirt
            );
        }
    }
}
