package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class AddFlagIfNotPresentFix extends DataFix {
    private final String name;
    private final boolean flagValue;
    private final String flagKey;
    private final TypeReference typeReference;

    public AddFlagIfNotPresentFix(Schema outputSchema, TypeReference typeReference, String flagKey, boolean flagValue) {
        super(outputSchema, true);
        this.flagValue = flagValue;
        this.flagKey = flagKey;
        this.name = "AddFlagIfNotPresentFix_" + this.flagKey + "=" + this.flagValue + " for " + outputSchema.getVersionKey();
        this.typeReference = typeReference;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.typeReference);
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.set(this.flagKey, DataFixUtils.orElseGet(dynamic.get(this.flagKey).result(), () -> dynamic.createBoolean(this.flagValue)))
            )
        );
    }
}
