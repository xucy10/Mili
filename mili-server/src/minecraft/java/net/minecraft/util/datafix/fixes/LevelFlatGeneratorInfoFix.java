package net.minecraft.util.datafix.fixes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.math.NumberUtils;

public class LevelFlatGeneratorInfoFix extends DataFix {
    private static final String GENERATOR_OPTIONS = "generatorOptions";
    @VisibleForTesting
    static final String DEFAULT = "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1;village";
    private static final Splitter SPLITTER = Splitter.on(';').limit(5);
    private static final Splitter LAYER_SPLITTER = Splitter.on(',');
    private static final Splitter OLD_AMOUNT_SPLITTER = Splitter.on('x').limit(2);
    private static final Splitter AMOUNT_SPLITTER = Splitter.on('*').limit(2);
    private static final Splitter BLOCK_SPLITTER = Splitter.on(':').limit(3);

    public LevelFlatGeneratorInfoFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "LevelFlatGeneratorInfoFix", this.getInputSchema().getType(References.LEVEL), typed -> typed.update(DSL.remainderFinder(), this::fix)
        );
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        return dynamic.get("generatorName").asString("").equalsIgnoreCase("flat")
            ? dynamic.update(
                "generatorOptions", dynamic1 -> DataFixUtils.orElse(dynamic1.asString().map(this::fixString).map(dynamic1::createString).result(), dynamic1)
            )
            : dynamic;
    }

    @VisibleForTesting
    String fixString(String string) {
        if (string.isEmpty()) {
            return "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1;village";
        } else {
            Iterator<String> iterator = SPLITTER.split(string).iterator();
            String string1 = iterator.next();
            int i;
            String string2;
            if (iterator.hasNext()) {
                i = NumberUtils.toInt(string1, 0);
                string2 = iterator.next();
            } else {
                i = 0;
                string2 = string1;
            }

            if (i >= 0 && i <= 3) {
                StringBuilder stringBuilder = new StringBuilder();
                Splitter splitter = i < 3 ? OLD_AMOUNT_SPLITTER : AMOUNT_SPLITTER;
                stringBuilder.append(StreamSupport.stream(LAYER_SPLITTER.split(string2).spliterator(), false).map(string3 -> {
                    List<String> parts = splitter.splitToList(string3);
                    int i1;
                    String string4;
                    if (parts.size() == 2) {
                        i1 = NumberUtils.toInt(parts.get(0));
                        string4 = parts.get(1);
                    } else {
                        i1 = 1;
                        string4 = parts.get(0);
                    }

                    List<String> parts1 = BLOCK_SPLITTER.splitToList(string4);
                    int i2 = parts1.get(0).equals("minecraft") ? 1 : 0;
                    String string5 = parts1.get(i2);
                    int i3 = i == 3 ? EntityBlockStateFix.getBlockId("minecraft:" + string5) : NumberUtils.toInt(string5, 0);
                    int i4 = i2 + 1;
                    int i5 = parts1.size() > i4 ? NumberUtils.toInt(parts1.get(i4), 0) : 0;
                    return (i1 == 1 ? "" : i1 + "*") + BlockStateData.getTag(i3 << 4 | i5).get("Name").asString("");
                }).collect(Collectors.joining(",")));

                while (iterator.hasNext()) {
                    stringBuilder.append(';').append(iterator.next());
                }

                return stringBuilder.toString();
            } else {
                return "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;1;village";
            }
        }
    }
}
