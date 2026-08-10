package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class EntitySalmonSizeFix extends NamedEntityFix {
    public EntitySalmonSizeFix(Schema outputSchema) {
        super(outputSchema, false, "EntitySalmonSizeFix", References.ENTITY, "minecraft:salmon");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), data -> {
            String string = data.get("type").asString("medium");
            return string.equals("large") ? data : data.set("type", data.createString("medium"));
        });
    }
}
