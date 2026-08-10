package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.Optional;
import java.util.UUID;

public class EntityStringUuidFix extends DataFix {
    public EntityStringUuidFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityStringUuidFix",
            this.getInputSchema().getType(References.ENTITY),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> {
                    Optional<String> optional = dynamic.get("UUID").asString().result();
                    if (optional.isPresent()) {
                        UUID uuid = UUID.fromString(optional.get());
                        return dynamic.remove("UUID")
                            .set("UUIDMost", dynamic.createLong(uuid.getMostSignificantBits()))
                            .set("UUIDLeast", dynamic.createLong(uuid.getLeastSignificantBits()));
                    } else {
                        return dynamic;
                    }
                }
            )
        );
    }
}
