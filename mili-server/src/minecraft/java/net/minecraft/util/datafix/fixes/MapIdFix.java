package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.Map;

public class MapIdFix extends DataFix {
    public MapIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "Map id fix",
            this.getInputSchema().getType(References.SAVED_DATA_MAP_INDEX),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.createMap(Map.of(dynamic.createString("data"), dynamic)))
        );
    }
}
