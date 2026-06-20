package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class InvalidBlockEntityLockFix extends DataFix {
    public InvalidBlockEntityLockFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "BlockEntityLockToComponentFix", this.getInputSchema().getType(References.BLOCK_ENTITY), typed -> typed.update(DSL.remainderFinder(), tag -> {
                Optional<? extends Dynamic<?>> optional = tag.get("lock").result();
                if (optional.isEmpty()) {
                    return tag;
                } else {
                    Dynamic<?> dynamic = InvalidLockComponentFix.fixLock((Dynamic<?>)optional.get());
                    return dynamic != null ? tag.set("lock", dynamic) : tag.remove("lock");
                }
            })
        );
    }
}
