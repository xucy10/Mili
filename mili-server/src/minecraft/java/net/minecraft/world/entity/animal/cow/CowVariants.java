package net.minecraft.world.entity.animal.cow;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.TemperatureVariants;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.ModelAndTexture;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public class CowVariants {
    public static final ResourceKey<CowVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<CowVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<CowVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<CowVariant> DEFAULT = TEMPERATE;

    private static ResourceKey<CowVariant> createKey(Identifier name) {
        return ResourceKey.create(Registries.COW_VARIANT, name);
    }

    public static void bootstrap(BootstrapContext<CowVariant> context) {
        register(context, TEMPERATE, CowVariant.ModelType.NORMAL, "temperate_cow", SpawnPrioritySelectors.fallback(0));
        register(context, WARM, CowVariant.ModelType.WARM, "warm_cow", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(context, COLD, CowVariant.ModelType.COLD, "cold_cow", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(
        BootstrapContext<CowVariant> context, ResourceKey<CowVariant> key, CowVariant.ModelType modelType, String assetId, TagKey<Biome> biomes
    ) {
        HolderSet<Biome> orThrow = context.lookup(Registries.BIOME).getOrThrow(biomes);
        register(context, key, modelType, assetId, SpawnPrioritySelectors.single(new BiomeCheck(orThrow), 1));
    }

    private static void register(
        BootstrapContext<CowVariant> context,
        ResourceKey<CowVariant> key,
        CowVariant.ModelType modelType,
        String assetId,
        SpawnPrioritySelectors spawnConditions
    ) {
        Identifier identifier = Identifier.withDefaultNamespace("entity/cow/" + assetId);
        context.register(key, new CowVariant(new ModelAndTexture<>(modelType, identifier), spawnConditions));
    }
}
