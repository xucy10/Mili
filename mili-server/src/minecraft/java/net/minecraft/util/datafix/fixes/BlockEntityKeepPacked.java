package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityKeepPacked extends NamedEntityFix {
    public BlockEntityKeepPacked(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityKeepPacked", References.BLOCK_ENTITY, "DUMMY");
    }

    private static Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.set("keepPacked", tag.createBoolean(true));
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), BlockEntityKeepPacked::fixTag);
    }
}
