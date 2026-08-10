package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResultInfo<Result>(String name, Schema<Result> schema) {
    public static <Result> Codec<ResultInfo<Result>> typedCodec() {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ResultInfo::name), Schema.<Result>typedCodec().fieldOf("schema").forGetter(ResultInfo::schema)
                )
                .apply(instance, ResultInfo::new)
        );
    }
}
