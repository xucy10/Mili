package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class DropInvalidSignDataFix extends DataFix {
    private final String entityName;

    public DropInvalidSignDataFix(Schema outputSchema, String entityName) {
        super(outputSchema, false);
        this.entityName = entityName;
    }

    private <T> Dynamic<T> fix(Dynamic<T> data) {
        data = data.update("front_text", DropInvalidSignDataFix::fixText);
        data = data.update("back_text", DropInvalidSignDataFix::fixText);

        for (String string : BlockEntitySignDoubleSidedEditableTextFix.FIELDS_TO_DROP) {
            data = data.remove(string);
        }

        return data;
    }

    private static <T> Dynamic<T> fixText(Dynamic<T> textDynamic) {
        Optional<Stream<Dynamic<T>>> optional = textDynamic.get("filtered_messages").asStreamOpt().result();
        if (optional.isEmpty()) {
            return textDynamic;
        } else {
            Dynamic<T> dynamic = LegacyComponentDataFixUtils.createEmptyComponent(textDynamic.getOps());
            List<Dynamic<T>> list = textDynamic.get("messages").asStreamOpt().result().orElse(Stream.of()).toList();
            List<Dynamic<T>> list1 = Streams.mapWithIndex(optional.get(), (dynamic1, l) -> {
                Dynamic<T> dynamic2 = l < list.size() ? list.get((int)l) : dynamic;
                return dynamic1.equals(dynamic) ? dynamic2 : dynamic1;
            }).toList();
            return list1.equals(list) ? textDynamic.remove("filtered_messages") : textDynamic.set("filtered_messages", textDynamic.createList(list1.stream()));
        }
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> choiceType = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, this.entityName);
        OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, choiceType);
        return this.fixTypeEverywhereTyped(
            "DropInvalidSignDataFix for " + this.entityName,
            type,
            typed -> typed.updateTyped(
                opticFinder,
                choiceType,
                typed1 -> {
                    boolean _boolean = typed1.get(DSL.remainderFinder()).get("_filtered_correct").asBoolean(false);
                    return _boolean
                        ? typed1.update(DSL.remainderFinder(), dynamic -> dynamic.remove("_filtered_correct"))
                        : Util.writeAndReadTypedOrThrow(typed1, choiceType, this::fix);
                }
            )
        );
    }
}
