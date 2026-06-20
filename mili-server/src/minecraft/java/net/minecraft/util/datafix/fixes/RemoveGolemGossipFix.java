package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class RemoveGolemGossipFix extends NamedEntityFix {
    public RemoveGolemGossipFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "Remove Golem Gossip Fix", References.ENTITY, "minecraft:villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), RemoveGolemGossipFix::fixValue);
    }

    private static Dynamic<?> fixValue(Dynamic<?> dynamic) {
        return dynamic.update(
            "Gossips", dynamic1 -> dynamic.createList(dynamic1.asStream().filter(dynamic2 -> !dynamic2.get("Type").asString("").equals("golem")))
        );
    }
}
