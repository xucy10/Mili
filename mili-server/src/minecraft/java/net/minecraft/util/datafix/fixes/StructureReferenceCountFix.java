package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class StructureReferenceCountFix extends DataFix {
    public StructureReferenceCountFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.STRUCTURE_FEATURE);
        return this.fixTypeEverywhereTyped(
            "Structure Reference Fix", type, typed -> typed.update(DSL.remainderFinder(), StructureReferenceCountFix::setCountToAtLeastOne)
        );
    }

    private static <T> Dynamic<T> setCountToAtLeastOne(Dynamic<T> dynamic) {
        return dynamic.update(
            "references", dynamic1 -> dynamic1.createInt(dynamic1.asNumber().map(Number::intValue).result().filter(integer -> integer > 0).orElse(1))
        );
    }
}
