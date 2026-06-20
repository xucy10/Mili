package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V4307 extends NamespacedSchema {
    public V4307(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    public static SequencedMap<String, Supplier<TypeTemplate>> components(Schema schema) {
        SequencedMap<String, Supplier<TypeTemplate>> map = V4059.components(schema);
        map.put("minecraft:can_place_on", () -> adventureModePredicate(schema));
        map.put("minecraft:can_break", () -> adventureModePredicate(schema));
        return map;
    }

    private static TypeTemplate adventureModePredicate(Schema schema) {
        TypeTemplate typeTemplate = DSL.optionalFields("blocks", DSL.or(References.BLOCK_NAME.in(schema), DSL.list(References.BLOCK_NAME.in(schema))));
        return DSL.or(typeTemplate, DSL.list(typeTemplate));
    }

    @Override
    public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(schema, entityTypes, blockEntityTypes);
        schema.registerType(true, References.DATA_COMPONENTS, () -> DSL.optionalFieldsLazy(components(schema)));
    }
}
