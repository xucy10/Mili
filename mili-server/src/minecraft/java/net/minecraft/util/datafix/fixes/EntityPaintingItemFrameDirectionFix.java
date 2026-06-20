package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class EntityPaintingItemFrameDirectionFix extends DataFix {
    private static final int[][] DIRECTIONS = new int[][]{{0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};

    public EntityPaintingItemFrameDirectionFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private Dynamic<?> doFix(Dynamic<?> dynamic, boolean fixDirection, boolean fixItemRotation) {
        if ((fixDirection || fixItemRotation) && dynamic.get("Facing").asNumber().result().isEmpty()) {
            int i;
            if (dynamic.get("Direction").asNumber().result().isPresent()) {
                i = dynamic.get("Direction").asByte((byte)0) % DIRECTIONS.length;
                int[] ints = DIRECTIONS[i];
                dynamic = dynamic.set("TileX", dynamic.createInt(dynamic.get("TileX").asInt(0) + ints[0]));
                dynamic = dynamic.set("TileY", dynamic.createInt(dynamic.get("TileY").asInt(0) + ints[1]));
                dynamic = dynamic.set("TileZ", dynamic.createInt(dynamic.get("TileZ").asInt(0) + ints[2]));
                dynamic = dynamic.remove("Direction");
                if (fixItemRotation && dynamic.get("ItemRotation").asNumber().result().isPresent()) {
                    dynamic = dynamic.set("ItemRotation", dynamic.createByte((byte)(dynamic.get("ItemRotation").asByte((byte)0) * 2)));
                }
            } else {
                i = dynamic.get("Dir").asByte((byte)0) % DIRECTIONS.length;
                dynamic = dynamic.remove("Dir");
            }

            dynamic = dynamic.set("Facing", dynamic.createByte((byte)i));
        }

        return dynamic;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.ENTITY, "Painting");
        OpticFinder<?> opticFinder = DSL.namedChoice("Painting", choiceType);
        Type<?> choiceType1 = this.getInputSchema().getChoiceType(References.ENTITY, "ItemFrame");
        OpticFinder<?> opticFinder1 = DSL.namedChoice("ItemFrame", choiceType1);
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        TypeRewriteRule typeRewriteRule = this.fixTypeEverywhereTyped(
            "EntityPaintingFix",
            type,
            typed -> typed.updateTyped(opticFinder, choiceType, typed1 -> typed1.update(DSL.remainderFinder(), dynamic -> this.doFix(dynamic, true, false)))
        );
        TypeRewriteRule typeRewriteRule1 = this.fixTypeEverywhereTyped(
            "EntityItemFrameFix",
            type,
            typed -> typed.updateTyped(opticFinder1, choiceType1, typed1 -> typed1.update(DSL.remainderFinder(), dynamic -> this.doFix(dynamic, false, true)))
        );
        return TypeRewriteRule.seq(typeRewriteRule, typeRewriteRule1);
    }
}
