package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class WrittenBookPagesStrictJsonFix extends ItemStackTagFix {
    public WrittenBookPagesStrictJsonFix(Schema outputSchema) {
        super(outputSchema, "WrittenBookPagesStrictJsonFix", string -> string.equals("minecraft:written_book"));
    }

    @Override
    protected Typed<?> fixItemStackTag(Typed<?> data) {
        Type<Pair<String, String>> type = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
        Type<?> type1 = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type1.findField("tag");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("pages");
        OpticFinder<Pair<String, String>> opticFinder2 = DSL.typeFinder(type);
        return data.updateTyped(opticFinder1, typed -> typed.update(opticFinder2, pair -> pair.mapSecond(LegacyComponentDataFixUtils::rewriteFromLenient)));
    }
}
