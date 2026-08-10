package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.function.Function;

public class AdvancementsRenameFix extends DataFix {
    private final String name;
    private final Function<String, String> renamer;

    public AdvancementsRenameFix(Schema outputSchema, boolean changesType, String name, Function<String, String> renamer) {
        super(outputSchema, changesType);
        this.name = name;
        this.renamer = renamer;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.name,
            this.getInputSchema().getType(References.ADVANCEMENTS),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.updateMapValues(pair -> {
                String string = pair.getFirst().asString("");
                return pair.mapFirst(dynamic1 -> dynamic.createString(this.renamer.apply(string)));
            }))
        );
    }
}
