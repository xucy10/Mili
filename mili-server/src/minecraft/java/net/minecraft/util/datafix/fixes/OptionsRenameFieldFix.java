package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsRenameFieldFix extends DataFix {
    private final String fixName;
    private final String fieldFrom;
    private final String fieldTo;

    public OptionsRenameFieldFix(Schema outputSchema, boolean changesType, String fixName, String fieldFrom, String fieldTo) {
        super(outputSchema, changesType);
        this.fixName = fixName;
        this.fieldFrom = fieldFrom;
        this.fieldTo = fieldTo;
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.fixName,
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.renameField(this.fieldFrom, this.fieldTo))
        );
    }
}
