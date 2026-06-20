package net.minecraft.server.jsonrpc.dataprovider;

import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.methods.DiscoveryService;

public class JsonRpcApiSchema implements DataProvider {
    private final Path path;

    public JsonRpcApiSchema(PackOutput output) {
        this.path = output.getOutputFolder(PackOutput.Target.REPORTS).resolve("json-rpc-api-schema.json");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        DiscoveryService.DiscoverResponse discoverResponse = DiscoveryService.discover(Schema.getSchemaRegistry());
        return DataProvider.saveStable(
            output, DiscoveryService.DiscoverResponse.CODEC.codec().encodeStart(JsonOps.INSTANCE, discoverResponse).getOrThrow(), this.path
        );
    }

    @Override
    public String getName() {
        return "Json RPC API schema";
    }
}
