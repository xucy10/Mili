package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BeehiveFieldRenameFix extends DataFix {
    public BeehiveFieldRenameFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private Dynamic<?> fixBeehive(Dynamic<?> tag) {
        return tag.remove("Bees");
    }

    private Dynamic<?> fixBee(Dynamic<?> tag) {
        tag = tag.remove("EntityData");
        tag = tag.renameField("TicksInHive", "ticks_in_hive");
        return tag.renameField("MinOccupationTicks", "min_ticks_in_hive");
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:beehive");
        OpticFinder<?> opticFinder = DSL.namedChoice("minecraft:beehive", choiceType);
        ListType<?> listType = (ListType<?>)choiceType.findFieldType("Bees");
        Type<?> element = listType.getElement();
        OpticFinder<?> opticFinder1 = DSL.fieldFinder("Bees", listType);
        OpticFinder<?> opticFinder2 = DSL.typeFinder(element);
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BeehiveFieldRenameFix",
            type,
            type1,
            typed -> ExtraDataFixUtils.cast(
                type1,
                typed.updateTyped(
                    opticFinder,
                    typed1 -> typed1.update(DSL.remainderFinder(), this::fixBeehive)
                        .updateTyped(opticFinder1, typed2 -> typed2.updateTyped(opticFinder2, typed3 -> typed3.update(DSL.remainderFinder(), this::fixBee)))
                )
            )
        );
    }
}
