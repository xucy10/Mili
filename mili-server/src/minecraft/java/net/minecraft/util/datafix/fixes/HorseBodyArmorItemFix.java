package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class HorseBodyArmorItemFix extends NamedEntityWriteReadFix {
    private final String previousBodyArmorTag;
    private final boolean clearArmorItems;

    public HorseBodyArmorItemFix(Schema outputSchema, String entityName, String previousBodyArmorTag, boolean clearArmorItems) {
        super(outputSchema, true, "Horse armor fix for " + entityName, References.ENTITY, entityName);
        this.previousBodyArmorTag = previousBodyArmorTag;
        this.clearArmorItems = clearArmorItems;
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<? extends Dynamic<?>> optional = tag.get(this.previousBodyArmorTag).result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Dynamic<T> dynamic1 = tag.remove(this.previousBodyArmorTag);
            if (this.clearArmorItems) {
                dynamic1 = dynamic1.update(
                    "ArmorItems",
                    dynamic2 -> dynamic2.createList(Streams.mapWithIndex(dynamic2.asStream(), (dynamic3, l) -> l == 2L ? dynamic3.emptyMap() : dynamic3))
                );
                dynamic1 = dynamic1.update(
                    "ArmorDropChances",
                    dynamic2 -> dynamic2.createList(
                        Streams.mapWithIndex(dynamic2.asStream(), (dynamic3, l) -> l == 2L ? dynamic3.createFloat(0.085F) : dynamic3)
                    )
                );
            }

            dynamic1 = dynamic1.set("body_armor_item", dynamic);
            return dynamic1.set("body_armor_drop_chance", tag.createFloat(2.0F));
        } else {
            return tag;
        }
    }
}
