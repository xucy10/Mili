package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityArmorStandSilentFix extends NamedEntityFix {
    public EntityArmorStandSilentFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityArmorStandSilentFix", References.ENTITY, "ArmorStand");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.get("Silent").asBoolean(false) && !tag.get("Marker").asBoolean(false) ? tag.remove("Silent") : tag;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
