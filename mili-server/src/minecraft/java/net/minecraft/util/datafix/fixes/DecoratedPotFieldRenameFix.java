package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class DecoratedPotFieldRenameFix extends DataFix {
    private static final String DECORATED_POT_ID = "minecraft:decorated_pot";

    public DecoratedPotFieldRenameFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:decorated_pot");
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:decorated_pot");
        return this.convertUnchecked("DecoratedPotFieldRenameFix", choiceType, choiceType1);
    }
}
