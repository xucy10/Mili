package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;

public class BlockEntitySignDoubleSidedEditableTextFix extends NamedEntityWriteReadFix {
    public static final List<String> FIELDS_TO_DROP = List.of(
        "Text1", "Text2", "Text3", "Text4", "FilteredText1", "FilteredText2", "FilteredText3", "FilteredText4", "Color", "GlowingText"
    );
    public static final String FILTERED_CORRECT = "_filtered_correct";
    private static final String DEFAULT_COLOR = "black";

    public BlockEntitySignDoubleSidedEditableTextFix(Schema outputSchema, String name, String entityName) {
        super(outputSchema, true, name, References.BLOCK_ENTITY, entityName);
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        tag = tag.set("front_text", fixFrontTextTag(tag))
            .set("back_text", createDefaultText(tag))
            .set("is_waxed", tag.createBoolean(false))
            .set("_filtered_correct", tag.createBoolean(true));

        for (String string : FIELDS_TO_DROP) {
            tag = tag.remove(string);
        }

        return tag;
    }

    private static <T> Dynamic<T> fixFrontTextTag(Dynamic<T> tag) {
        Dynamic<T> dynamic = LegacyComponentDataFixUtils.createEmptyComponent(tag.getOps());
        List<Dynamic<T>> list = getLines(tag, "Text").map(optional -> optional.orElse(dynamic)).toList();
        Dynamic<T> dynamic1 = tag.emptyMap()
            .set("messages", tag.createList(list.stream()))
            .set("color", tag.get("Color").result().orElse(tag.createString("black")))
            .set("has_glowing_text", tag.get("GlowingText").result().orElse(tag.createBoolean(false)));
        List<Optional<Dynamic<T>>> list1 = getLines(tag, "FilteredText").toList();
        if (list1.stream().anyMatch(Optional::isPresent)) {
            dynamic1 = dynamic1.set("filtered_messages", tag.createList(Streams.mapWithIndex(list1.stream(), (optional, l) -> {
                Dynamic<T> dynamic2 = list.get((int)l);
                return optional.orElse(dynamic2);
            })));
        }

        return dynamic1;
    }

    private static <T> Stream<Optional<Dynamic<T>>> getLines(Dynamic<T> dynamic, String prefix) {
        return Stream.of(
            dynamic.get(prefix + "1").result(), dynamic.get(prefix + "2").result(), dynamic.get(prefix + "3").result(), dynamic.get(prefix + "4").result()
        );
    }

    private static <T> Dynamic<T> createDefaultText(Dynamic<T> dynamic) {
        return dynamic.emptyMap()
            .set("messages", createEmptyLines(dynamic))
            .set("color", dynamic.createString("black"))
            .set("has_glowing_text", dynamic.createBoolean(false));
    }

    private static <T> Dynamic<T> createEmptyLines(Dynamic<T> dynamic) {
        Dynamic<T> dynamic1 = LegacyComponentDataFixUtils.createEmptyComponent(dynamic.getOps());
        return dynamic.createList(Stream.of(dynamic1, dynamic1, dynamic1, dynamic1));
    }
}
