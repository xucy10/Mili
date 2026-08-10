package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityPaintingMotiveFix extends NamedEntityFix {
    private static final Map<String, String> MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        map.put("donkeykong", "donkey_kong");
        map.put("burningskull", "burning_skull");
        map.put("skullandroses", "skull_and_roses");
    });

    public EntityPaintingMotiveFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityPaintingMotiveFix", References.ENTITY, "minecraft:painting");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        Optional<String> optional = tag.get("Motive").asString().result();
        if (optional.isPresent()) {
            String string = optional.get().toLowerCase(Locale.ROOT);
            return tag.set("Motive", tag.createString(NamespacedSchema.ensureNamespaced(MAP.getOrDefault(string, string))));
        } else {
            return tag;
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
