package net.minecraft.world.entity.animal.chicken;

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

public class ChickenVariants {
    public static final ResourceKey<ChickenVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<ChickenVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<ChickenVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<ChickenVariant> DEFAULT = TEMPERATE;

    private static ResourceKey<ChickenVariant> createKey(Identifier name) {
        return ResourceKey.create(Registries.CHICKEN_VARIANT, name);
    }

    public static void bootstrap(BootstrapContext<ChickenVariant> context) {
        register(context, TEMPERATE, ChickenVariant.ModelType.NORMAL, "temperate_chicken", SpawnPrioritySelectors.fallback(0));
        register(context, WARM, ChickenVariant.ModelType.NORMAL, "warm_chicken", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(context, COLD, ChickenVariant.ModelType.COLD, "cold_chicken", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(
        BootstrapContext<ChickenVariant> context, ResourceKey<ChickenVariant> key, ChickenVariant.ModelType modelType, String name, TagKey<Biome> biomes
    ) {
        HolderSet<Biome> orThrow = context.lookup(Registries.BIOME).getOrThrow(biomes);
        register(context, key, modelType, name, SpawnPrioritySelectors.single(new BiomeCheck(orThrow), 1));
    }

    private static void register(
        BootstrapContext<ChickenVariant> context,
        ResourceKey<ChickenVariant> key,
        ChickenVariant.ModelType modelType,
        String name,
        SpawnPrioritySelectors spawnConditions
    ) {
        Identifier identifier = Identifier.withDefaultNamespace("entity/chicken/" + name);
        context.register(key, new ChickenVariant(new ModelAndTexture<>(modelType, identifier), spawnConditions));
    }
}
