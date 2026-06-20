package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import java.util.stream.Stream;
import net.minecraft.util.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class WorldGenSettingsHeightAndBiomeFix extends DataFix {
    private static final String NAME = "WorldGenSettingsHeightAndBiomeFix";
    public static final String WAS_PREVIOUSLY_INCREASED_KEY = "has_increased_height_already";

    public WorldGenSettingsHeightAndBiomeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.WORLD_GEN_SETTINGS);
        OpticFinder<?> opticFinder = type.findField("dimensions");
        Type<?> type1 = this.getOutputSchema().getType(References.WORLD_GEN_SETTINGS);
        Type<?> type2 = type1.findFieldType("dimensions");
        return this.fixTypeEverywhereTyped(
            "WorldGenSettingsHeightAndBiomeFix",
            type,
            type1,
            typed -> {
                OptionalDynamic<?> optionalDynamic = typed.get(DSL.remainderFinder()).get("has_increased_height_already");
                boolean isEmpty = optionalDynamic.result().isEmpty();
                boolean _boolean = optionalDynamic.asBoolean(true);
                return typed.update(DSL.remainderFinder(), dynamic -> dynamic.remove("has_increased_height_already"))
                    .updateTyped(
                        opticFinder,
                        type2,
                        typed1 -> Util.writeAndReadTypedOrThrow(
                            typed1,
                            type2,
                            dynamic -> dynamic.update(
                                "minecraft:overworld",
                                dynamic1 -> dynamic1.update(
                                    "generator",
                                    dynamic2 -> {
                                        String string = dynamic2.get("type").asString("");
                                        if ("minecraft:noise".equals(string)) {
                                            MutableBoolean mutableBoolean = new MutableBoolean();
                                            dynamic2 = dynamic2.update(
                                                "biome_source",
                                                dynamic3 -> {
                                                    String string1 = dynamic3.get("type").asString("");
                                                    if ("minecraft:vanilla_layered".equals(string1) || isEmpty && "minecraft:multi_noise".equals(string1)) {
                                                        if (dynamic3.get("large_biomes").asBoolean(false)) {
                                                            mutableBoolean.setTrue();
                                                        }

                                                        return dynamic3.createMap(
                                                            ImmutableMap.of(
                                                                dynamic3.createString("preset"),
                                                                dynamic3.createString("minecraft:overworld"),
                                                                dynamic3.createString("type"),
                                                                dynamic3.createString("minecraft:multi_noise")
                                                            )
                                                        );
                                                    } else {
                                                        return dynamic3;
                                                    }
                                                }
                                            );
                                            return mutableBoolean.booleanValue()
                                                ? dynamic2.update(
                                                    "settings",
                                                    dynamic3 -> "minecraft:overworld".equals(dynamic3.asString(""))
                                                        ? dynamic3.createString("minecraft:large_biomes")
                                                        : dynamic3
                                                )
                                                : dynamic2;
                                        } else if ("minecraft:flat".equals(string)) {
                                            return _boolean
                                                ? dynamic2
                                                : dynamic2.update(
                                                    "settings", dynamic3 -> dynamic3.update("layers", WorldGenSettingsHeightAndBiomeFix::updateLayers)
                                                );
                                        } else {
                                            return dynamic2;
                                        }
                                    }
                                )
                            )
                        )
                    );
            }
        );
    }

    private static Dynamic<?> updateLayers(Dynamic<?> dynamic) {
        Dynamic<?> dynamic1 = dynamic.createMap(
            ImmutableMap.of(dynamic.createString("height"), dynamic.createInt(64), dynamic.createString("block"), dynamic.createString("minecraft:air"))
        );
        return dynamic.createList(Stream.concat(Stream.of(dynamic1), dynamic.asStream()));
    }
}
