package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EffectDurationFix extends DataFix {
    private static final Set<String> POTION_ITEMS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );

    public EffectDurationFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("tag");
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(
                "EffectDurationEntity", inputSchema.getType(References.ENTITY), typed -> typed.update(DSL.remainderFinder(), this::updateEntity)
            ),
            this.fixTypeEverywhereTyped(
                "EffectDurationPlayer", inputSchema.getType(References.PLAYER), typed -> typed.update(DSL.remainderFinder(), this::updateEntity)
            ),
            this.fixTypeEverywhereTyped("EffectDurationItem", type, typed -> {
                if (typed.getOptional(opticFinder).filter(pair -> POTION_ITEMS.contains(pair.getSecond())).isPresent()) {
                    Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder1);
                    if (optionalTyped.isPresent()) {
                        Dynamic<?> dynamic = optionalTyped.get().get(DSL.remainderFinder());
                        Typed<?> typed1 = optionalTyped.get().set(DSL.remainderFinder(), dynamic.update("CustomPotionEffects", this::fix));
                        return typed.set(opticFinder1, typed1);
                    }
                }

                return typed;
            })
        );
    }

    private Dynamic<?> fixEffect(Dynamic<?> dynamic) {
        return dynamic.update("FactorCalculationData", dynamic1 -> {
            int _int = dynamic1.get("effect_changed_timestamp").asInt(-1);
            dynamic1 = dynamic1.remove("effect_changed_timestamp");
            int _int1 = dynamic.get("Duration").asInt(-1);
            int i = _int - _int1;
            return dynamic1.set("ticks_active", dynamic1.createInt(i));
        });
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        return dynamic.createList(dynamic.asStream().map(this::fixEffect));
    }

    private Dynamic<?> updateEntity(Dynamic<?> entityTag) {
        entityTag = entityTag.update("Effects", this::fix);
        entityTag = entityTag.update("ActiveEffects", this::fix);
        return entityTag.update("CustomPotionEffects", this::fix);
    }
}
