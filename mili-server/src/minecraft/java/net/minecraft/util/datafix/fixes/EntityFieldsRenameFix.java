package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Map.Entry;

public class EntityFieldsRenameFix extends NamedEntityFix {
    private final Map<String, String> renames;

    public EntityFieldsRenameFix(Schema outputSchema, String name, String entityName, Map<String, String> renames) {
        super(outputSchema, false, name, References.ENTITY, entityName);
        this.renames = renames;
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        for (Entry<String, String> entry : this.renames.entrySet()) {
            tag = tag.renameField(entry.getKey(), entry.getValue());
        }

        return tag;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
