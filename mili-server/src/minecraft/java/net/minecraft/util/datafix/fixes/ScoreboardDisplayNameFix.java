package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class ScoreboardDisplayNameFix extends DataFix {
    private final String name;
    private final TypeReference type;

    public ScoreboardDisplayNameFix(Schema outputSchema, String name, TypeReference type) {
        super(outputSchema, false);
        this.name = name;
        this.type = type;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        OpticFinder<?> opticFinder = type.findField("DisplayName");
        OpticFinder<Pair<String, String>> opticFinder1 = DSL.typeFinder((Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT));
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            typed -> typed.updateTyped(
                opticFinder, typed1 -> typed1.update(opticFinder1, pair -> pair.mapSecond(LegacyComponentDataFixUtils::createTextComponentJson))
            )
        );
    }
}
