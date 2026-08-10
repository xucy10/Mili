package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;

public class SpawnerDataFix extends DataFix {
    public SpawnerDataFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.UNTAGGED_SPAWNER);
        Type<?> type1 = this.getOutputSchema().getType(References.UNTAGGED_SPAWNER);
        OpticFinder<?> opticFinder = type.findField("SpawnData");
        Type<?> type2 = type1.findField("SpawnData").type();
        OpticFinder<?> opticFinder1 = type.findField("SpawnPotentials");
        Type<?> type3 = type1.findField("SpawnPotentials").type();
        return this.fixTypeEverywhereTyped(
            "Fix mob spawner data structure",
            type,
            type1,
            typed -> typed.updateTyped(opticFinder, type2, typed1 -> this.wrapEntityToSpawnData(type2, typed1))
                .updateTyped(opticFinder1, type3, typed1 -> this.wrapSpawnPotentialsToWeightedEntries(type3, typed1))
        );
    }

    private <T> Typed<T> wrapEntityToSpawnData(Type<T> type, Typed<?> typed) {
        DynamicOps<?> ops = typed.getOps();
        return new Typed<>(type, ops, (T)Pair.<Object, Dynamic<?>>of(typed.getValue(), new Dynamic<>(ops)));
    }

    private <T> Typed<T> wrapSpawnPotentialsToWeightedEntries(Type<T> type, Typed<?> typed) {
        DynamicOps<?> ops = typed.getOps();
        List<?> list = (List<?>)typed.getValue();
        List<?> list1 = list.stream().map(object -> {
            Pair<Object, Dynamic<?>> pair = (Pair<Object, Dynamic<?>>)object;
            int i = pair.getSecond().get("Weight").asNumber().result().orElse(1).intValue();
            Dynamic<?> dynamic = new Dynamic<>(ops);
            dynamic = dynamic.set("weight", dynamic.createInt(i));
            Dynamic<?> dynamic1 = pair.getSecond().remove("Weight").remove("Entity");
            return Pair.of(Pair.of(pair.getFirst(), dynamic1), dynamic);
        }).toList();
        return new Typed<>(type, ops, (T)list1);
    }
}
