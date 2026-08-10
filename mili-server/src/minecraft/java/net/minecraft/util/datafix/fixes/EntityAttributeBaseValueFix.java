package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityAttributeBaseValueFix extends NamedEntityFix {
    private final String attributeId;
    private final DoubleUnaryOperator valueFixer;

    public EntityAttributeBaseValueFix(Schema outputSchema, String name, String entityName, String attributeId, DoubleUnaryOperator valueFixer) {
        super(outputSchema, false, name, References.ENTITY, entityName);
        this.attributeId = attributeId;
        this.valueFixer = valueFixer;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixValue);
    }

    private Dynamic<?> fixValue(Dynamic<?> tag) {
        return tag.update("attributes", dynamic -> tag.createList(dynamic.asStream().map(dynamic1 -> {
            String string = NamespacedSchema.ensureNamespaced(dynamic1.get("id").asString(""));
            if (!string.equals(this.attributeId)) {
                return dynamic1;
            } else {
                double _double = dynamic1.get("base").asDouble(0.0);
                return dynamic1.set("base", dynamic1.createDouble(this.valueFixer.applyAsDouble(_double)));
            }
        })));
    }
}
