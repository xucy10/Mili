package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;

public class OverreachingTickFix extends DataFix {
    public OverreachingTickFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("block_ticks");
        return this.fixTypeEverywhereTyped("Handle ticks saved in the wrong chunk", type, typed -> {
            Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder);
            Optional<? extends Dynamic<?>> optional = optionalTyped.isPresent() ? optionalTyped.get().write().result() : Optional.empty();
            return typed.update(DSL.remainderFinder(), dynamic -> {
                int _int = dynamic.get("xPos").asInt(0);
                int _int1 = dynamic.get("zPos").asInt(0);
                Optional<? extends Dynamic<?>> optional1 = dynamic.get("fluid_ticks").get().result();
                dynamic = extractOverreachingTicks(dynamic, _int, _int1, optional, "neighbor_block_ticks");
                return extractOverreachingTicks(dynamic, _int, _int1, optional1, "neighbor_fluid_ticks");
            });
        });
    }

    private static Dynamic<?> extractOverreachingTicks(Dynamic<?> tag, int x, int z, Optional<? extends Dynamic<?>> ticks, String id) {
        if (ticks.isPresent()) {
            List<? extends Dynamic<?>> list = ticks.get().asStream().filter(dynamic -> {
                int _int = dynamic.get("x").asInt(0);
                int _int1 = dynamic.get("z").asInt(0);
                int abs = Math.abs(x - (_int >> 4));
                int abs1 = Math.abs(z - (_int1 >> 4));
                return (abs != 0 || abs1 != 0) && abs <= 1 && abs1 <= 1;
            }).toList();
            if (!list.isEmpty()) {
                tag = tag.set("UpgradeData", tag.get("UpgradeData").orElseEmptyMap().set(id, tag.createList(list.stream())));
            }
        }

        return tag;
    }
}
