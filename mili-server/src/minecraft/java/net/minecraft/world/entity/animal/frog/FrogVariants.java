package net.minecraft.world.entity.animal.frog;

import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.TemperatureVariants;
import net.minecraft.world.entity.variant.BiomeCheck;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.level.biome.Biome;

public interface FrogVariants {
    ResourceKey<FrogVariant> TEMPERATE = createKey(TemperatureVariants.TEMPERATE);
    ResourceKey<FrogVariant> WARM = createKey(TemperatureVariants.WARM);
    ResourceKey<FrogVariant> COLD = createKey(TemperatureVariants.COLD);

    private static ResourceKey<FrogVariant> createKey(Identifier name) {
        return ResourceKey.create(Registries.FROG_VARIANT, name);
    }

    static void bootstrap(BootstrapContext<FrogVariant> context) {
        register(context, TEMPERATE, "entity/frog/temperate_frog", SpawnPrioritySelectors.fallback(0));
        register(context, WARM, "entity/frog/warm_frog", BiomeTags.SPAWNS_WARM_VARIANT_FROGS);
        register(context, COLD, "entity/frog/cold_frog", BiomeTags.SPAWNS_COLD_VARIANT_FROGS);
    }

    private static void register(BootstrapContext<FrogVariant> context, ResourceKey<FrogVariant> key, String name, TagKey<Biome> biome) {
        HolderSet<Biome> orThrow = context.lookup(Registries.BIOME).getOrThrow(biome);
        register(context, key, name, SpawnPrioritySelectors.single(new BiomeCheck(orThrow), 1));
    }

    private static void register(BootstrapContext<FrogVariant> context, ResourceKey<FrogVariant> key, String name, SpawnPrioritySelectors spawnConditions) {
        context.register(key, new FrogVariant(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(name)), spawnConditions));
    }
}
