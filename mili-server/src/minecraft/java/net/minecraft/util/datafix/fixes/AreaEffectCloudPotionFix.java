package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class AreaEffectCloudPotionFix extends NamedEntityFix {
    public AreaEffectCloudPotionFix(Schema outputSchema) {
        super(outputSchema, false, "AreaEffectCloudPotionFix", References.ENTITY, "minecraft:area_effect_cloud");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("Color").result();
        Optional<Dynamic<T>> optional1 = tag.get("effects").result();
        Optional<Dynamic<T>> optional2 = tag.get("Potion").result();
        tag = tag.remove("Color").remove("effects").remove("Potion");
        if (optional.isEmpty() && optional1.isEmpty() && optional2.isEmpty()) {
            return tag;
        } else {
            Dynamic<T> dynamic = tag.emptyMap();
            if (optional.isPresent()) {
                dynamic = dynamic.set("custom_color", optional.get());
            }

            if (optional1.isPresent()) {
                dynamic = dynamic.set("custom_effects", optional1.get());
            }

            if (optional2.isPresent()) {
                dynamic = dynamic.set("potion", optional2.get());
            }

            return tag.set("potion_contents", dynamic);
        }
    }
}
