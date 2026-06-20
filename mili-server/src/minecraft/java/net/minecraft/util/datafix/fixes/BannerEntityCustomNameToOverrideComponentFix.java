package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class BannerEntityCustomNameToOverrideComponentFix extends DataFix {
    public BannerEntityCustomNameToOverrideComponentFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        TaggedChoiceType<?> taggedChoiceType = this.getInputSchema().findChoiceType(References.BLOCK_ENTITY);
        OpticFinder<?> opticFinder = type.findField("CustomName");
        OpticFinder<Pair<String, String>> opticFinder1 = DSL.typeFinder((Type<Pair<String, String>>)this.getInputSchema().getType(References.TEXT_COMPONENT));
        return this.fixTypeEverywhereTyped("Banner entity custom_name to item_name component fix", type, typed -> {
            Object first = typed.get(taggedChoiceType.finder()).getFirst();
            return first.equals("minecraft:banner") ? this.fix(typed, opticFinder1, opticFinder) : typed;
        });
    }

    private Typed<?> fix(Typed<?> data, OpticFinder<Pair<String, String>> textComponentTypeOptic, OpticFinder<?> customNameOptic) {
        Optional<String> optional = data.getOptionalTyped(customNameOptic).flatMap(typed -> typed.getOptional(textComponentTypeOptic).map(Pair::getSecond));
        boolean isPresent = optional.flatMap(LegacyComponentDataFixUtils::extractTranslationString)
            .filter(string -> string.equals("block.minecraft.ominous_banner"))
            .isPresent();
        return isPresent
            ? Util.writeAndReadTypedOrThrow(
                data,
                data.getType(),
                dynamic -> {
                    Dynamic<?> dynamic1 = dynamic.createMap(
                        Map.of(
                            dynamic.createString("minecraft:item_name"),
                            dynamic.createString(optional.get()),
                            dynamic.createString("minecraft:hide_additional_tooltip"),
                            dynamic.emptyMap()
                        )
                    );
                    return dynamic.set("components", dynamic1).remove("CustomName");
                }
            )
            : data;
    }
}
