package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.Map;
import java.util.stream.Stream;

public class CustomModelDataExpandFix extends DataFix {
    public CustomModelDataExpandFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.DATA_COMPONENTS);
        return this.fixTypeEverywhereTyped(
            "Custom Model Data expansion",
            type,
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("minecraft:custom_model_data", dynamic1 -> {
                float f = dynamic1.asNumber(0.0F).floatValue();
                return dynamic1.createMap(Map.of(dynamic1.createString("floats"), dynamic1.createList(Stream.of(dynamic1.createFloat(f)))));
            }))
        );
    }
}
