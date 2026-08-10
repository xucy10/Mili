package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class VillagerDataFix extends NamedEntityFix {
    public VillagerDataFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Villager profession data fix (" + entityName + ")", References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
        return typed.set(
            DSL.remainderFinder(),
            dynamic.remove("Profession")
                .remove("Career")
                .remove("CareerLevel")
                .set(
                    "VillagerData",
                    dynamic.createMap(
                        ImmutableMap.of(
                            dynamic.createString("type"),
                            dynamic.createString("minecraft:plains"),
                            dynamic.createString("profession"),
                            dynamic.createString(upgradeData(dynamic.get("Profession").asInt(0), dynamic.get("Career").asInt(0))),
                            dynamic.createString("level"),
                            DataFixUtils.orElse(dynamic.get("CareerLevel").result(), dynamic.createInt(1))
                        )
                    )
                )
        );
    }

    private static String upgradeData(int profession, int career) {
        if (profession == 0) {
            if (career == 2) {
                return "minecraft:fisherman";
            } else if (career == 3) {
                return "minecraft:shepherd";
            } else {
                return career == 4 ? "minecraft:fletcher" : "minecraft:farmer";
            }
        } else if (profession == 1) {
            return career == 2 ? "minecraft:cartographer" : "minecraft:librarian";
        } else if (profession == 2) {
            return "minecraft:cleric";
        } else if (profession == 3) {
            if (career == 2) {
                return "minecraft:weaponsmith";
            } else {
                return career == 3 ? "minecraft:toolsmith" : "minecraft:armorer";
            }
        } else if (profession == 4) {
            return career == 2 ? "minecraft:leatherworker" : "minecraft:butcher";
        } else {
            return profession == 5 ? "minecraft:nitwit" : "minecraft:none";
        }
    }
}
