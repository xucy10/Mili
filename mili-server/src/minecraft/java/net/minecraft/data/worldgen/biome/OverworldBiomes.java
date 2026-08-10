package net.minecraft.data.worldgen.biome;

import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.placement.AquaticPlacements;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class OverworldBiomes {
    protected static final int NORMAL_WATER_COLOR = 4159204;
    private static final int DARK_DRY_FOLIAGE_COLOR = 8082228;
    public static final int SWAMP_SKELETON_WEIGHT = 70;

    public static int calculateSkyColor(float temperature) {
        float f = temperature / 3.0F;
        f = Mth.clamp(f, -1.0F, 1.0F);
        return ARGB.opaque(Mth.hsvToRgb(0.62222224F - f * 0.05F, 0.5F + f * 0.1F, 1.0F));
    }

    private static Biome.BiomeBuilder baseBiome(float temperature, float downfall) {
        return new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(temperature)
            .downfall(downfall)
            .setAttribute(EnvironmentAttributes.SKY_COLOR, calculateSkyColor(temperature))
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(4159204).build());
    }

    private static void globalOverworldGeneration(BiomeGenerationSettings.Builder generationSettings) {
        BiomeDefaultFeatures.addDefaultCarversAndLakes(generationSettings);
        BiomeDefaultFeatures.addDefaultCrystalFormations(generationSettings);
        BiomeDefaultFeatures.addDefaultMonsterRoom(generationSettings);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(generationSettings);
        BiomeDefaultFeatures.addDefaultSprings(generationSettings);
        BiomeDefaultFeatures.addSurfaceFreezing(generationSettings);
    }

    public static Biome oldGrowthTaiga(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isSpruce) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4));
        builder.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3));
        builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
        if (isSpruce) {
            BiomeDefaultFeatures.commonSpawns(builder);
        } else {
            BiomeDefaultFeatures.caveSpawns(builder);
            BiomeDefaultFeatures.monsters(builder, 100, 25, 0, 100, false);
        }

        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addMossyStoneBlock(builder1);
        BiomeDefaultFeatures.addFerns(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        builder1.addFeature(
            GenerationStep.Decoration.VEGETAL_DECORATION,
            isSpruce ? VegetationPlacements.TREES_OLD_GROWTH_SPRUCE_TAIGA : VegetationPlacements.TREES_OLD_GROWTH_PINE_TAIGA
        );
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addGiantTaigaVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        BiomeDefaultFeatures.addCommonBerryBushes(builder1);
        return baseBiome(isSpruce ? 0.25F : 0.3F, 0.8F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_OLD_GROWTH_TAIGA))
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome sparseJungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 2, 4));
        return baseJungle(placedFeatures, worldCarvers, 0.8F, false, true, false)
            .mobSpawnSettings(builder.build())
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SPARSE_JUNGLE))
            .build();
    }

    public static Biome jungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, 40, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 1, 2))
            .addSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 1, 3))
            .addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 1, 2));
        return baseJungle(placedFeatures, worldCarvers, 0.9F, false, false, true)
            .mobSpawnSettings(builder.build())
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_JUNGLE))
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .build();
    }

    public static Biome bambooJungle(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.baseJungleSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, 40, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 1, 2))
            .addSpawn(MobCategory.CREATURE, 80, new MobSpawnSettings.SpawnerData(EntityType.PANDA, 1, 2))
            .addSpawn(MobCategory.MONSTER, 2, new MobSpawnSettings.SpawnerData(EntityType.OCELOT, 1, 1));
        return baseJungle(placedFeatures, worldCarvers, 0.9F, true, false, true)
            .mobSpawnSettings(builder.build())
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_BAMBOO_JUNGLE))
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .build();
    }

    private static Biome.BiomeBuilder baseJungle(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        float downfall,
        boolean isBambooJungle,
        boolean isSparse,
        boolean addBamboo
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isBambooJungle) {
            BiomeDefaultFeatures.addBambooVegetation(builder);
        } else {
            if (addBamboo) {
                BiomeDefaultFeatures.addLightBambooVegetation(builder);
            }

            if (isSparse) {
                BiomeDefaultFeatures.addSparseJungleTrees(builder);
            } else {
                BiomeDefaultFeatures.addJungleTrees(builder);
            }
        }

        BiomeDefaultFeatures.addWarmFlowers(builder);
        BiomeDefaultFeatures.addJungleGrass(builder);
        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
        BiomeDefaultFeatures.addJungleVines(builder);
        if (isSparse) {
            BiomeDefaultFeatures.addSparseJungleMelons(builder);
        } else {
            BiomeDefaultFeatures.addJungleMelons(builder);
        }

        return baseBiome(0.95F, downfall).generationSettings(builder.build());
    }

    public static Biome windsweptHills(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isForest) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 4, 6));
        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (isForest) {
            BiomeDefaultFeatures.addMountainForestTrees(builder1);
        } else {
            BiomeDefaultFeatures.addMountainTrees(builder1);
        }

        BiomeDefaultFeatures.addBushes(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        BiomeDefaultFeatures.addExtraEmeralds(builder1);
        BiomeDefaultFeatures.addInfestedStone(builder1);
        return baseBiome(0.2F, 0.3F).mobSpawnSettings(builder.build()).generationSettings(builder1.build()).build();
    }

    public static Biome desert(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.desertSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDesertVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDesertExtraVegetation(builder1);
        BiomeDefaultFeatures.addDesertExtraDecoration(builder1);
        return baseBiome(2.0F, 0.0F)
            .hasPrecipitation(false)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DESERT))
            .setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome plains(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        boolean isSunflowerPlains,
        boolean isCold,
        boolean isIceSpikes
    ) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        if (isCold) {
            builder.creatureGenerationProbability(0.07F);
            BiomeDefaultFeatures.snowySpawns(builder, !isIceSpikes);
            if (isIceSpikes) {
                builder1.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_SPIKE);
                builder1.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, MiscOverworldPlacements.ICE_PATCH);
            }
        } else {
            BiomeDefaultFeatures.plainsSpawns(builder);
            BiomeDefaultFeatures.addPlainGrass(builder1);
            if (isSunflowerPlains) {
                builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_SUNFLOWER);
            } else {
                BiomeDefaultFeatures.addBushes(builder1);
            }
        }

        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (isCold) {
            BiomeDefaultFeatures.addSnowyTrees(builder1);
            BiomeDefaultFeatures.addDefaultFlowers(builder1);
            BiomeDefaultFeatures.addDefaultGrass(builder1);
        } else {
            BiomeDefaultFeatures.addPlainVegetation(builder1);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        return baseBiome(isCold ? 0.0F : 0.8F, isCold ? 0.5F : 0.4F).mobSpawnSettings(builder.build()).generationSettings(builder1.build()).build();
    }

    public static Biome mushroomFields(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.mooshroomSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addMushroomFieldVegetation(builder1);
        BiomeDefaultFeatures.addNearWaterVegetation(builder1);
        return baseBiome(0.9F, 1.0F)
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .setAttribute(EnvironmentAttributes.CAN_PILLAGER_PATROL_SPAWN, false)
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome savanna(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isShatteredSavanna, boolean isPlateau
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        if (!isShatteredSavanna) {
            BiomeDefaultFeatures.addSavannaGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isShatteredSavanna) {
            BiomeDefaultFeatures.addShatteredSavannaTrees(builder);
            BiomeDefaultFeatures.addDefaultFlowers(builder);
            BiomeDefaultFeatures.addShatteredSavannaGrass(builder);
        } else {
            BiomeDefaultFeatures.addSavannaTrees(builder);
            BiomeDefaultFeatures.addWarmFlowers(builder);
            BiomeDefaultFeatures.addSavannaExtraGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder1);
        builder1.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.HORSE, 2, 6))
            .addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.DONKEY, 1, 1))
            .addSpawn(MobCategory.CREATURE, 10, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 2, 3));
        BiomeDefaultFeatures.commonSpawnWithZombieHorse(builder1);
        if (isPlateau) {
            builder1.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.LLAMA, 4, 4));
            builder1.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 8));
        }

        return baseBiome(2.0F, 0.0F)
            .hasPrecipitation(false)
            .setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome badlands(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean trees) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.CREATURE, 6, new MobSpawnSettings.SpawnerData(EntityType.ARMADILLO, 1, 2));
        builder.creatureGenerationProbability(0.03F);
        if (trees) {
            builder.addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 8));
            builder.creatureGenerationProbability(0.04F);
        }

        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addExtraGold(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (trees) {
            BiomeDefaultFeatures.addBadlandsTrees(builder1);
        }

        BiomeDefaultFeatures.addBadlandGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addBadlandExtraVegetation(builder1);
        return baseBiome(2.0F, 0.0F)
            .hasPrecipitation(false)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_BADLANDS))
            .setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, true)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(4159204).foliageColorOverride(10387789).grassColorOverride(9470285).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    private static Biome.BiomeBuilder baseOcean() {
        return baseBiome(0.5F, 0.5F).setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD.withUnderwater(Musics.UNDER_WATER));
    }

    private static BiomeGenerationSettings.Builder baseOceanGeneration(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addWaterTrees(builder);
        BiomeDefaultFeatures.addDefaultFlowers(builder);
        BiomeDefaultFeatures.addDefaultGrass(builder);
        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
        return builder;
    }

    public static Biome coldOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.oceanSpawns(builder, 3, 4, 15);
        builder.addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5));
        builder.addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP_COLD : AquaticPlacements.SEAGRASS_COLD);
        BiomeDefaultFeatures.addColdOceanExtraVegetation(builder1);
        return baseOcean()
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(4020182).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome ocean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.oceanSpawns(builder, 1, 4, 10);
        builder.addSpawn(MobCategory.WATER_CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 1, 2))
            .addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP : AquaticPlacements.SEAGRASS_NORMAL);
        BiomeDefaultFeatures.addColdOceanExtraVegetation(builder1);
        return baseOcean().mobSpawnSettings(builder.build()).generationSettings(builder1.build()).build();
    }

    public static Biome lukeWarmOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        if (isDeep) {
            BiomeDefaultFeatures.oceanSpawns(builder, 8, 4, 8);
        } else {
            BiomeDefaultFeatures.oceanSpawns(builder, 10, 2, 15);
        }

        builder.addSpawn(MobCategory.WATER_AMBIENT, 5, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 1, 3))
            .addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8))
            .addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.DOLPHIN, 1, 2))
            .addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, isDeep ? AquaticPlacements.SEAGRASS_DEEP_WARM : AquaticPlacements.SEAGRASS_WARM);
        BiomeDefaultFeatures.addLukeWarmKelp(builder1);
        return baseOcean()
            .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -16509389)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(4566514).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome warmOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.PUFFERFISH, 1, 3))
            .addSpawn(MobCategory.WATER_CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
        BiomeDefaultFeatures.warmOceanSpawns(builder, 10, 4);
        BiomeGenerationSettings.Builder builder1 = baseOceanGeneration(placedFeatures, worldCarvers)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.WARM_OCEAN_VEGETATION)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_WARM)
            .addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEA_PICKLE);
        return baseOcean()
            .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -16507085)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(4445678).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome frozenOcean(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isDeep) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 1, 4))
            .addSpawn(MobCategory.WATER_AMBIENT, 15, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5))
            .addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.POLAR_BEAR, 1, 2))
            .addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.NAUTILUS, 1, 1));
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.MONSTER, 5, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, 1, 1));
        float f = isDeep ? 0.5F : 0.0F;
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addIcebergs(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addBlueIce(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addWaterTrees(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        return baseBiome(f, 0.5F)
            .temperatureAdjustment(Biome.TemperatureModifier.FROZEN)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(3750089).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome forest(
        HolderGetter<PlacedFeature> placedFeatures,
        HolderGetter<ConfiguredWorldCarver<?>> worldCarvers,
        boolean isBirchForest,
        boolean tallBirchTrees,
        boolean isFlowerForest
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder);
        BackgroundMusic backgroundMusic;
        if (isFlowerForest) {
            backgroundMusic = new BackgroundMusic(SoundEvents.MUSIC_BIOME_FLOWER_FOREST);
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FOREST_FLOWERS);
        } else {
            backgroundMusic = new BackgroundMusic(SoundEvents.MUSIC_BIOME_FOREST);
            BiomeDefaultFeatures.addForestFlowers(builder);
        }

        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isFlowerForest) {
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_FLOWER_FOREST);
            builder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_FLOWER_FOREST);
            BiomeDefaultFeatures.addDefaultGrass(builder);
        } else {
            if (isBirchForest) {
                BiomeDefaultFeatures.addBirchForestFlowers(builder);
                if (tallBirchTrees) {
                    BiomeDefaultFeatures.addTallBirchTrees(builder);
                } else {
                    BiomeDefaultFeatures.addBirchTrees(builder);
                }
            } else {
                BiomeDefaultFeatures.addOtherBirchTrees(builder);
            }

            BiomeDefaultFeatures.addBushes(builder);
            BiomeDefaultFeatures.addDefaultFlowers(builder);
            BiomeDefaultFeatures.addForestGrass(builder);
        }

        BiomeDefaultFeatures.addDefaultMushrooms(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, true);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder1);
        BiomeDefaultFeatures.commonSpawns(builder1);
        if (isFlowerForest) {
            builder1.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3));
        } else if (!isBirchForest) {
            builder1.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4));
        }

        return baseBiome(isBirchForest ? 0.6F : 0.7F, isBirchForest ? 0.6F : 0.8F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, backgroundMusic)
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome taiga(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        builder.addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 4, 4))
            .addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
            .addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addFerns(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addTaigaTrees(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addTaigaGrass(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        if (isCold) {
            BiomeDefaultFeatures.addRareBerryBushes(builder1);
        } else {
            BiomeDefaultFeatures.addCommonBerryBushes(builder1);
        }

        int i = isCold ? 4020182 : 4159204;
        return baseBiome(isCold ? -0.5F : 0.25F, isCold ? 0.4F : 0.8F)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(i).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome darkForest(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isPaleGarden) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        if (!isPaleGarden) {
            BiomeDefaultFeatures.farmAnimals(builder);
        }

        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        builder1.addFeature(
            GenerationStep.Decoration.VEGETAL_DECORATION,
            isPaleGarden ? VegetationPlacements.PALE_GARDEN_VEGETATION : VegetationPlacements.DARK_FOREST_VEGETATION
        );
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addForestFlowers(builder1);
        } else {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_MOSS_PATCH);
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PALE_GARDEN_FLOWERS);
        }

        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addDefaultFlowers(builder1);
        } else {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_PALE_GARDEN);
        }

        BiomeDefaultFeatures.addForestGrass(builder1);
        if (!isPaleGarden) {
            BiomeDefaultFeatures.addDefaultMushrooms(builder1);
            BiomeDefaultFeatures.addLeafLitterPatch(builder1);
        }

        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        EnvironmentAttributeMap environmentAttributeMap = EnvironmentAttributeMap.builder()
            .set(EnvironmentAttributes.SKY_COLOR, -4605511)
            .set(EnvironmentAttributes.FOG_COLOR, -8292496)
            .set(EnvironmentAttributes.WATER_FOG_COLOR, -11179648)
            .set(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY)
            .set(EnvironmentAttributes.MUSIC_VOLUME, 0.0F)
            .build();
        EnvironmentAttributeMap environmentAttributeMap1 = EnvironmentAttributeMap.builder()
            .set(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_FOREST))
            .build();
        return baseBiome(0.7F, 0.8F)
            .putAttributes(isPaleGarden ? environmentAttributeMap : environmentAttributeMap1)
            .specialEffects(
                isPaleGarden
                    ? new BiomeSpecialEffects.Builder()
                        .waterColor(7768221)
                        .grassColorOverride(7832178)
                        .foliageColorOverride(8883574)
                        .dryFoliageColorOverride(10528412)
                        .build()
                    : new BiomeSpecialEffects.Builder()
                        .waterColor(4159204)
                        .dryFoliageColorOverride(8082228)
                        .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST)
                        .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome swamp(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.farmAnimals(builder);
        BiomeDefaultFeatures.swampSpawns(builder, 70);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addSwampClayDisk(builder1);
        BiomeDefaultFeatures.addSwampVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addSwampExtraVegetation(builder1);
        builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_SWAMP);
        return baseBiome(0.8F, 0.9F)
            .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -14474473)
            .modifyAttribute(EnvironmentAttributes.WATER_FOG_END_DISTANCE, FloatModifier.MULTIPLY, 0.85F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SWAMP))
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(6388580)
                    .foliageColorOverride(6975545)
                    .dryFoliageColorOverride(8082228)
                    .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome mangroveSwamp(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.swampSpawns(builder, 70);
        builder.addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8));
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        BiomeDefaultFeatures.addFossilDecoration(builder1);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addMangroveSwampDisks(builder1);
        BiomeDefaultFeatures.addMangroveSwampVegetation(builder1);
        BiomeDefaultFeatures.addMangroveSwampExtraVegetation(builder1);
        return baseBiome(0.8F, 0.9F)
            .setAttribute(EnvironmentAttributes.FOG_COLOR, -4138753)
            .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -11699616)
            .modifyAttribute(EnvironmentAttributes.WATER_FOG_END_DISTANCE, FloatModifier.MULTIPLY, 0.85F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SWAMP))
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(3832426)
                    .foliageColorOverride(9285927)
                    .dryFoliageColorOverride(8082228)
                    .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.SWAMP)
                    .build()
            )
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome river(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder()
            .addSpawn(MobCategory.WATER_CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 1, 4))
            .addSpawn(MobCategory.WATER_AMBIENT, 5, new MobSpawnSettings.SpawnerData(EntityType.SALMON, 1, 5));
        BiomeDefaultFeatures.commonSpawns(builder);
        builder.addSpawn(MobCategory.MONSTER, isCold ? 1 : 100, new MobSpawnSettings.SpawnerData(EntityType.DROWNED, 1, 1));
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addWaterTrees(builder1);
        BiomeDefaultFeatures.addBushes(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        if (!isCold) {
            builder1.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_RIVER);
        }

        return baseBiome(isCold ? 0.0F : 0.5F, 0.5F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.OVERWORLD.withUnderwater(Musics.UNDER_WATER))
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(isCold ? 3750089 : 4159204).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome beach(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCold, boolean isStony) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        boolean flag = !isStony && !isCold;
        if (flag) {
            builder.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.TURTLE, 2, 5));
        }

        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addDefaultFlowers(builder1);
        BiomeDefaultFeatures.addDefaultGrass(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, true);
        float f;
        if (isCold) {
            f = 0.05F;
        } else if (isStony) {
            f = 0.2F;
        } else {
            f = 0.8F;
        }

        int i = isCold ? 4020182 : 4159204;
        return baseBiome(f, flag ? 0.4F : 0.3F)
            .specialEffects(new BiomeSpecialEffects.Builder().waterColor(i).build())
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome theVoid(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        builder.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, MiscOverworldPlacements.VOID_START_PLATFORM);
        return baseBiome(0.5F, 0.5F)
            .hasPrecipitation(false)
            .mobSpawnSettings(new MobSpawnSettings.Builder().build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome meadowOrCherryGrove(
        HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers, boolean isCherryGrove
    ) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(isCherryGrove ? EntityType.PIG : EntityType.DONKEY, 1, 2))
            .addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 6))
            .addSpawn(MobCategory.CREATURE, 2, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addPlainGrass(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        if (isCherryGrove) {
            BiomeDefaultFeatures.addCherryGroveVegetation(builder);
        } else {
            BiomeDefaultFeatures.addMeadowVegetation(builder);
        }

        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        if (isCherryGrove) {
            BiomeSpecialEffects.Builder builder2 = new BiomeSpecialEffects.Builder()
                .waterColor(6141935)
                .grassColorOverride(11983713)
                .foliageColorOverride(11983713);
            return baseBiome(0.5F, 0.8F)
                .setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, -10635281)
                .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_CHERRY_GROVE))
                .specialEffects(builder2.build())
                .mobSpawnSettings(builder1.build())
                .generationSettings(builder.build())
                .build();
        } else {
            return baseBiome(0.5F, 0.8F)
                .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_MEADOW))
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(937679).build())
                .mobSpawnSettings(builder1.build())
                .generationSettings(builder.build())
                .build();
        }
    }

    private static Biome.BiomeBuilder basePeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 1, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        return baseBiome(-0.7F, 0.9F)
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build());
    }

    public static Biome frozenPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        return basePeaks(placedFeatures, worldCarvers)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_FROZEN_PEAKS))
            .build();
    }

    public static Biome jaggedPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        return basePeaks(placedFeatures, worldCarvers)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_JAGGED_PEAKS))
            .build();
    }

    public static Biome stonyPeaks(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        return baseBiome(1.0F, 0.3F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_STONY_PEAKS))
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome snowySlopes(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
            .addSpawn(MobCategory.CREATURE, 5, new MobSpawnSettings.SpawnerData(EntityType.GOAT, 1, 3));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, false);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        return baseBiome(-0.3F, 0.9F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SNOWY_SLOPES))
            .setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true)
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome grove(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        MobSpawnSettings.Builder builder1 = new MobSpawnSettings.Builder();
        builder1.addSpawn(MobCategory.CREATURE, 1, new MobSpawnSettings.SpawnerData(EntityType.WOLF, 1, 1))
            .addSpawn(MobCategory.CREATURE, 8, new MobSpawnSettings.SpawnerData(EntityType.RABBIT, 2, 3))
            .addSpawn(MobCategory.CREATURE, 4, new MobSpawnSettings.SpawnerData(EntityType.FOX, 2, 4));
        BiomeDefaultFeatures.commonSpawns(builder1);
        globalOverworldGeneration(builder);
        BiomeDefaultFeatures.addFrozenSprings(builder);
        BiomeDefaultFeatures.addDefaultOres(builder);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder);
        BiomeDefaultFeatures.addGroveTrees(builder);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder, false);
        BiomeDefaultFeatures.addExtraEmeralds(builder);
        BiomeDefaultFeatures.addInfestedStone(builder);
        return baseBiome(-0.2F, 0.8F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_GROVE))
            .mobSpawnSettings(builder1.build())
            .generationSettings(builder.build())
            .build();
    }

    public static Biome lushCaves(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        builder.addSpawn(MobCategory.AXOLOTLS, 10, new MobSpawnSettings.SpawnerData(EntityType.AXOLOTL, 4, 6));
        builder.addSpawn(MobCategory.WATER_AMBIENT, 25, new MobSpawnSettings.SpawnerData(EntityType.TROPICAL_FISH, 8, 8));
        BiomeDefaultFeatures.commonSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addLushCavesSpecialOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addLushCavesVegetationFeatures(builder1);
        return baseBiome(0.5F, 0.5F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_LUSH_CAVES))
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome dripstoneCaves(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.dripstoneCavesSpawns(builder);
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        globalOverworldGeneration(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1, true);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addPlainVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, false);
        BiomeDefaultFeatures.addDripstone(builder1);
        return baseBiome(0.8F, 0.4F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DRIPSTONE_CAVES))
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }

    public static Biome deepDark(HolderGetter<PlacedFeature> placedFeatures, HolderGetter<ConfiguredWorldCarver<?>> worldCarvers) {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        BiomeGenerationSettings.Builder builder1 = new BiomeGenerationSettings.Builder(placedFeatures, worldCarvers);
        builder1.addCarver(Carvers.CAVE);
        builder1.addCarver(Carvers.CAVE_EXTRA_UNDERGROUND);
        builder1.addCarver(Carvers.CANYON);
        BiomeDefaultFeatures.addDefaultCrystalFormations(builder1);
        BiomeDefaultFeatures.addDefaultMonsterRoom(builder1);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(builder1);
        BiomeDefaultFeatures.addSurfaceFreezing(builder1);
        BiomeDefaultFeatures.addPlainGrass(builder1);
        BiomeDefaultFeatures.addDefaultOres(builder1);
        BiomeDefaultFeatures.addDefaultSoftDisks(builder1);
        BiomeDefaultFeatures.addPlainVegetation(builder1);
        BiomeDefaultFeatures.addDefaultMushrooms(builder1);
        BiomeDefaultFeatures.addDefaultExtraVegetation(builder1, false);
        BiomeDefaultFeatures.addSculk(builder1);
        return baseBiome(0.8F, 0.4F)
            .setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_DEEP_DARK))
            .mobSpawnSettings(builder.build())
            .generationSettings(builder1.build())
            .build();
    }
}
