package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BannerPatternFormatFix extends NamedEntityFix {
    private static final Map<String, String> PATTERN_ID_MAP = Map.ofEntries(
        Map.entry("b", "minecraft:base"),
        Map.entry("bl", "minecraft:square_bottom_left"),
        Map.entry("br", "minecraft:square_bottom_right"),
        Map.entry("tl", "minecraft:square_top_left"),
        Map.entry("tr", "minecraft:square_top_right"),
        Map.entry("bs", "minecraft:stripe_bottom"),
        Map.entry("ts", "minecraft:stripe_top"),
        Map.entry("ls", "minecraft:stripe_left"),
        Map.entry("rs", "minecraft:stripe_right"),
        Map.entry("cs", "minecraft:stripe_center"),
        Map.entry("ms", "minecraft:stripe_middle"),
        Map.entry("drs", "minecraft:stripe_downright"),
        Map.entry("dls", "minecraft:stripe_downleft"),
        Map.entry("ss", "minecraft:small_stripes"),
        Map.entry("cr", "minecraft:cross"),
        Map.entry("sc", "minecraft:straight_cross"),
        Map.entry("bt", "minecraft:triangle_bottom"),
        Map.entry("tt", "minecraft:triangle_top"),
        Map.entry("bts", "minecraft:triangles_bottom"),
        Map.entry("tts", "minecraft:triangles_top"),
        Map.entry("ld", "minecraft:diagonal_left"),
        Map.entry("rd", "minecraft:diagonal_up_right"),
        Map.entry("lud", "minecraft:diagonal_up_left"),
        Map.entry("rud", "minecraft:diagonal_right"),
        Map.entry("mc", "minecraft:circle"),
        Map.entry("mr", "minecraft:rhombus"),
        Map.entry("vh", "minecraft:half_vertical"),
        Map.entry("hh", "minecraft:half_horizontal"),
        Map.entry("vhr", "minecraft:half_vertical_right"),
        Map.entry("hhb", "minecraft:half_horizontal_bottom"),
        Map.entry("bo", "minecraft:border"),
        Map.entry("cbo", "minecraft:curly_border"),
        Map.entry("gra", "minecraft:gradient"),
        Map.entry("gru", "minecraft:gradient_up"),
        Map.entry("bri", "minecraft:bricks"),
        Map.entry("glb", "minecraft:globe"),
        Map.entry("cre", "minecraft:creeper"),
        Map.entry("sku", "minecraft:skull"),
        Map.entry("flo", "minecraft:flower"),
        Map.entry("moj", "minecraft:mojang"),
        Map.entry("pig", "minecraft:piglin")
    );

    public BannerPatternFormatFix(Schema outputSchema) {
        super(outputSchema, false, "BannerPatternFormatFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), BannerPatternFormatFix::fixTag);
    }

    private static Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.renameAndFixField("Patterns", "patterns", dynamic -> dynamic.createList(dynamic.asStream().map(BannerPatternFormatFix::fixLayer)));
    }

    private static Dynamic<?> fixLayer(Dynamic<?> tag) {
        tag = tag.renameAndFixField(
            "Pattern",
            "pattern",
            dynamic -> DataFixUtils.orElse(
                dynamic.asString().map(string -> PATTERN_ID_MAP.getOrDefault(string, string)).map(dynamic::createString).result(), dynamic
            )
        );
        tag = tag.set("color", tag.createString(ExtraDataFixUtils.dyeColorIdToName(tag.get("Color").asInt(0))));
        return tag.remove("Color");
    }
}
