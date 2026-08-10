package net.minecraft.world.entity.animal.pig;

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

public class PigVariants {
    public static final ResourceKey<PigVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    public static final ResourceKey<PigVariant> WARM = createKey(TemperatureVariants.WARM);
    public static final ResourceKey<PigVariant> COLD = createKey(TemperatureVariants.COLD);
    public static final ResourceKey<PigVariant> DEFAULT = TEMPERATE;

    private static ResourceKey<PigVariant> createKey(Identifier name) {
        return ResourceKey.create(Registries.PIG_VARIANT, name);
    }

    public static void bootstrap(BootstrapContext<PigVariant> context) {
        register(context, TEMPERATE, PigVariant.ModelType.NORMAL, "temperate_pig", SpawnPrioritySelectors.fallback(0));
        register(context, WARM, PigVariant.ModelType.NORMAL, "warm_pig", BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS);
        register(context, COLD, PigVariant.ModelType.COLD, "cold_pig", BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS);
    }

    private static void register(
        BootstrapContext<PigVariant> context, ResourceKey<PigVariant> key, PigVariant.ModelType modelType, String name, TagKey<Biome> biomes
    ) {
        HolderSet<Biome> orThrow = context.lookup(Registries.BIOME).getOrThrow(biomes);
        register(context, key, modelType, name, SpawnPrioritySelectors.single(new BiomeCheck(orThrow), 1));
    }

    private static void register(
        BootstrapContext<PigVariant> context, ResourceKey<PigVariant> key, PigVariant.ModelType modelType, String name, SpawnPrioritySelectors spawnConditions
    ) {
        Identifier identifier = Identifier.withDefaultNamespace("entity/pig/" + name);
        context.register(key, new PigVariant(new ModelAndTexture<>(modelType, identifier), spawnConditions));
    }
}
