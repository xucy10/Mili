package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsAddTextBackgroundFix extends DataFix {
    public OptionsAddTextBackgroundFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsAddTextBackgroundFix",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(DSL.remainderFinder(), dynamic -> DataFixUtils.orElse(dynamic.get("chatOpacity").asString().map(string -> {
                double d = this.calculateBackground(string);
                return dynamic.set("textBackgroundOpacity", dynamic.createString(String.valueOf(d)));
            }).result(), dynamic))
        );
    }

    private double calculateBackground(String oldBackground) {
        try {
            double d = 0.9 * Double.parseDouble(oldBackground) + 0.1;
            return d / 2.0;
        } catch (NumberFormatException var4) {
            return 0.5;
        }
    }
}
