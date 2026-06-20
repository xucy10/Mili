package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class FireResistantToDamageResistantComponentFix extends DataComponentRemainderFix {
    public FireResistantToDamageResistantComponentFix(Schema outputSchema) {
        super(outputSchema, "FireResistantToDamageResistantComponentFix", "minecraft:fire_resistant", "minecraft:damage_resistant");
    }

    @Override
    protected <T> Dynamic<T> fixComponent(Dynamic<T> component) {
        return component.emptyMap().set("types", component.createString("#minecraft:is_fire"));
    }
}
