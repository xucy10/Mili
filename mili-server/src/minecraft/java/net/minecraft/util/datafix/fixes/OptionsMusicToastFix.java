package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsMusicToastFix extends DataFix {
    public OptionsMusicToastFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsMusicToastFix",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.renameAndFixField(
                    "showNowPlayingToast",
                    "musicToast",
                    dynamic1 -> dynamic.createString(dynamic1.asString("false").equals("false") ? "never" : "pause_and_toast")
                )
            )
        );
    }
}
