package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String entityName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = entityName;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> choiceType = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type1 = this.getOutputSchema().getType(this.type);
        OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, choiceType);
        Type<?> type2 = ExtraDataFixUtils.patchSubType(type, type, type1);
        return this.fix(type, type1, type2, opticFinder);
    }

    private <S, T, A> TypeRewriteRule fix(Type<S> inputType, Type<T> outputType, Type<?> type, OpticFinder<A> optic) {
        return this.fixTypeEverywhereTyped(this.name, inputType, outputType, typed -> {
            if (typed.getOptional(optic).isEmpty()) {
                return ExtraDataFixUtils.cast(outputType, typed);
            } else {
                Typed<?> typed1 = ExtraDataFixUtils.cast(type, typed);
                return Util.writeAndReadTypedOrThrow(typed1, outputType, this::fix);
            }
        });
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> tag);
}
