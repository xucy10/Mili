package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class DebugProfileOverlayReferenceFix extends DataFix {
    public DebugProfileOverlayReferenceFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "DebugProfileOverlayReferenceFix",
            this.getInputSchema().getType(References.DEBUG_PROFILE),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.update(
                    "custom",
                    dynamic1 -> dynamic1.updateMapValues(
                        pair -> pair.mapSecond(dynamic2 -> dynamic2.asString("").equals("inF3") ? dynamic2.createString("inOverlay") : dynamic2)
                    )
                )
            )
        );
    }
}
