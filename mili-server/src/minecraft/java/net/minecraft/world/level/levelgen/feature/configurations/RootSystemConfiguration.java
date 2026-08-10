package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RootSystemConfiguration implements FeatureConfiguration {
    public static final Codec<RootSystemConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter(config -> config.treeFeature),
                Codec.intRange(1, 64).fieldOf("required_vertical_space_for_tree").forGetter(config -> config.requiredVerticalSpaceForTree),
                Codec.intRange(1, 64).fieldOf("root_radius").forGetter(config -> config.rootRadius),
                TagKey.hashedCodec(Registries.BLOCK).fieldOf("root_replaceable").forGetter(config -> config.rootReplaceable),
                BlockStateProvider.CODEC.fieldOf("root_state_provider").forGetter(config -> config.rootStateProvider),
                Codec.intRange(1, 256).fieldOf("root_placement_attempts").forGetter(config -> config.rootPlacementAttempts),
                Codec.intRange(1, 4096).fieldOf("root_column_max_height").forGetter(config -> config.rootColumnMaxHeight),
                Codec.intRange(1, 64).fieldOf("hanging_root_radius").forGetter(config -> config.hangingRootRadius),
                Codec.intRange(1, 16).fieldOf("hanging_roots_vertical_span").forGetter(config -> config.hangingRootsVerticalSpan),
                BlockStateProvider.CODEC.fieldOf("hanging_root_state_provider").forGetter(config -> config.hangingRootStateProvider),
                Codec.intRange(1, 256).fieldOf("hanging_root_placement_attempts").forGetter(config -> config.hangingRootPlacementAttempts),
                Codec.intRange(1, 64).fieldOf("allowed_vertical_water_for_tree").forGetter(config -> config.allowedVerticalWaterForTree),
                BlockPredicate.CODEC.fieldOf("allowed_tree_position").forGetter(config -> config.allowedTreePosition)
            )
            .apply(instance, RootSystemConfiguration::new)
    );
    public final Holder<PlacedFeature> treeFeature;
    public final int requiredVerticalSpaceForTree;
    public final int rootRadius;
    public final TagKey<Block> rootReplaceable;
    public final BlockStateProvider rootStateProvider;
    public final int rootPlacementAttempts;
    public final int rootColumnMaxHeight;
    public final int hangingRootRadius;
    public final int hangingRootsVerticalSpan;
    public final BlockStateProvider hangingRootStateProvider;
    public final int hangingRootPlacementAttempts;
    public final int allowedVerticalWaterForTree;
    public final BlockPredicate allowedTreePosition;

    public RootSystemConfiguration(
        Holder<PlacedFeature> treeFeature,
        int requiredVerticalSpaceForTree,
        int rootRadius,
        TagKey<Block> rootReplaceable,
        BlockStateProvider rootStateProvider,
        int rootPlacementAttempts,
        int rootColumnMaxHeight,
        int hangingRootRadius,
        int hangingRootsVerticalSpan,
        BlockStateProvider hangingRootStateProvider,
        int hangingRootPlacementAttempts,
        int allowedVerticalWaterForTree,
        BlockPredicate allowedTreePosition
    ) {
        this.treeFeature = treeFeature;
        this.requiredVerticalSpaceForTree = requiredVerticalSpaceForTree;
        this.rootRadius = rootRadius;
        this.rootReplaceable = rootReplaceable;
        this.rootStateProvider = rootStateProvider;
        this.rootPlacementAttempts = rootPlacementAttempts;
        this.rootColumnMaxHeight = rootColumnMaxHeight;
        this.hangingRootRadius = hangingRootRadius;
        this.hangingRootsVerticalSpan = hangingRootsVerticalSpan;
        this.hangingRootStateProvider = hangingRootStateProvider;
        this.hangingRootPlacementAttempts = hangingRootPlacementAttempts;
        this.allowedVerticalWaterForTree = allowedVerticalWaterForTree;
        this.allowedTreePosition = allowedTreePosition;
    }
}
