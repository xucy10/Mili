package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityFurnaceBurnTimeFix extends NamedEntityFix {
    public BlockEntityFurnaceBurnTimeFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "BlockEntityFurnaceBurnTimeFix" + entityName, References.BLOCK_ENTITY, entityName);
    }

    public Dynamic<?> fixBurnTime(Dynamic<?> tag) {
        tag = tag.renameField("CookTime", "cooking_time_spent");
        tag = tag.renameField("CookTimeTotal", "cooking_total_time");
        tag = tag.renameField("BurnTime", "lit_time_remaining");
        return tag.setFieldIfPresent("lit_total_time", tag.get("lit_time_remaining").result());
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixBurnTime);
    }
}
