package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;

public class ItemListReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ItemListReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("items.json");
        return this.registries
            .thenCompose(
                provider -> {
                    JsonObject jsonObject = new JsonObject();
                    RegistryOps<JsonElement> registryOps = provider.createSerializationContext(JsonOps.INSTANCE);
                    provider.lookupOrThrow(Registries.ITEM)
                        .listElements()
                        .forEach(
                            reference -> {
                                JsonObject jsonObject1 = new JsonObject();
                                jsonObject1.add(
                                    "components",
                                    DataComponentMap.CODEC
                                        .encodeStart(registryOps, reference.value().components())
                                        .getOrThrow(string -> new IllegalStateException("Failed to encode components: " + string))
                                );
                                jsonObject.add(reference.getRegisteredName(), jsonObject1);
                            }
                        );
                    return DataProvider.saveStable(output, jsonObject, path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Item List";
    }
}
