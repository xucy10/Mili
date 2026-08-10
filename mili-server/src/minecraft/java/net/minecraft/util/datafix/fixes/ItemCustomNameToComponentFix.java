package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class ItemCustomNameToComponentFix extends DataFix {
    public ItemCustomNameToComponentFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        Type<Pair<String, String>> type1 = (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT);
        OpticFinder<?> opticFinder = type.findField("tag");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("display");
        OpticFinder<?> opticFinder2 = opticFinder1.type().findField("Name");
        OpticFinder<Pair<String, String>> opticFinder3 = DSL.typeFinder(type1);
        return this.fixTypeEverywhereTyped(
            "ItemCustomNameToComponentFix",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> typed1.updateTyped(
                    opticFinder1,
                    typed2 -> typed2.updateTyped(
                        opticFinder2, typed3 -> typed3.update(opticFinder3, pair -> pair.mapSecond(LegacyComponentDataFixUtils::createTextComponentJson))
                    )
                )
            )
        );
    }
}
