package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import java.util.Optional;

public class ZombieVillagerRebuildXpFix extends NamedEntityFix {
    public ZombieVillagerRebuildXpFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "Zombie Villager XP rebuild", References.ENTITY, "minecraft:zombie_villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), dynamic -> {
            Optional<Number> optional = dynamic.get("Xp").asNumber().result();
            if (optional.isEmpty()) {
                int _int = dynamic.get("VillagerData").get("level").asInt(1);
                return dynamic.set("Xp", dynamic.createInt(VillagerRebuildLevelAndXpFix.getMinXpPerLevel(_int)));
            } else {
                return dynamic;
            }
        });
    }
}
