package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class BlockEntityJukeboxFix extends NamedEntityFix {
    public BlockEntityJukeboxFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityJukeboxFix", References.BLOCK_ENTITY, "minecraft:jukebox");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:jukebox");
        Type<?> type = choiceType.findFieldType("RecordItem");
        OpticFinder<?> opticFinder = DSL.fieldFinder("RecordItem", type);
        Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
        int _int = dynamic.get("Record").asInt(0);
        if (_int > 0) {
            dynamic.remove("Record");
            String string = ItemStackTheFlatteningFix.updateItem(ItemIdFix.getItem(_int), 0);
            if (string != null) {
                Dynamic<?> dynamic1 = dynamic.emptyMap();
                dynamic1 = dynamic1.set("id", dynamic1.createString(string));
                dynamic1 = dynamic1.set("Count", dynamic1.createByte((byte)1));
                return typed.set(
                        opticFinder,
                        type.readTyped(dynamic1).result().orElseThrow(() -> new IllegalStateException("Could not create record item stack.")).getFirst()
                    )
                    .set(DSL.remainderFinder(), dynamic);
            }
        }

        return typed;
    }
}
