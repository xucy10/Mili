package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class PlayerUUIDFix extends AbstractUUIDFix {
    public PlayerUUIDFix(Schema outputSchema) {
        super(outputSchema, References.PLAYER);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "PlayerUUIDFix",
            this.getInputSchema().getType(this.typeReference),
            typed -> {
                OpticFinder<?> opticFinder = typed.getType().findField("RootVehicle");
                return typed.updateTyped(
                        opticFinder,
                        opticFinder.type(),
                        typed1 -> typed1.update(DSL.remainderFinder(), dynamic -> replaceUUIDLeastMost(dynamic, "Attach", "Attach").orElse(dynamic))
                    )
                    .update(DSL.remainderFinder(), dynamic -> EntityUUIDFix.updateEntityUUID(EntityUUIDFix.updateLivingEntity(dynamic)));
            }
        );
    }
}
