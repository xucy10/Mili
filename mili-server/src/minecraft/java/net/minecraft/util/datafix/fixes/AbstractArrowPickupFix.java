package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;

public class AbstractArrowPickupFix extends DataFix {
    public AbstractArrowPickupFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        return this.fixTypeEverywhereTyped("AbstractArrowPickupFix", inputSchema.getType(References.ENTITY), this::updateProjectiles);
    }

    private Typed<?> updateProjectiles(Typed<?> typed) {
        typed = this.updateEntity(typed, "minecraft:arrow", AbstractArrowPickupFix::updatePickup);
        typed = this.updateEntity(typed, "minecraft:spectral_arrow", AbstractArrowPickupFix::updatePickup);
        return this.updateEntity(typed, "minecraft:trident", AbstractArrowPickupFix::updatePickup);
    }

    private static Dynamic<?> updatePickup(Dynamic<?> dynamic) {
        if (dynamic.get("pickup").result().isPresent()) {
            return dynamic;
        } else {
            boolean _boolean = dynamic.get("player").asBoolean(true);
            return dynamic.set("pickup", dynamic.createByte((byte)(_boolean ? 1 : 0))).remove("player");
        }
    }

    private Typed<?> updateEntity(Typed<?> typed, String choiceName, Function<Dynamic<?>, Dynamic<?>> updater) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.ENTITY, choiceName);
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(References.ENTITY, choiceName);
        return typed.updateTyped(DSL.namedChoice(choiceName, choiceType), choiceType1, typed1 -> typed1.update(DSL.remainderFinder(), updater));
    }
}
