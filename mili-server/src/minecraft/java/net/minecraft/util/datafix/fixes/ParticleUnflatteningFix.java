package net.minecraft.util.datafix.fixes;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ParticleUnflatteningFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ParticleUnflatteningFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.PARTICLE);
        Type<?> type1 = this.getOutputSchema().getType(References.PARTICLE);
        return this.writeFixAndRead("ParticleUnflatteningFix", type, type1, this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<String> optional = tag.asString().result();
        if (optional.isEmpty()) {
            return tag;
        } else {
            String string = optional.get();
            String[] parts = string.split(" ", 2);
            String string1 = NamespacedSchema.ensureNamespaced(parts[0]);
            Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("type"), tag.createString(string1)));

            return switch (string1) {
                case "minecraft:item" -> parts.length > 1 ? this.updateItem(dynamic, parts[1]) : dynamic;
                case "minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar" -> parts.length > 1
                    ? this.updateBlock(dynamic, parts[1])
                    : dynamic;
                case "minecraft:dust" -> parts.length > 1 ? this.updateDust(dynamic, parts[1]) : dynamic;
                case "minecraft:dust_color_transition" -> parts.length > 1 ? this.updateDustTransition(dynamic, parts[1]) : dynamic;
                case "minecraft:sculk_charge" -> parts.length > 1 ? this.updateSculkCharge(dynamic, parts[1]) : dynamic;
                case "minecraft:vibration" -> parts.length > 1 ? this.updateVibration(dynamic, parts[1]) : dynamic;
                case "minecraft:shriek" -> parts.length > 1 ? this.updateShriek(dynamic, parts[1]) : dynamic;
                default -> dynamic;
            };
        }
    }

    private <T> Dynamic<T> updateItem(Dynamic<T> tag, String item) {
        int index = item.indexOf("{");
        Dynamic<T> dynamic = tag.createMap(Map.of(tag.createString("Count"), tag.createInt(1)));
        if (index == -1) {
            dynamic = dynamic.set("id", tag.createString(item));
        } else {
            dynamic = dynamic.set("id", tag.createString(item.substring(0, index)));
            Dynamic<T> dynamic1 = parseTag(tag.getOps(), item.substring(index));
            if (dynamic1 != null) {
                dynamic = dynamic.set("tag", dynamic1);
            }
        }

        return tag.set("item", dynamic);
    }

    private static <T> @Nullable Dynamic<T> parseTag(DynamicOps<T> ops, String tag) {
        try {
            return new Dynamic<>(ops, TagParser.create(ops).parseFully(tag));
        } catch (Exception var3) {
            LOGGER.warn("Failed to parse tag: {}", tag, var3);
            return null;
        }
    }

    private <T> Dynamic<T> updateBlock(Dynamic<T> tag, String block) {
        int index = block.indexOf("[");
        Dynamic<T> dynamic = tag.emptyMap();
        if (index == -1) {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block)));
        } else {
            dynamic = dynamic.set("Name", tag.createString(NamespacedSchema.ensureNamespaced(block.substring(0, index))));
            Map<Dynamic<T>, Dynamic<T>> map = parseBlockProperties(tag, block.substring(index));
            if (!map.isEmpty()) {
                dynamic = dynamic.set("Properties", tag.createMap(map));
            }
        }

        return tag.set("block_state", dynamic);
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> parseBlockProperties(Dynamic<T> tag, String properties) {
        try {
            Map<Dynamic<T>, Dynamic<T>> map = new HashMap<>();
            StringReader stringReader = new StringReader(properties);
            stringReader.expect('[');
            stringReader.skipWhitespace();

            while (stringReader.canRead() && stringReader.peek() != ']') {
                stringReader.skipWhitespace();
                String string = stringReader.readString();
                stringReader.skipWhitespace();
                stringReader.expect('=');
                stringReader.skipWhitespace();
                String string1 = stringReader.readString();
                stringReader.skipWhitespace();
                map.put(tag.createString(string), tag.createString(string1));
                if (stringReader.canRead()) {
                    if (stringReader.peek() != ',') {
                        break;
                    }

                    stringReader.skip();
                }
            }

            stringReader.expect(']');
            return map;
        } catch (Exception var6) {
            LOGGER.warn("Failed to parse block properties: {}", properties, var6);
            return Map.of();
        }
    }

    private static <T> Dynamic<T> readVector(Dynamic<T> tag, StringReader reader) throws CommandSyntaxException {
        float _float = reader.readFloat();
        reader.expect(' ');
        float _float1 = reader.readFloat();
        reader.expect(' ');
        float _float2 = reader.readFloat();
        return tag.createList(Stream.of(_float, _float1, _float2).map(tag::createFloat));
    }

    private <T> Dynamic<T> updateDust(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            Dynamic<T> vector = readVector(tag, stringReader);
            stringReader.expect(' ');
            float _float = stringReader.readFloat();
            return tag.set("color", vector).set("scale", tag.createFloat(_float));
        } catch (Exception var6) {
            LOGGER.warn("Failed to parse particle options: {}", options, var6);
            return tag;
        }
    }

    private <T> Dynamic<T> updateDustTransition(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            Dynamic<T> vector = readVector(tag, stringReader);
            stringReader.expect(' ');
            float _float = stringReader.readFloat();
            stringReader.expect(' ');
            Dynamic<T> vector1 = readVector(tag, stringReader);
            return tag.set("from_color", vector).set("to_color", vector1).set("scale", tag.createFloat(_float));
        } catch (Exception var7) {
            LOGGER.warn("Failed to parse particle options: {}", options, var7);
            return tag;
        }
    }

    private <T> Dynamic<T> updateSculkCharge(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            float _float = stringReader.readFloat();
            return tag.set("roll", tag.createFloat(_float));
        } catch (Exception var5) {
            LOGGER.warn("Failed to parse particle options: {}", options, var5);
            return tag;
        }
    }

    private <T> Dynamic<T> updateVibration(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            float f = (float)stringReader.readDouble();
            stringReader.expect(' ');
            float f1 = (float)stringReader.readDouble();
            stringReader.expect(' ');
            float f2 = (float)stringReader.readDouble();
            stringReader.expect(' ');
            int _int = stringReader.readInt();
            Dynamic<T> dynamic = (Dynamic<T>)tag.createIntList(IntStream.of(Mth.floor(f), Mth.floor(f1), Mth.floor(f2)));
            Dynamic<T> dynamic1 = tag.createMap(Map.of(tag.createString("type"), tag.createString("minecraft:block"), tag.createString("pos"), dynamic));
            return tag.set("destination", dynamic1).set("arrival_in_ticks", tag.createInt(_int));
        } catch (Exception var10) {
            LOGGER.warn("Failed to parse particle options: {}", options, var10);
            return tag;
        }
    }

    private <T> Dynamic<T> updateShriek(Dynamic<T> tag, String options) {
        try {
            StringReader stringReader = new StringReader(options);
            int _int = stringReader.readInt();
            return tag.set("delay", tag.createInt(_int));
        } catch (Exception var5) {
            LOGGER.warn("Failed to parse particle options: {}", options, var5);
            return tag;
        }
    }
}
