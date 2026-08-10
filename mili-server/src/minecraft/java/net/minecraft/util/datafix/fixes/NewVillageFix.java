package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.CompoundList.CompoundListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class NewVillageFix extends DataFix {
    public NewVillageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        CompoundListType<String, ?> compoundListType = DSL.compoundList(DSL.string(), this.getInputSchema().getType(References.STRUCTURE_FEATURE));
        OpticFinder<? extends List<? extends Pair<String, ?>>> opticFinder = compoundListType.finder();
        return this.cap(compoundListType);
    }

    private <SF> TypeRewriteRule cap(CompoundListType<String, SF> type) {
        Type<?> type1 = this.getInputSchema().getType(References.CHUNK);
        Type<?> type2 = this.getInputSchema().getType(References.STRUCTURE_FEATURE);
        OpticFinder<?> opticFinder = type1.findField("Level");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Structures");
        OpticFinder<?> opticFinder2 = opticFinder1.type().findField("Starts");
        OpticFinder<List<Pair<String, SF>>> opticFinder3 = type.finder();
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(
                "NewVillageFix",
                type1,
                typed -> typed.updateTyped(
                    opticFinder,
                    typed1 -> typed1.updateTyped(
                        opticFinder1,
                        typed2 -> typed2.updateTyped(
                                opticFinder2,
                                typed3 -> typed3.update(
                                    opticFinder3,
                                    list -> list.stream()
                                        .filter(pair -> !Objects.equals(pair.getFirst(), "Village"))
                                        .map(pair -> pair.mapFirst(string -> string.equals("New_Village") ? "Village" : string))
                                        .collect(Collectors.toList())
                                )
                            )
                            .update(
                                DSL.remainderFinder(),
                                dynamic -> dynamic.update(
                                    "References",
                                    dynamic1 -> {
                                        Optional<? extends Dynamic<?>> optional = dynamic1.get("New_Village").result();
                                        return DataFixUtils.orElse(
                                                optional.map(dynamic2 -> dynamic1.remove("New_Village").set("Village", (Dynamic<?>)dynamic2)), dynamic1
                                            )
                                            .remove("Village");
                                    }
                                )
                            )
                    )
                )
            ),
            this.fixTypeEverywhereTyped(
                "NewVillageStartFix",
                type2,
                typed -> typed.update(
                    DSL.remainderFinder(),
                    dynamic -> dynamic.update(
                        "id",
                        dynamic1 -> Objects.equals(NamespacedSchema.ensureNamespaced(dynamic1.asString("")), "minecraft:new_village")
                            ? dynamic1.createString("minecraft:village")
                            : dynamic1
                    )
                )
            )
        );
    }
}
