package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;

public class EntityFallDistanceFloatToDoubleFix extends DataFix {
    private final TypeReference type;

    public EntityFallDistanceFloatToDoubleFix(Schema outputSchema, TypeReference type) {
        super(outputSchema, false);
        this.type = type;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityFallDistanceFloatToDoubleFixFor" + this.type.typeName(),
            this.getOutputSchema().getType(this.type),
            EntityFallDistanceFloatToDoubleFix::fixEntity
        );
    }

    private static Typed<?> fixEntity(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.renameAndFixField("FallDistance", "fall_distance", dynamic1 -> dynamic1.createDouble(dynamic1.asFloat(0.0F)))
        );
    }
}
