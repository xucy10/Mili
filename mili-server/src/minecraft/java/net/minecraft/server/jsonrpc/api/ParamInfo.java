package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ParamInfo<Param>(String name, Schema<Param> schema, boolean required) {
    public ParamInfo(String name, Schema<Param> schema) {
        this(name, schema, true);
    }

    public static <Param> MapCodec<ParamInfo<Param>> typedCodec() {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(ParamInfo::name),
                    Schema.<Param>typedCodec().fieldOf("schema").forGetter(ParamInfo::schema),
                    Codec.BOOL.fieldOf("required").forGetter(ParamInfo::required)
                )
                .apply(instance, ParamInfo::new)
        );
    }
}
