package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class StriderGravityFix extends NamedEntityFix {
    public StriderGravityFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "StriderGravityFix", References.ENTITY, "minecraft:strider");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.get("NoGravity").asBoolean(false) ? tag.set("NoGravity", tag.createBoolean(false)) : tag;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
