package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;

public class MemoryExpiryDataFix extends NamedEntityFix {
    public MemoryExpiryDataFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Memory expiry data fix (" + entityName + ")", References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.update("Brain", this::updateBrain);
    }

    private Dynamic<?> updateBrain(Dynamic<?> brainTag) {
        return brainTag.update("memories", this::updateMemories);
    }

    private Dynamic<?> updateMemories(Dynamic<?> memoriesTag) {
        return memoriesTag.updateMapValues(this::updateMemoryEntry);
    }

    private Pair<Dynamic<?>, Dynamic<?>> updateMemoryEntry(Pair<Dynamic<?>, Dynamic<?>> memory) {
        return memory.mapSecond(this::wrapMemoryValue);
    }

    private Dynamic<?> wrapMemoryValue(Dynamic<?> memory) {
        return memory.createMap(ImmutableMap.of(memory.createString("value"), memory));
    }
}
