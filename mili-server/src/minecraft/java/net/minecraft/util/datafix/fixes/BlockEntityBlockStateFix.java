package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class BlockEntityBlockStateFix extends NamedEntityFix {
    public BlockEntityBlockStateFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBlockStateFix", References.BLOCK_ENTITY, "minecraft:piston");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        Type<?> choiceType = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:piston");
        Type<?> type = choiceType.findFieldType("blockState");
        OpticFinder<?> opticFinder = DSL.fieldFinder("blockState", type);
        Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
        int _int = dynamic.get("blockId").asInt(0);
        dynamic = dynamic.remove("blockId");
        int i = dynamic.get("blockData").asInt(0) & 15;
        dynamic = dynamic.remove("blockData");
        Dynamic<?> tag = BlockStateData.getTag(_int << 4 | i);
        Typed<?> typed1 = choiceType.pointTyped(typed.getOps()).orElseThrow(() -> new IllegalStateException("Could not create new piston block entity."));
        return typed1.set(DSL.remainderFinder(), dynamic)
            .set(
                opticFinder,
                type.readTyped(tag).result().orElseThrow(() -> new IllegalStateException("Could not parse newly created block state tag.")).getFirst()
            );
    }
}
