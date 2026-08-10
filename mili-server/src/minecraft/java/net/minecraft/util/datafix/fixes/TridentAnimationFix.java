package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import org.jspecify.annotations.Nullable;

public class TridentAnimationFix extends DataComponentRemainderFix {
    public TridentAnimationFix(Schema outputSchema) {
        super(outputSchema, "TridentAnimationFix", "minecraft:consumable");
    }

    @Override
    protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> component) {
        return component.update("animation", dynamic -> {
            String string = dynamic.asString().result().orElse("");
            return "spear".equals(string) ? dynamic.createString("trident") : dynamic;
        });
    }
}
