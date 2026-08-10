package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.Optional;

public class ObjectiveRenderTypeFix extends DataFix {
    public ObjectiveRenderTypeFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static String getRenderType(String oldRenderType) {
        return oldRenderType.equals("health") ? "hearts" : "integer";
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.OBJECTIVE);
        return this.fixTypeEverywhereTyped("ObjectiveRenderTypeFix", type, typed -> typed.update(DSL.remainderFinder(), dynamic -> {
            Optional<String> optional = dynamic.get("RenderType").asString().result();
            if (optional.isEmpty()) {
                String string = dynamic.get("CriteriaName").asString("");
                String renderType = getRenderType(string);
                return dynamic.set("RenderType", dynamic.createString(renderType));
            } else {
                return dynamic;
            }
        }));
    }
}
