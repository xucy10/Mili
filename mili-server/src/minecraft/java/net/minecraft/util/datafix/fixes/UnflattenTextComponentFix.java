package net.minecraft.util.datafix.fixes;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public class UnflattenTextComponentFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public UnflattenTextComponentFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, String>> type = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
        Type<?> type1 = this.getOutputSchema().getType(References.TEXT_COMPONENT);
        return this.createFixer(type, type1);
    }

    private <T> TypeRewriteRule createFixer(Type<Pair<String, String>> inputType, Type<T> outputType) {
        return this.fixTypeEverywhere(
            "UnflattenTextComponentFix",
            inputType,
            outputType,
            dynamicOps -> pair -> Util.readTypedOrThrow(outputType, unflattenJson(dynamicOps, pair.getSecond()), true).getValue()
        );
    }

    private static <T> Dynamic<T> unflattenJson(DynamicOps<T> ops, String json) {
        try {
            JsonElement jsonElement = LenientJsonParser.parse(json);
            if (!jsonElement.isJsonNull()) {
                return new Dynamic<>(ops, JsonOps.INSTANCE.convertTo(ops, jsonElement));
            }
        } catch (Exception var3) {
            LOGGER.error("Failed to unflatten text component json: {}", json, var3);
        }

        return new Dynamic<>(ops, ops.createString(json));
    }
}
