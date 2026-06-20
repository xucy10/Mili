package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;

public class ChestedHorsesInventoryZeroIndexingFix extends DataFix {
    public ChestedHorsesInventoryZeroIndexingFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticFinder = DSL.typeFinder(
            (Type<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>>)this.getInputSchema()
                .getType(References.ITEM_STACK)
        );
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return TypeRewriteRule.seq(
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:llama"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:trader_llama"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:mule"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:donkey")
        );
    }

    private TypeRewriteRule horseLikeInventoryIndexingFixer(
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticFinder, Type<?> type, String entityId
    ) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.ENTITY, entityId);
        OpticFinder<?> opticFinder1 = DSL.namedChoice(entityId, choiceType);
        OpticFinder<?> opticFinder2 = choiceType.findField("Items");
        return this.fixTypeEverywhereTyped(
            "Fix non-zero indexing in chest horse type " + entityId,
            type,
            typed -> typed.updateTyped(
                opticFinder1,
                typed1 -> typed1.updateTyped(
                    opticFinder2,
                    typed2 -> typed2.update(
                        opticFinder,
                        pair -> pair.mapSecond(
                            pair1 -> pair1.mapSecond(
                                pair2 -> pair2.mapSecond(dynamic -> dynamic.update("Slot", dynamic1 -> dynamic1.createByte((byte)(dynamic1.asInt(2) - 2))))
                            )
                        )
                    )
                )
            )
        );
    }
}
