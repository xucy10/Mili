package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class LodestoneCompassComponentFix extends DataComponentRemainderFix {
    public LodestoneCompassComponentFix(Schema outputSchema) {
        super(outputSchema, "LodestoneCompassComponentFix", "minecraft:lodestone_target", "minecraft:lodestone_tracker");
    }

    @Override
    protected <T> Dynamic<T> fixComponent(Dynamic<T> component) {
        Optional<Dynamic<T>> optional = component.get("pos").result();
        Optional<Dynamic<T>> optional1 = component.get("dimension").result();
        component = component.remove("pos").remove("dimension");
        if (optional.isPresent() && optional1.isPresent()) {
            component = component.set("target", component.emptyMap().set("pos", optional.get()).set("dimension", optional1.get()));
        }

        return component;
    }
}
