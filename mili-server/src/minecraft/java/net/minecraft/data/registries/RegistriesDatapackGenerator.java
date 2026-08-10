package net.minecraft.data.registries;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;

public class RegistriesDatapackGenerator implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.registries = registries;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return this.registries
            .thenCompose(
                provider -> {
                    DynamicOps<JsonElement> dynamicOps = provider.createSerializationContext(JsonOps.INSTANCE);
                    return CompletableFuture.allOf(
                        RegistryDataLoader.WORLDGEN_REGISTRIES
                            .stream()
                            .flatMap(
                                registryData -> this.dumpRegistryCap(output, provider, dynamicOps, (RegistryDataLoader.RegistryData<?>)registryData).stream()
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private <T> Optional<CompletableFuture<?>> dumpRegistryCap(
        CachedOutput output, HolderLookup.Provider registries, DynamicOps<JsonElement> ops, RegistryDataLoader.RegistryData<T> registryData
    ) {
        ResourceKey<? extends Registry<T>> resourceKey = registryData.key();
        return registries.lookup(resourceKey)
            .map(
                registryLookup -> {
                    PackOutput.PathProvider pathProvider = this.output.createRegistryElementsPathProvider(resourceKey);
                    return CompletableFuture.allOf(
                        registryLookup.listElements()
                            .map(
                                reference -> dumpValue(
                                    pathProvider.json(reference.key().identifier()), output, ops, registryData.elementCodec(), reference.value()
                                )
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private static <E> CompletableFuture<?> dumpValue(Path valuePath, CachedOutput output, DynamicOps<JsonElement> ops, Encoder<E> encoder, E value) {
        return encoder.encodeStart(ops, value)
            .mapOrElse(
                jsonElement -> DataProvider.saveStable(output, jsonElement, valuePath),
                error -> CompletableFuture.failedFuture(new IllegalStateException("Couldn't generate file '" + valuePath + "': " + error.message()))
            );
    }

    @Override
    public final String getName() {
        return "Registries";
    }
}
