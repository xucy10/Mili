package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.net.URI;
import java.net.URISyntaxException;

public class ReferenceUtil {
    public static final Codec<URI> REFERENCE_CODEC = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(new URI(string));
        } catch (URISyntaxException var2) {
            return DataResult.error(var2::getMessage);
        }
    }, URI::toString);

    public static URI createLocalReference(String reference) {
        return URI.create("#/components/schemas/" + reference);
    }
}
