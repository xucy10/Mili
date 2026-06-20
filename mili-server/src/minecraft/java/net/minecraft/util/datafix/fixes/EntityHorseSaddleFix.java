package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityHorseSaddleFix extends NamedEntityFix {
    public EntityHorseSaddleFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityHorseSaddleFix", References.ENTITY, "EntityHorse");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> typeRaw = this.getInputSchema().getTypeRaw(References.ITEM_STACK);
        OpticFinder<?> opticFinder1 = DSL.fieldFinder("SaddleItem", typeRaw);
        Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder1);
        Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
        if (optionalTyped.isEmpty() && dynamic.get("Saddle").asBoolean(false)) {
            Typed<?> typed1 = typeRaw.pointTyped(typed.getOps()).orElseThrow(IllegalStateException::new);
            typed1 = typed1.set(opticFinder, Pair.of(References.ITEM_NAME.typeName(), "minecraft:saddle"));
            Dynamic<?> dynamic1 = dynamic.emptyMap();
            dynamic1 = dynamic1.set("Count", dynamic1.createByte((byte)1));
            dynamic1 = dynamic1.set("Damage", dynamic1.createShort((short)0));
            typed1 = typed1.set(DSL.remainderFinder(), dynamic1);
            dynamic.remove("Saddle");
            return typed.set(opticFinder1, typed1).set(DSL.remainderFinder(), dynamic);
        } else {
            return typed;
        }
    }
}
