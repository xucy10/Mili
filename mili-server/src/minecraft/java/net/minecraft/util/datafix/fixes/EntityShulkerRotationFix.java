package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;

public class EntityShulkerRotationFix extends NamedEntityFix {
    public EntityShulkerRotationFix(Schema outputSchema) {
        super(outputSchema, false, "EntityShulkerRotationFix", References.ENTITY, "minecraft:shulker");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        List<Double> list = tag.get("Rotation").asList(dynamic -> dynamic.asDouble(180.0));
        if (!list.isEmpty()) {
            list.set(0, list.get(0) - 180.0);
            return tag.set("Rotation", tag.createList(list.stream().map(tag::createDouble)));
        } else {
            return tag;
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
