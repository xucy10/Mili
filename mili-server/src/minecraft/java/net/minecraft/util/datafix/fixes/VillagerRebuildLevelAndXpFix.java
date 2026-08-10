package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.Mth;

public class VillagerRebuildLevelAndXpFix extends DataFix {
    private static final int TRADES_PER_LEVEL = 2;
    private static final int[] LEVEL_XP_THRESHOLDS = new int[]{0, 10, 50, 100, 150};

    public static int getMinXpPerLevel(int level) {
        return LEVEL_XP_THRESHOLDS[Mth.clamp(level - 1, 0, LEVEL_XP_THRESHOLDS.length - 1)];
    }

    public VillagerRebuildLevelAndXpFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.ENTITY, "minecraft:villager");
        OpticFinder<?> opticFinder = DSL.namedChoice("minecraft:villager", choiceType);
        OpticFinder<?> opticFinder1 = choiceType.findField("Offers");
        Type<?> type = opticFinder1.type();
        OpticFinder<?> opticFinder2 = type.findField("Recipes");
        ListType<?> listType = (ListType<?>)opticFinder2.type();
        OpticFinder<?> opticFinder3 = listType.getElement().finder();
        return this.fixTypeEverywhereTyped(
            "Villager level and xp rebuild",
            this.getInputSchema().getType(References.ENTITY),
            typed -> typed.updateTyped(
                opticFinder,
                choiceType,
                typed1 -> {
                    Dynamic<?> dynamic = typed1.get(DSL.remainderFinder());
                    int _int = dynamic.get("VillagerData").get("level").asInt(0);
                    Typed<?> typed2 = typed1;
                    if (_int == 0 || _int == 1) {
                        int i = typed1.getOptionalTyped(opticFinder1)
                            .flatMap(typed3 -> typed3.getOptionalTyped(opticFinder2))
                            .map(typed3 -> typed3.getAllTyped(opticFinder3).size())
                            .orElse(0);
                        _int = Mth.clamp(i / 2, 1, 5);
                        if (_int > 1) {
                            typed2 = addLevel(typed1, _int);
                        }
                    }

                    Optional<Number> optional = dynamic.get("Xp").asNumber().result();
                    if (optional.isEmpty()) {
                        typed2 = addXpFromLevel(typed2, _int);
                    }

                    return typed2;
                }
            )
        );
    }

    private static Typed<?> addLevel(Typed<?> typed, int level) {
        return typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("VillagerData", dynamic1 -> dynamic1.set("level", dynamic1.createInt(level))));
    }

    private static Typed<?> addXpFromLevel(Typed<?> typed, int xp) {
        int minXpPerLevel = getMinXpPerLevel(xp);
        return typed.update(DSL.remainderFinder(), dynamic -> dynamic.set("Xp", dynamic.createInt(minXpPerLevel)));
    }
}
