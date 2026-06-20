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
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntitySpawnerItemVariantComponentFix extends DataFix {
    public EntitySpawnerItemVariantComponentFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("components");
        return this.fixTypeEverywhereTyped(
            "ItemStack bucket_entity_data variants to separate components",
            type,
            typed -> {
                String string = typed.getOptional(opticFinder).map(Pair::getSecond).orElse("");

                return switch (string) {
                    case "minecraft:salmon_bucket" -> typed.updateTyped(opticFinder1, (Fixer) EntitySpawnerItemVariantComponentFix::fixSalmonBucket);
                    case "minecraft:axolotl_bucket" -> typed.updateTyped(opticFinder1, (Fixer) EntitySpawnerItemVariantComponentFix::fixAxolotlBucket);
                    case "minecraft:tropical_fish_bucket" -> typed.updateTyped(opticFinder1, (Fixer) EntitySpawnerItemVariantComponentFix::fixTropicalFishBucket);
                    case "minecraft:painting" -> typed.updateTyped(
                        opticFinder1, typed1 -> Util.writeAndReadTypedOrThrow(typed1, typed1.getType(), EntitySpawnerItemVariantComponentFix::fixPainting)
                    );
                    default -> typed;
                };
            }
        );
    }

    private static String getBaseColor(int variant) {
        return ExtraDataFixUtils.dyeColorIdToName(variant >> 16 & 0xFF);
    }

    private static String getPatternColor(int variant) {
        return ExtraDataFixUtils.dyeColorIdToName(variant >> 24 & 0xFF);
    }

    private static String getPattern(int variant) {
        return switch (variant & 65535) {
            case 1 -> "flopper";
            case 256 -> "sunstreak";
            case 257 -> "stripey";
            case 512 -> "snooper";
            case 513 -> "glitter";
            case 768 -> "dasher";
            case 769 -> "blockfish";
            case 1024 -> "brinely";
            case 1025 -> "betty";
            case 1280 -> "spotty";
            case 1281 -> "clayfish";
            default -> "kob";
        };
    }

    private static <T> Dynamic<T> fixTropicalFishBucket(Dynamic<T> data, Dynamic<T> entityData) {
        Optional<Number> optional = entityData.get("BucketVariantTag").asNumber().result();
        if (optional.isEmpty()) {
            return data;
        } else {
            int i = optional.get().intValue();
            String pattern = getPattern(i);
            String baseColor = getBaseColor(i);
            String patternColor = getPatternColor(i);
            return data.update("minecraft:bucket_entity_data", dynamic -> dynamic.remove("BucketVariantTag"))
                .set("minecraft:tropical_fish/pattern", data.createString(pattern))
                .set("minecraft:tropical_fish/base_color", data.createString(baseColor))
                .set("minecraft:tropical_fish/pattern_color", data.createString(patternColor));
        }
    }

    private static <T> Dynamic<T> fixAxolotlBucket(Dynamic<T> data, Dynamic<T> entityData) {
        Optional<Number> optional = entityData.get("Variant").asNumber().result();
        if (optional.isEmpty()) {
            return data;
        } else {
            String string = switch (optional.get().intValue()) {
                case 1 -> "wild";
                case 2 -> "gold";
                case 3 -> "cyan";
                case 4 -> "blue";
                default -> "lucy";
            };
            return data.update("minecraft:bucket_entity_data", dynamic -> dynamic.remove("Variant"))
                .set("minecraft:axolotl/variant", data.createString(string));
        }
    }

    private static <T> Dynamic<T> fixSalmonBucket(Dynamic<T> data, Dynamic<T> entityData) {
        Optional<Dynamic<T>> optional = entityData.get("type").result();
        return optional.isEmpty()
            ? data
            : data.update("minecraft:bucket_entity_data", dynamic -> dynamic.remove("type")).set("minecraft:salmon/size", optional.get());
    }

    private static <T> Dynamic<T> fixPainting(Dynamic<T> data) {
        Optional<Dynamic<T>> optional = data.get("minecraft:entity_data").result();
        if (optional.isEmpty()) {
            return data;
        } else if (optional.get().get("id").asString().result().filter(string -> string.equals("minecraft:painting")).isEmpty()) {
            return data;
        } else {
            Optional<Dynamic<T>> optional1 = optional.get().get("variant").result();
            Dynamic<T> dynamic = optional.get().remove("variant");
            if (dynamic.remove("id").equals(dynamic.emptyMap())) {
                data = data.remove("minecraft:entity_data");
            } else {
                data = data.set("minecraft:entity_data", dynamic);
            }

            if (optional1.isPresent()) {
                data = data.set("minecraft:painting/variant", optional1.get());
            }

            return data;
        }
    }

    @FunctionalInterface
    interface Fixer extends Function<Typed<?>, Typed<?>> {
        @Override
        default Typed<?> apply(Typed<?> data) {
            return data.update(DSL.remainderFinder(), this::fixRemainder);
        }

        default <T> Dynamic<T> fixRemainder(Dynamic<T> data) {
            return data.get("minecraft:bucket_entity_data").result().map(dynamic -> this.fixRemainder(data, (Dynamic<T>)dynamic)).orElse(data);
        }

        <T> Dynamic<T> fixRemainder(Dynamic<T> data, Dynamic<T> entityData);
    }
}
