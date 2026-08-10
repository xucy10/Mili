package net.minecraft.world.level.block.grower;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jspecify.annotations.Nullable;

public final class TreeGrower {
    private static final Map<String, TreeGrower> GROWERS = new Object2ObjectArrayMap<>();
    public static final Codec<TreeGrower> CODEC = Codec.stringResolver(treeGrower -> treeGrower.name, GROWERS::get);
    public static final TreeGrower OAK = new TreeGrower(
        "oak",
        0.1F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.OAK),
        Optional.of(TreeFeatures.FANCY_OAK),
        Optional.of(TreeFeatures.OAK_BEES_005),
        Optional.of(TreeFeatures.FANCY_OAK_BEES_005)
    );
    public static final TreeGrower SPRUCE = new TreeGrower(
        "spruce",
        0.5F,
        Optional.of(TreeFeatures.MEGA_SPRUCE),
        Optional.of(TreeFeatures.MEGA_PINE),
        Optional.of(TreeFeatures.SPRUCE),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower MANGROVE = new TreeGrower(
        "mangrove",
        0.85F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.MANGROVE),
        Optional.of(TreeFeatures.TALL_MANGROVE),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower AZALEA = new TreeGrower("azalea", Optional.empty(), Optional.of(TreeFeatures.AZALEA_TREE), Optional.empty());
    public static final TreeGrower BIRCH = new TreeGrower("birch", Optional.empty(), Optional.of(TreeFeatures.BIRCH), Optional.of(TreeFeatures.BIRCH_BEES_005));
    public static final TreeGrower JUNGLE = new TreeGrower(
        "jungle", Optional.of(TreeFeatures.MEGA_JUNGLE_TREE), Optional.of(TreeFeatures.JUNGLE_TREE_NO_VINE), Optional.empty()
    );
    public static final TreeGrower ACACIA = new TreeGrower("acacia", Optional.empty(), Optional.of(TreeFeatures.ACACIA), Optional.empty());
    public static final TreeGrower CHERRY = new TreeGrower(
        "cherry", Optional.empty(), Optional.of(TreeFeatures.CHERRY), Optional.of(TreeFeatures.CHERRY_BEES_005)
    );
    public static final TreeGrower DARK_OAK = new TreeGrower("dark_oak", Optional.of(TreeFeatures.DARK_OAK), Optional.empty(), Optional.empty());
    public static final TreeGrower PALE_OAK = new TreeGrower("pale_oak", Optional.of(TreeFeatures.PALE_OAK_BONEMEAL), Optional.empty(), Optional.empty());
    private final String name;
    private final float secondaryChance;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers;

    public TreeGrower(
        String name,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers
    ) {
        this(name, 0.0F, megaTree, Optional.empty(), tree, Optional.empty(), flowers, Optional.empty());
    }

    public TreeGrower(
        String name,
        float secondaryChance,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers
    ) {
        this.name = name;
        this.secondaryChance = secondaryChance;
        this.megaTree = megaTree;
        this.secondaryMegaTree = secondaryMegaTree;
        this.tree = tree;
        this.secondaryTree = secondaryTree;
        this.flowers = flowers;
        this.secondaryFlowers = secondaryFlowers;
        GROWERS.put(name, this);
    }

    private @Nullable ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
        if (random.nextFloat() < this.secondaryChance) {
            if (flowers && this.secondaryFlowers.isPresent()) {
                return this.secondaryFlowers.get();
            }

            if (this.secondaryTree.isPresent()) {
                return this.secondaryTree.get();
            }
        }

        return flowers && this.flowers.isPresent() ? this.flowers.get() : this.tree.orElse(null);
    }

    private @Nullable ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) {
        return this.secondaryMegaTree.isPresent() && random.nextFloat() < this.secondaryChance ? this.secondaryMegaTree.get() : this.megaTree.orElse(null);
    }

    public boolean growTree(ServerLevel level, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> configuredMegaFeature = this.getConfiguredMegaFeature(random);
        if (configuredMegaFeature != null) {
            Holder<ConfiguredFeature<?, ?>> holder = level.registryAccess()
                .lookupOrThrow(Registries.CONFIGURED_FEATURE)
                .get(configuredMegaFeature)
                .orElse(null);
            if (holder != null) {
                this.setTreeType(holder); // CraftBukkit
                for (int i = 0; i >= -1; i--) {
                    for (int i1 = 0; i1 >= -1; i1--) {
                        if (isTwoByTwoSapling(state, level, pos, i, i1)) {
                            ConfiguredFeature<?, ?> configuredFeature = holder.value();
                            BlockState blockState = Blocks.AIR.defaultBlockState();
                            level.setBlock(pos.offset(i, 0, i1), blockState, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i + 1, 0, i1), blockState, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i, 0, i1 + 1), blockState, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i + 1, 0, i1 + 1), blockState, Block.UPDATE_NONE);
                            if (configuredFeature.place(level, chunkGenerator, random, pos.offset(i, 0, i1))) {
                                return true;
                            }

                            level.setBlock(pos.offset(i, 0, i1), state, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i + 1, 0, i1), state, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i, 0, i1 + 1), state, Block.UPDATE_NONE);
                            level.setBlock(pos.offset(i + 1, 0, i1 + 1), state, Block.UPDATE_NONE);
                            return false;
                        }
                    }
                }
            }
        }

        ResourceKey<ConfiguredFeature<?, ?>> configuredFeature1 = this.getConfiguredFeature(random, this.hasFlowers(level, pos));
        if (configuredFeature1 == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder1 = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(configuredFeature1).orElse(null);
            if (holder1 == null) {
                return false;
            } else {
                this.setTreeType(holder1); // CraftBukkit
                ConfiguredFeature<?, ?> configuredFeature2 = holder1.value();
                BlockState blockState1 = level.getFluidState(pos).createLegacyBlock();
                level.setBlock(pos, blockState1, Block.UPDATE_NONE);
                if (configuredFeature2.place(level, chunkGenerator, random, pos)) {
                    if (level.getBlockState(pos) == blockState1) {
                        level.sendBlockUpdated(pos, state, blockState1, Block.UPDATE_CLIENTS);
                    }

                    return true;
                } else {
                    level.setBlock(pos, state, Block.UPDATE_NONE);
                    return false;
                }
            }
        }
    }

    private static boolean isTwoByTwoSapling(BlockState state, BlockGetter level, BlockPos pos, int xOffset, int yOffset) {
        Block block = state.getBlock();
        return level.getBlockState(pos.offset(xOffset, 0, yOffset)).is(block)
            && level.getBlockState(pos.offset(xOffset + 1, 0, yOffset)).is(block)
            && level.getBlockState(pos.offset(xOffset, 0, yOffset + 1)).is(block)
            && level.getBlockState(pos.offset(xOffset + 1, 0, yOffset + 1)).is(block);
    }

    private boolean hasFlowers(LevelAccessor level, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.MutableBlockPos.betweenClosed(pos.below().north(2).west(2), pos.above().south(2).east(2))) {
            if (level.getBlockState(blockPos).is(BlockTags.FLOWERS)) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start
    private void setTreeType(Holder<ConfiguredFeature<?, ?>> feature) {
        org.bukkit.TreeType treeType; // Folia - region threading
        if (feature.is(TreeFeatures.OAK) || feature.is(TreeFeatures.OAK_BEES_005)) {
            treeType = org.bukkit.TreeType.TREE; // Folia - region threading
        } else if (feature.is(TreeFeatures.HUGE_RED_MUSHROOM)) {
            treeType = org.bukkit.TreeType.RED_MUSHROOM; // Folia - region threading
        } else if (feature.is(TreeFeatures.HUGE_BROWN_MUSHROOM)) {
            treeType = org.bukkit.TreeType.BROWN_MUSHROOM; // Folia - region threading
        } else if (feature.is(TreeFeatures.JUNGLE_TREE)) {
            treeType = org.bukkit.TreeType.COCOA_TREE; // Folia - region threading
        } else if (feature.is(TreeFeatures.JUNGLE_TREE_NO_VINE)) {
            treeType = org.bukkit.TreeType.SMALL_JUNGLE; // Folia - region threading
        } else if (feature.is(TreeFeatures.PINE)) {
            treeType = org.bukkit.TreeType.TALL_REDWOOD; // Folia - region threading
        } else if (feature.is(TreeFeatures.SPRUCE)) {
            treeType = org.bukkit.TreeType.REDWOOD; // Folia - region threading
        } else if (feature.is(TreeFeatures.ACACIA)) {
            treeType = org.bukkit.TreeType.ACACIA; // Folia - region threading
        } else if (feature.is(TreeFeatures.BIRCH) || feature.is(TreeFeatures.BIRCH_BEES_005)) {
            treeType = org.bukkit.TreeType.BIRCH; // Folia - region threading
        } else if (feature.is(TreeFeatures.SUPER_BIRCH_BEES_0002)) {
            treeType = org.bukkit.TreeType.TALL_BIRCH; // Folia - region threading
        } else if (feature.is(TreeFeatures.SWAMP_OAK)) {
            treeType = org.bukkit.TreeType.SWAMP; // Folia - region threading
        } else if (feature.is(TreeFeatures.FANCY_OAK) || feature.is(TreeFeatures.FANCY_OAK_BEES_005)) {
            treeType = org.bukkit.TreeType.BIG_TREE; // Folia - region threading
        } else if (feature.is(TreeFeatures.JUNGLE_BUSH)) {
            treeType = org.bukkit.TreeType.JUNGLE_BUSH; // Folia - region threading
        } else if (feature.is(TreeFeatures.DARK_OAK)) {
            treeType = org.bukkit.TreeType.DARK_OAK; // Folia - region threading
        } else if (feature.is(TreeFeatures.MEGA_SPRUCE)) {
            treeType = org.bukkit.TreeType.MEGA_REDWOOD; // Folia - region threading
        } else if (feature.is(TreeFeatures.MEGA_PINE)) {
            treeType = org.bukkit.TreeType.MEGA_PINE; // Folia - region threading
        } else if (feature.is(TreeFeatures.MEGA_JUNGLE_TREE)) {
            treeType = org.bukkit.TreeType.JUNGLE; // Folia - region threading
        } else if (feature.is(TreeFeatures.AZALEA_TREE)) {
            treeType = org.bukkit.TreeType.AZALEA; // Folia - region threading
        } else if (feature.is(TreeFeatures.MANGROVE)) {
            treeType = org.bukkit.TreeType.MANGROVE; // Folia - region threading
        } else if (feature.is(TreeFeatures.TALL_MANGROVE)) {
            treeType = org.bukkit.TreeType.TALL_MANGROVE; // Folia - region threading
        } else if (feature.is(TreeFeatures.CHERRY) || feature.is(TreeFeatures.CHERRY_BEES_005)) {
            treeType = org.bukkit.TreeType.CHERRY; // Folia - region threading
        } else if (feature.is(TreeFeatures.PALE_OAK) || feature.is(TreeFeatures.PALE_OAK_BONEMEAL)) {
            treeType = org.bukkit.TreeType.PALE_OAK; // Folia - region threading
        } else if (feature.is(TreeFeatures.PALE_OAK_CREAKING)) {
            treeType = org.bukkit.TreeType.PALE_OAK_CREAKING; // Folia - region threading
        } else {
            throw new IllegalArgumentException("Unknown tree generator " + feature);
        }
        net.minecraft.world.level.block.SaplingBlock.treeTypeRT.set(treeType); // Folia - region threading
    }
    // CraftBukkit end
}
