package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class ChunkLightRemoveFix extends DataFix {
    public ChunkLightRemoveFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        OpticFinder<?> opticFinder = DSL.fieldFinder("Level", type1);
        return this.fixTypeEverywhereTyped(
            "ChunkLightRemoveFix",
            type,
            this.getOutputSchema().getType(References.CHUNK),
            typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), dynamic -> dynamic.remove("isLightOn")))
        );
    }
}
