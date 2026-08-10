package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import java.util.Map;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class StatsRenameFix extends DataFix {
    private final String name;
    private final Map<String, String> renames;

    public StatsRenameFix(Schema outputSchema, String name, Map<String, String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.createStatRule(), this.createCriteriaRule());
    }

    private TypeRewriteRule createCriteriaRule() {
        Type<?> type = this.getOutputSchema().getType(References.OBJECTIVE);
        Type<?> type1 = this.getInputSchema().getType(References.OBJECTIVE);
        OpticFinder<?> opticFinder = type1.findField("CriteriaType");
        TaggedChoiceType<?> taggedChoiceType = opticFinder.type()
            .findChoiceType("type", -1)
            .orElseThrow(() -> new IllegalStateException("Can't find choice type for criteria"));
        Type<?> type2 = taggedChoiceType.types().get("minecraft:custom");
        if (type2 == null) {
            throw new IllegalStateException("Failed to find custom criterion type variant");
        } else {
            OpticFinder<?> opticFinder1 = DSL.namedChoice("minecraft:custom", type2);
            OpticFinder<String> opticFinder2 = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
            return this.fixTypeEverywhereTyped(
                this.name,
                type1,
                type,
                typed -> typed.updateTyped(
                    opticFinder,
                    typed1 -> typed1.updateTyped(opticFinder1, typed2 -> typed2.update(opticFinder2, string -> this.renames.getOrDefault(string, string)))
                )
            );
        }
    }

    private TypeRewriteRule createStatRule() {
        Type<?> type = this.getOutputSchema().getType(References.STATS);
        Type<?> type1 = this.getInputSchema().getType(References.STATS);
        OpticFinder<?> opticFinder = type1.findField("stats");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("minecraft:custom");
        OpticFinder<String> opticFinder2 = NamespacedSchema.namespacedString().finder();
        return this.fixTypeEverywhereTyped(
            this.name,
            type1,
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> typed1.updateTyped(opticFinder1, typed2 -> typed2.update(opticFinder2, string -> this.renames.getOrDefault(string, string)))
            )
        );
    }
}
