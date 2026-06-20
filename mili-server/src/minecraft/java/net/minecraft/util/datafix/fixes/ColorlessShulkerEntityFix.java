package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class ColorlessShulkerEntityFix extends NamedEntityFix {
    public ColorlessShulkerEntityFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "Colorless shulker entity fix", References.ENTITY, "minecraft:shulker");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(), dynamic -> dynamic.get("Color").asInt(0) == 10 ? dynamic.set("Color", dynamic.createByte((byte)16)) : dynamic
        );
    }
}
