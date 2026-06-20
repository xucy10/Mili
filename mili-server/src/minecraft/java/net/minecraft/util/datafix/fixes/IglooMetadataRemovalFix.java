package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class IglooMetadataRemovalFix extends DataFix {
    public IglooMetadataRemovalFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.STRUCTURE_FEATURE);
        return this.fixTypeEverywhereTyped("IglooMetadataRemovalFix", type, typed -> typed.update(DSL.remainderFinder(), IglooMetadataRemovalFix::fixTag));
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> tag) {
        boolean flag = tag.get("Children").asStreamOpt().map(stream -> stream.allMatch(IglooMetadataRemovalFix::isIglooPiece)).result().orElse(false);
        return flag ? tag.set("id", tag.createString("Igloo")).remove("Children") : tag.update("Children", IglooMetadataRemovalFix::removeIglooPieces);
    }

    private static <T> Dynamic<T> removeIglooPieces(Dynamic<T> dynamic) {
        return dynamic.asStreamOpt()
            .map(stream -> stream.filter(dynamic1 -> !isIglooPiece((Dynamic<?>)dynamic1)))
            .map(dynamic::createList)
            .result()
            .orElse(dynamic);
    }

    private static boolean isIglooPiece(Dynamic<?> dynamic) {
        return dynamic.get("id").asString("").equals("Iglu");
    }
}
