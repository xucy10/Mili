package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;

public abstract class SimpleEntityRenameFix extends EntityRenameFix {
    public SimpleEntityRenameFix(String name, Schema outputSchema, boolean changesType) {
        super(name, outputSchema, changesType);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String entityName, Typed<?> typed) {
        Pair<String, Dynamic<?>> newNameAndTag = this.getNewNameAndTag(entityName, typed.getOrCreate(DSL.remainderFinder()));
        return Pair.of(newNameAndTag.getFirst(), typed.set(DSL.remainderFinder(), newNameAndTag.getSecond()));
    }

    protected abstract Pair<String, Dynamic<?>> getNewNameAndTag(String name, Dynamic<?> tag);
}
