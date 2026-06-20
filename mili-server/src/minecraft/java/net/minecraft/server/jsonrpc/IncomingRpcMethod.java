package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import org.jspecify.annotations.Nullable;

public interface IncomingRpcMethod<Params, Result> {
    MethodInfo<Params, Result> info();

    IncomingRpcMethod.Attributes attributes();

    JsonElement apply(MinecraftApi api, @Nullable JsonElement params, ClientInfo client);

    static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(
        IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> parameterlessFunction
    ) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(parameterlessFunction);
    }

    static <Params, Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> method(
        IncomingRpcMethod.RpcMethodFunction<Params, Result> parameterFunction
    ) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(parameterFunction);
    }

    static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(Function<MinecraftApi, Result> parameterlessFunction) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(parameterlessFunction);
    }

    public record Attributes(boolean runOnMainThread, boolean discoverable) {
    }

    public static class IncomingRpcMethodBuilder<Params, Result> {
        private String description = "";
        private @Nullable ParamInfo<Params> paramInfo;
        private @Nullable ResultInfo<Result> resultInfo;
        private boolean discoverable = true;
        private boolean runOnMainThread = true;
        private IncomingRpcMethod.@Nullable ParameterlessRpcMethodFunction<Result> parameterlessFunction;
        private IncomingRpcMethod.@Nullable RpcMethodFunction<Params, Result> parameterFunction;

        public IncomingRpcMethodBuilder(IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> parameterlessFunction) {
            this.parameterlessFunction = parameterlessFunction;
        }

        public IncomingRpcMethodBuilder(IncomingRpcMethod.RpcMethodFunction<Params, Result> parameterFunction) {
            this.parameterFunction = parameterFunction;
        }

        public IncomingRpcMethodBuilder(Function<MinecraftApi, Result> parameterlessFunction) {
            this.parameterlessFunction = (api, client) -> parameterlessFunction.apply(api);
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> description(String description) {
            this.description = description;
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> response(String name, Schema<Result> schema) {
            this.resultInfo = new ResultInfo<>(name, schema.info());
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> param(String name, Schema<Params> schema) {
            this.paramInfo = new ParamInfo<>(name, schema.info());
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> undiscoverable() {
            this.discoverable = false;
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> notOnMainThread() {
            this.runOnMainThread = false;
            return this;
        }

        public IncomingRpcMethod<Params, Result> build() {
            if (this.resultInfo == null) {
                throw new IllegalStateException("No response defined");
            } else {
                IncomingRpcMethod.Attributes attributes = new IncomingRpcMethod.Attributes(this.runOnMainThread, this.discoverable);
                MethodInfo<Params, Result> methodInfo = new MethodInfo<>(this.description, this.paramInfo, this.resultInfo);
                if (this.parameterlessFunction != null) {
                    return new IncomingRpcMethod.ParameterlessMethod<>(methodInfo, attributes, this.parameterlessFunction);
                } else if (this.parameterFunction != null) {
                    if (this.paramInfo == null) {
                        throw new IllegalStateException("No param schema defined");
                    } else {
                        return new IncomingRpcMethod.Method<>(methodInfo, attributes, this.parameterFunction);
                    }
                } else {
                    throw new IllegalStateException("No method defined");
                }
            }
        }

        public IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> registry, String namespace) {
            return this.register(registry, Identifier.withDefaultNamespace(namespace));
        }

        private IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> registry, Identifier id) {
            return Registry.register(registry, id, this.build());
        }
    }

    public record Method<Params, Result>(
        @Override MethodInfo<Params, Result> info,
        @Override IncomingRpcMethod.Attributes attributes,
        IncomingRpcMethod.RpcMethodFunction<Params, Result> function
    ) implements IncomingRpcMethod<Params, Result> {
        @Override
        public JsonElement apply(MinecraftApi api, @Nullable JsonElement params, ClientInfo client) {
            if (params != null && (params.isJsonArray() || params.isJsonObject())) {
                if (this.info.params().isEmpty()) {
                    throw new IllegalArgumentException("Method defined as having parameters without describing them");
                } else {
                    JsonElement jsonElement1;
                    if (params.isJsonObject()) {
                        String string = this.info.params().get().name();
                        JsonElement jsonElement = params.getAsJsonObject().get(string);
                        if (jsonElement == null) {
                            throw new InvalidParameterJsonRpcException(
                                String.format(Locale.ROOT, "Params passed by-name, but expected param [%s] does not exist", string)
                            );
                        }

                        jsonElement1 = jsonElement;
                    } else {
                        JsonArray asJsonArray = params.getAsJsonArray();
                        if (asJsonArray.isEmpty() || asJsonArray.size() > 1) {
                            throw new InvalidParameterJsonRpcException("Expected exactly one element in the params array");
                        }

                        jsonElement1 = asJsonArray.get(0);
                    }

                    Params orThrow = this.info
                        .params()
                        .get()
                        .schema()
                        .codec()
                        .parse(JsonOps.INSTANCE, jsonElement1)
                        .getOrThrow(InvalidParameterJsonRpcException::new);
                    Result object = this.function.apply(api, orThrow, client);
                    if (this.info.result().isEmpty()) {
                        throw new IllegalStateException("No result codec defined");
                    } else {
                        return this.info.result().get().schema().codec().encodeStart(JsonOps.INSTANCE, object).getOrThrow(EncodeJsonRpcException::new);
                    }
                }
            } else {
                throw new InvalidParameterJsonRpcException("Expected params as array or named");
            }
        }
    }

    public record ParameterlessMethod<Params, Result>(
        @Override MethodInfo<Params, Result> info,
        @Override IncomingRpcMethod.Attributes attributes,
        IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> supplier
    ) implements IncomingRpcMethod<Params, Result> {
        @Override
        public JsonElement apply(MinecraftApi api, @Nullable JsonElement params, ClientInfo client) {
            if (params == null || params.isJsonArray() && params.getAsJsonArray().isEmpty()) {
                if (this.info.params().isPresent()) {
                    throw new IllegalArgumentException("Parameterless method unexpectedly has parameter description");
                } else {
                    Result object = this.supplier.apply(api, client);
                    if (this.info.result().isEmpty()) {
                        throw new IllegalStateException("No result codec defined");
                    } else {
                        return this.info
                            .result()
                            .get()
                            .schema()
                            .codec()
                            .encodeStart(JsonOps.INSTANCE, object)
                            .getOrThrow(InvalidParameterJsonRpcException::new);
                    }
                }
            } else {
                throw new InvalidParameterJsonRpcException("Expected no params, or an empty array");
            }
        }
    }

    @FunctionalInterface
    public interface ParameterlessRpcMethodFunction<Result> {
        Result apply(MinecraftApi api, ClientInfo client);
    }

    @FunctionalInterface
    public interface RpcMethodFunction<Params, Result> {
        Result apply(MinecraftApi api, Params params, ClientInfo client);
    }
}
