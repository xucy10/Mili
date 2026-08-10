package net.minecraft.data.registries;

import com.mojang.datafixers.DataFixUtils;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Cloner;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RegistryPatchGenerator {
    public static CompletableFuture<RegistrySetBuilder.PatchedRegistries> createLookup(
        CompletableFuture<HolderLookup.Provider> lookup, RegistrySetBuilder registrySetBuilder
    ) {
        return lookup.thenApply(
            provider -> {
                RegistryAccess.Frozen frozen = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                Cloner.Factory factory = new Cloner.Factory();
                RegistryDataLoader.WORLDGEN_REGISTRIES.forEach(registryData -> registryData.runWithArguments(factory::addCodec));
                RegistrySetBuilder.PatchedRegistries patchedRegistries = registrySetBuilder.buildPatch(frozen, provider, factory);
                HolderLookup.Provider provider1 = patchedRegistries.full();
                Optional<? extends HolderLookup.RegistryLookup<Biome>> optional = provider1.lookup(Registries.BIOME);
                Optional<? extends HolderLookup.RegistryLookup<PlacedFeature>> optional1 = provider1.lookup(Registries.PLACED_FEATURE);
                if (optional.isPresent() || optional1.isPresent()) {
                    VanillaRegistries.validateThatAllBiomeFeaturesHaveBiomeFilter(
                        DataFixUtils.orElseGet(optional1, () -> provider.lookupOrThrow(Registries.PLACED_FEATURE)),
                        DataFixUtils.orElseGet(optional, () -> provider.lookupOrThrow(Registries.BIOME))
                    );
                }

                return patchedRegistries;
            }
        );
    }
}
