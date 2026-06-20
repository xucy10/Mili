package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class SignTextStrictJsonFix extends NamedEntityFix {
    private static final List<String> LINE_FIELDS = List.of("Text1", "Text2", "Text3", "Text4");

    public SignTextStrictJsonFix(Schema outputSchema) {
        super(outputSchema, false, "SignTextStrictJsonFix", References.BLOCK_ENTITY, "Sign");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        for (String string : LINE_FIELDS) {
            OpticFinder<?> opticFinder = typed.getType().findField(string);
            OpticFinder<Pair<String, String>> opticFinder1 = DSL.typeFinder(
                (Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT)
            );
            typed = typed.updateTyped(
                opticFinder, typed1 -> typed1.update(opticFinder1, pair -> pair.mapSecond(LegacyComponentDataFixUtils::rewriteFromLenient))
            );
        }

        return typed;
    }
}
