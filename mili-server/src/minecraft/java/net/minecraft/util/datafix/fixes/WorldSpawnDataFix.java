package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.stream.IntStream;

public class WorldSpawnDataFix extends DataFix {
    public WorldSpawnDataFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "WorldSpawnDataFix",
            this.getInputSchema().getType(References.LEVEL),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> {
                    int _int = dynamic.get("SpawnX").asInt(0);
                    int _int1 = dynamic.get("SpawnY").asInt(0);
                    int _int2 = dynamic.get("SpawnZ").asInt(0);
                    float _float = dynamic.get("SpawnAngle").asFloat(0.0F);
                    Dynamic<?> dynamic1 = dynamic.emptyMap()
                        .set("dimension", dynamic.createString("minecraft:overworld"))
                        .set("pos", dynamic.createIntList(IntStream.of(_int, _int1, _int2)))
                        .set("yaw", dynamic.createFloat(_float))
                        .set("pitch", dynamic.createFloat(0.0F));
                    dynamic = dynamic.remove("SpawnX").remove("SpawnY").remove("SpawnZ").remove("SpawnAngle");
                    return dynamic.set("spawn", dynamic1);
                }
            )
        );
    }
}
