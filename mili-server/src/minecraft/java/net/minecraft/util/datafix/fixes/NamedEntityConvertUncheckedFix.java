package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class NamedEntityConvertUncheckedFix extends NamedEntityFix {
    public NamedEntityConvertUncheckedFix(Schema outputSchema, String name, TypeReference type, String entityName) {
        super(outputSchema, true, name, type, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        Type<?> choiceType = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        return ExtraDataFixUtils.cast(choiceType, typed);
    }
}
