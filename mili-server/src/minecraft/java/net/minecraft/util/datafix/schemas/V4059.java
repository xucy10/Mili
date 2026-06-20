package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V4059 extends NamespacedSchema {
    public V4059(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    public static SequencedMap<String, Supplier<TypeTemplate>> components(Schema schema) {
        SequencedMap<String, Supplier<TypeTemplate>> map = V3818_3.components(schema);
        map.remove("minecraft:food");
        map.put("minecraft:use_remainder", () -> References.ITEM_STACK.in(schema));
        map.put(
            "minecraft:equippable",
            () -> DSL.optionalFields("allowed_entities", DSL.or(References.ENTITY_NAME.in(schema), DSL.list(References.ENTITY_NAME.in(schema))))
        );
        return map;
    }

    @Override
    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        schema.registerType(true, References.DATA_COMPONENTS, () -> DSL.optionalFieldsLazy(components(schema)));
    }
}
