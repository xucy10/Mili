package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public abstract class EntityRenameFix extends DataFix {
    protected final String name;

    public EntityRenameFix(String name, Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
        this.name = name;
    }

    @Override
    public TypeRewriteRule makeRule() {
        TaggedChoiceType<String> taggedChoiceType = (TaggedChoiceType<String>)this.getInputSchema().findChoiceType(References.ENTITY);
        TaggedChoiceType<String> taggedChoiceType1 = (TaggedChoiceType<String>)this.getOutputSchema().findChoiceType(References.ENTITY);
        Function<String, Type<?>> function = Util.memoize(string -> {
            Type<?> type = taggedChoiceType.types().get(string);
            return ExtraDataFixUtils.patchSubType(type, taggedChoiceType, taggedChoiceType1);
        });
        return this.fixTypeEverywhere(
            this.name,
            taggedChoiceType,
            taggedChoiceType1,
            dynamicOps -> pair -> {
                String string = pair.getFirst();
                Type<?> type = function.apply(string);
                Pair<String, Typed<?>> pair1 = this.fix(string, this.getEntity(pair.getSecond(), dynamicOps, type));
                Type<?> type1 = taggedChoiceType1.types().get(pair1.getFirst());
                if (!type1.equals(pair1.getSecond().getType(), true, true)) {
                    throw new IllegalStateException(
                        String.format(Locale.ROOT, "Dynamic type check failed: %s not equal to %s", type1, pair1.getSecond().getType())
                    );
                } else {
                    return Pair.of(pair1.getFirst(), pair1.getSecond().getValue());
                }
            }
        );
    }

    private <A> Typed<A> getEntity(Object value, DynamicOps<?> ops, Type<A> type) {
        return new Typed<>(type, ops, (A)value);
    }

    protected abstract Pair<String, Typed<?>> fix(String entityName, Typed<?> typed);
}
