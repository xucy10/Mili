package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsFancyGraphicsToGraphicsModeFix extends DataFix {
    public OptionsFancyGraphicsToGraphicsModeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "fancyGraphics to graphicsMode",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.renameAndFixField("fancyGraphics", "graphicsMode", OptionsFancyGraphicsToGraphicsModeFix::fixGraphicsMode)
            )
        );
    }

    private static <T> Dynamic<T> fixGraphicsMode(Dynamic<T> dynamic) {
        return "true".equals(dynamic.asString("true")) ? dynamic.createString("1") : dynamic.createString("0");
    }
}
