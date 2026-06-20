package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class WorldBorderWarningTimeFix extends DataFix {
    public WorldBorderWarningTimeFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "WorldBorderWarningTimeFix",
            this.getInputSchema().getType(References.SAVED_DATA_WORLD_BORDER),
            this.getOutputSchema().getType(References.SAVED_DATA_WORLD_BORDER),
            dynamic -> dynamic.update("data", dynamic1 -> dynamic1.update("warning_time", dynamic2 -> dynamic1.createInt(dynamic2.asInt(15) * 20)))
        );
    }
}
