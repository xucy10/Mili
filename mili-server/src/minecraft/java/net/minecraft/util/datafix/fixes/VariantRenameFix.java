package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import java.util.Map;

public class VariantRenameFix extends NamedEntityFix {
    private final Map<String, String> renames;

    public VariantRenameFix(Schema outputSchema, String name, TypeReference type, String entityName, Map<String, String> renames) {
        super(outputSchema, false, name, type, entityName);
        this.renames = renames;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "variant",
                dynamic1 -> DataFixUtils.orElse(
                    dynamic1.asString().map(string -> dynamic1.createString(this.renames.getOrDefault(string, string))).result(), dynamic1
                )
            )
        );
    }
}
