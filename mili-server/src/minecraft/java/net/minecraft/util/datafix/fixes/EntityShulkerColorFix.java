package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityShulkerColorFix extends NamedEntityFix {
    public EntityShulkerColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityShulkerColorFix", References.ENTITY, "minecraft:shulker");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.get("Color").map(Dynamic::asNumber).result().isEmpty() ? tag.set("Color", tag.createByte((byte)10)) : tag;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
