package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsGraphicsModeSplitFix extends DataFix {
    private final String newFieldName;
    private final String valueIfFast;
    private final String valueIfFancy;
    private final String valueIfFabulous;

    public OptionsGraphicsModeSplitFix(Schema outputSchema, String newFieldName, String valueIfFast, String valueIfFancy, String valueIfFabulous) {
        super(outputSchema, true);
        this.newFieldName = newFieldName;
        this.valueIfFast = valueIfFast;
        this.valueIfFancy = valueIfFancy;
        this.valueIfFabulous = valueIfFabulous;
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "graphicsMode split to " + this.newFieldName,
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> DataFixUtils.orElseGet(
                    dynamic.get("graphicsMode").asString().map(string -> dynamic.set(this.newFieldName, dynamic.createString(this.getValue(string)))).result(),
                    () -> dynamic.set(this.newFieldName, dynamic.createString(this.valueIfFancy))
                )
            )
        );
    }

    private String getValue(String valueId) {
        return switch (valueId) {
            case "2" -> this.valueIfFabulous;
            case "0" -> this.valueIfFast;
            default -> this.valueIfFancy;
        };
    }
}
