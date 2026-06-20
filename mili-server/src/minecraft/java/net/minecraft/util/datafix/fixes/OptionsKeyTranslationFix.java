package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.stream.Collectors;

public class OptionsKeyTranslationFix extends DataFix {
    public OptionsKeyTranslationFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsKeyTranslationFix",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.getMapValues().map(map -> dynamic.createMap(map.entrySet().stream().map(entry -> {
                if (entry.getKey().asString("").startsWith("key_")) {
                    String string = entry.getValue().asString("");
                    if (!string.startsWith("key.mouse") && !string.startsWith("scancode.")) {
                        return Pair.of(entry.getKey(), dynamic.createString("key.keyboard." + string.substring("key.".length())));
                    }
                }

                return Pair.of(entry.getKey(), entry.getValue());
            }).collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)))).result().orElse((Dynamic) dynamic))
        );
    }
}
