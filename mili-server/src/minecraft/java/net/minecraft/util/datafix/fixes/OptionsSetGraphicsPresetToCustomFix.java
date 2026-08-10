package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsSetGraphicsPresetToCustomFix extends DataFix {
    public OptionsSetGraphicsPresetToCustomFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "graphicsPreset set to \"custom\"",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.set("graphicsPreset", dynamic.createString("custom")))
        );
    }
}
