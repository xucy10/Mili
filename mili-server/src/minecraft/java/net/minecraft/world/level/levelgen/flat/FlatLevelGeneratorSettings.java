package net.minecraft.world.level.levelgen.flat;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

public class FlatLevelGeneratorSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<FlatLevelGeneratorSettings> CODEC = RecordCodecBuilder.<FlatLevelGeneratorSettings>create(
            instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.STRUCTURE_SET)
                        .lenientOptionalFieldOf("structure_overrides")
                        .forGetter(flatLevelGeneratorSettings -> flatLevelGeneratorSettings.structureOverrides),
                    FlatLayerInfo.CODEC.listOf().fieldOf("layers").forGetter(FlatLevelGeneratorSettings::getLayersInfo),
                    Codec.BOOL.fieldOf("lakes").orElse(false).forGetter(flatLevelGeneratorSettings -> flatLevelGeneratorSettings.addLakes),
                    Codec.BOOL.fieldOf("features").orElse(false).forGetter(flatLevelGeneratorSettings -> flatLevelGeneratorSettings.decoration),
                    Biome.CODEC
                        .lenientOptionalFieldOf("biome")
                        .orElseGet(Optional::empty)
                        .forGetter(flatLevelGeneratorSettings -> Optional.of(flatLevelGeneratorSettings.biome)),
                    RegistryOps.retrieveElement(Biomes.PLAINS),
                    RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND),
                    RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_SURFACE)
                )
                .apply(instance, FlatLevelGeneratorSettings::new)
        )
        .comapFlatMap(FlatLevelGeneratorSettings::validateHeight, Function.identity())
        .stable();
    private final Optional<HolderSet<StructureSet>> structureOverrides;
    private final List<FlatLayerInfo> layersInfo = Lists.newArrayList();
    private final Holder<Biome> biome;
    private final List<BlockState> layers;
    private boolean voidGen;
    private boolean decoration;
    private boolean addLakes;
    private final List<Holder<PlacedFeature>> lakes;

    private static DataResult<FlatLevelGeneratorSettings> validateHeight(FlatLevelGeneratorSettings flatSettings) {
        int i = flatSettings.layersInfo.stream().mapToInt(FlatLayerInfo::getHeight).sum();
        return i > DimensionType.Y_SIZE
            ? DataResult.error(() -> "Sum of layer heights is > " + DimensionType.Y_SIZE, flatSettings)
            : DataResult.success(flatSettings);
    }

    private FlatLevelGeneratorSettings(
        Optional<HolderSet<StructureSet>> structureOverrides,
        List<FlatLayerInfo> layersInfo,
        boolean addLakes,
        boolean decoration,
        Optional<Holder<Biome>> biome,
        Holder.Reference<Biome> defaultBiome,
        Holder<PlacedFeature> lavaUnderground,
        Holder<PlacedFeature> lavaSurface
    ) {
        this(structureOverrides, getBiome(biome, defaultBiome), List.of(lavaUnderground, lavaSurface));
        if (addLakes) {
            this.setAddLakes();
        }

        if (decoration) {
            this.setDecoration();
        }

        this.layersInfo.addAll(layersInfo);
        this.updateLayers();
    }

    private static Holder<Biome> getBiome(Optional<? extends Holder<Biome>> biome, Holder<Biome> defaultBiome) {
        if (biome.isEmpty()) {
            LOGGER.error("Unknown biome, defaulting to plains");
            return defaultBiome;
        } else {
            return (Holder<Biome>)biome.get();
        }
    }

    public FlatLevelGeneratorSettings(Optional<HolderSet<StructureSet>> structureOverrides, Holder<Biome> biome, List<Holder<PlacedFeature>> lakes) {
        this.structureOverrides = structureOverrides;
        this.biome = biome;
        this.layers = Lists.newArrayList();
        this.lakes = lakes;
    }

    public FlatLevelGeneratorSettings withBiomeAndLayers(List<FlatLayerInfo> layerInfos, Optional<HolderSet<StructureSet>> structureSets, Holder<Biome> biome) {
        FlatLevelGeneratorSettings flatLevelGeneratorSettings = new FlatLevelGeneratorSettings(structureSets, biome, this.lakes);

        for (FlatLayerInfo flatLayerInfo : layerInfos) {
            flatLevelGeneratorSettings.layersInfo.add(new FlatLayerInfo(flatLayerInfo.getHeight(), flatLayerInfo.getBlockState().getBlock()));
            flatLevelGeneratorSettings.updateLayers();
        }

        if (this.decoration) {
            flatLevelGeneratorSettings.setDecoration();
        }

        if (this.addLakes) {
            flatLevelGeneratorSettings.setAddLakes();
        }

        return flatLevelGeneratorSettings;
    }

    public void setDecoration() {
        this.decoration = true;
    }

    public void setAddLakes() {
        this.addLakes = true;
    }

    public BiomeGenerationSettings adjustGenerationSettings(Holder<Biome> biome) {
        if (!biome.equals(this.biome)) {
            return biome.value().getGenerationSettings();
        } else {
            BiomeGenerationSettings generationSettings = this.getBiome().value().getGenerationSettings();
            BiomeGenerationSettings.PlainBuilder plainBuilder = new BiomeGenerationSettings.PlainBuilder();
            if (this.addLakes) {
                for (Holder<PlacedFeature> holder : this.lakes) {
                    plainBuilder.addFeature(GenerationStep.Decoration.LAKES, holder);
                }
            }

            boolean flag = (!this.voidGen || biome.is(Biomes.THE_VOID)) && this.decoration;
            if (flag) {
                List<HolderSet<PlacedFeature>> list = generationSettings.features();

                for (int i = 0; i < list.size(); i++) {
                    if (i != GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal()
                        && i != GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal()
                        && (!this.addLakes || i != GenerationStep.Decoration.LAKES.ordinal())) {
                        for (Holder<PlacedFeature> holder1 : list.get(i)) {
                            plainBuilder.addFeature(i, holder1);
                        }
                    }
                }
            }

            List<BlockState> list = this.getLayers();

            for (int ix = 0; ix < list.size(); ix++) {
                BlockState blockState = list.get(ix);
                if (!Heightmap.Types.MOTION_BLOCKING.isOpaque().test(blockState)) {
                    list.set(ix, null);
                    plainBuilder.addFeature(
                        GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                        PlacementUtils.inlinePlaced(Feature.FILL_LAYER, new LayerConfiguration(ix, blockState))
                    );
                }
            }

            return plainBuilder.build();
        }
    }

    public Optional<HolderSet<StructureSet>> structureOverrides() {
        return this.structureOverrides;
    }

    public Holder<Biome> getBiome() {
        return this.biome;
    }

    public List<FlatLayerInfo> getLayersInfo() {
        return this.layersInfo;
    }

    public List<BlockState> getLayers() {
        return this.layers;
    }

    public void updateLayers() {
        this.layers.clear();

        for (FlatLayerInfo flatLayerInfo : this.layersInfo) {
            for (int i = 0; i < flatLayerInfo.getHeight(); i++) {
                this.layers.add(flatLayerInfo.getBlockState());
            }
        }

        this.voidGen = this.layers.stream().allMatch(blockState -> blockState.is(Blocks.AIR));
    }

    public static FlatLevelGeneratorSettings getDefault(
        HolderGetter<Biome> biomes, HolderGetter<StructureSet> structureSetGetter, HolderGetter<PlacedFeature> placedFeatureGetter
    ) {
        HolderSet<StructureSet> holderSet = HolderSet.direct(
            structureSetGetter.getOrThrow(BuiltinStructureSets.STRONGHOLDS), structureSetGetter.getOrThrow(BuiltinStructureSets.VILLAGES)
        );
        FlatLevelGeneratorSettings flatLevelGeneratorSettings = new FlatLevelGeneratorSettings(
            Optional.of(holderSet), getDefaultBiome(biomes), createLakesList(placedFeatureGetter)
        );
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        flatLevelGeneratorSettings.updateLayers();
        return flatLevelGeneratorSettings;
    }

    public static Holder<Biome> getDefaultBiome(HolderGetter<Biome> biomes) {
        return biomes.getOrThrow(Biomes.PLAINS);
    }

    public static List<Holder<PlacedFeature>> createLakesList(HolderGetter<PlacedFeature> placedFeatureGetter) {
        return List.of(
            placedFeatureGetter.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND),
            placedFeatureGetter.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_SURFACE)
        );
    }
}
