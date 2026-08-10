package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class AreaEffectCloudDurationScaleFix extends NamedEntityFix {
    public AreaEffectCloudDurationScaleFix(Schema outputSchema) {
        super(outputSchema, false, "AreaEffectCloudDurationScaleFix", References.ENTITY, "minecraft:area_effect_cloud");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), dynamic -> dynamic.set("potion_duration_scale", dynamic.createFloat(0.25F)));
    }
}
