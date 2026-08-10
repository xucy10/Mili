package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.stream.Stream;

public class FoodToConsumableFix extends DataFix {
    public FoodToConsumableFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "Food to consumable fix",
            this.getInputSchema().getType(References.DATA_COMPONENTS),
            this.getOutputSchema().getType(References.DATA_COMPONENTS),
            dynamic -> {
                Optional<? extends Dynamic<?>> optional = dynamic.get("minecraft:food").result();
                if (optional.isPresent()) {
                    float _float = optional.get().get("eat_seconds").asFloat(1.6F);
                    Stream<? extends Dynamic<?>> stream = optional.get().get("effects").asStream();
                    Stream<? extends Dynamic<?>> stream1 = stream.map(
                        dynamic1 -> dynamic1.emptyMap()
                            .set("type", dynamic1.createString("minecraft:apply_effects"))
                            .set("effects", dynamic1.createList(dynamic1.get("effect").result().stream()))
                            .set("probability", dynamic1.createFloat(dynamic1.get("probability").asFloat(1.0F)))
                    );
                    dynamic = Dynamic.copyField((Dynamic<?>)optional.get(), "using_converts_to", dynamic, "minecraft:use_remainder");
                    dynamic = dynamic.set("minecraft:food", optional.get().remove("eat_seconds").remove("effects").remove("using_converts_to"));
                    return dynamic.set(
                        "minecraft:consumable",
                        dynamic.emptyMap().set("consume_seconds", dynamic.createFloat(_float)).set("on_consume_effects", dynamic.createList(stream1))
                    );
                } else {
                    return dynamic;
                }
            }
        );
    }
}
