package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Util;

public class StructureSettingsFlattenFix extends DataFix {
    public StructureSettingsFlattenFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.WORLD_GEN_SETTINGS);
        OpticFinder<?> opticFinder = type.findField("dimensions");
        return this.fixTypeEverywhereTyped(
            "StructureSettingsFlatten",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> Util.writeAndReadTypedOrThrow(
                    typed1, opticFinder.type(), dynamic -> dynamic.updateMapValues(StructureSettingsFlattenFix::fixDimension)
                )
            )
        );
    }

    private static Pair<Dynamic<?>, Dynamic<?>> fixDimension(Pair<Dynamic<?>, Dynamic<?>> dimensions) {
        Dynamic<?> dynamic = dimensions.getSecond();
        return Pair.of(
            dimensions.getFirst(),
            dynamic.update(
                "generator", dynamic1 -> dynamic1.update("settings", dynamic2 -> dynamic2.update("structures", StructureSettingsFlattenFix::fixStructures))
            )
        );
    }

    private static Dynamic<?> fixStructures(Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = dynamic.get("structures")
            .orElseEmptyMap()
            .updateMapValues(pair -> pair.mapSecond(dynamic2 -> dynamic2.set("type", dynamic.createString("minecraft:random_spread"))));
        return DataFixUtils.orElse(
            dynamic.get("stronghold")
                .result()
                .map(dynamic2 -> dynamic1.set("minecraft:stronghold", dynamic2.set("type", dynamic.createString("minecraft:concentric_rings")))),
            dynamic1
        );
    }
}
