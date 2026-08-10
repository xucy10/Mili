package net.minecraft.data.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockListReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public BlockListReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("blocks.json");
        return this.registries
            .thenCompose(
                provider -> {
                    JsonObject jsonObject = new JsonObject();
                    RegistryOps<JsonElement> registryOps = provider.createSerializationContext(JsonOps.INSTANCE);
                    provider.lookupOrThrow(Registries.BLOCK)
                        .listElements()
                        .forEach(
                            reference -> {
                                JsonObject jsonObject1 = new JsonObject();
                                StateDefinition<Block, BlockState> stateDefinition = reference.value().getStateDefinition();
                                if (!stateDefinition.getProperties().isEmpty()) {
                                    JsonObject jsonObject2 = new JsonObject();

                                    for (Property<?> property : stateDefinition.getProperties()) {
                                        JsonArray jsonArray = new JsonArray();

                                        for (Comparable<?> comparable : property.getPossibleValues()) {
                                            jsonArray.add(Util.getPropertyName(property, comparable));
                                        }

                                        jsonObject2.add(property.getName(), jsonArray);
                                    }

                                    jsonObject1.add("properties", jsonObject2);
                                }

                                JsonArray jsonArray1 = new JsonArray();

                                for (BlockState blockState : stateDefinition.getPossibleStates()) {
                                    JsonObject jsonObject3 = new JsonObject();
                                    JsonObject jsonObject4 = new JsonObject();

                                    for (Property<?> property1 : stateDefinition.getProperties()) {
                                        jsonObject4.addProperty(property1.getName(), Util.getPropertyName(property1, blockState.getValue(property1)));
                                    }

                                    if (!jsonObject4.isEmpty()) {
                                        jsonObject3.add("properties", jsonObject4);
                                    }

                                    jsonObject3.addProperty("id", Block.getId(blockState));
                                    if (blockState == reference.value().defaultBlockState()) {
                                        jsonObject3.addProperty("default", true);
                                    }

                                    jsonArray1.add(jsonObject3);
                                }

                                jsonObject1.add("states", jsonArray1);
                                String registeredName = reference.getRegisteredName();
                                JsonElement jsonElement = BlockTypes.CODEC
                                    .codec()
                                    .encodeStart(registryOps, reference.value())
                                    .getOrThrow(
                                        string -> new AssertionError(
                                            "Failed to serialize block " + registeredName + " (is type registered in BlockTypes?): " + string
                                        )
                                    );
                                jsonObject1.add("definition", jsonElement);
                                jsonObject.add(registeredName, jsonObject1);
                            }
                        );
                    return DataProvider.saveStable(output, jsonObject, path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Block List";
    }
}
