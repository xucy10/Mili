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
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemShulkerBoxColorFix extends DataFix {
    public static final String[] NAMES_BY_COLOR = new String[]{
        "minecraft:white_shulker_box",
        "minecraft:orange_shulker_box",
        "minecraft:magenta_shulker_box",
        "minecraft:light_blue_shulker_box",
        "minecraft:yellow_shulker_box",
        "minecraft:lime_shulker_box",
        "minecraft:pink_shulker_box",
        "minecraft:gray_shulker_box",
        "minecraft:silver_shulker_box",
        "minecraft:cyan_shulker_box",
        "minecraft:purple_shulker_box",
        "minecraft:blue_shulker_box",
        "minecraft:brown_shulker_box",
        "minecraft:green_shulker_box",
        "minecraft:red_shulker_box",
        "minecraft:black_shulker_box"
    };

    public ItemShulkerBoxColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder1 = type.findField("tag");
        OpticFinder<?> opticFinder2 = opticFinder1.type().findField("BlockEntityTag");
        return this.fixTypeEverywhereTyped(
            "ItemShulkerBoxColorFix",
            type,
            typed -> {
                Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
                if (optional.isPresent() && Objects.equals(optional.get().getSecond(), "minecraft:shulker_box")) {
                    Optional<? extends Typed<?>> optionalTyped = typed.getOptionalTyped(opticFinder1);
                    if (optionalTyped.isPresent()) {
                        Typed<?> typed1 = (Typed<?>)optionalTyped.get();
                        Optional<? extends Typed<?>> optionalTyped1 = typed1.getOptionalTyped(opticFinder2);
                        if (optionalTyped1.isPresent()) {
                            Typed<?> typed2 = (Typed<?>)optionalTyped1.get();
                            Dynamic<?> dynamic = typed2.get(DSL.remainderFinder());
                            int _int = dynamic.get("Color").asInt(0);
                            dynamic.remove("Color");
                            return typed.set(opticFinder1, typed1.set(opticFinder2, typed2.set(DSL.remainderFinder(), dynamic)))
                                .set(opticFinder, Pair.of(References.ITEM_NAME.typeName(), NAMES_BY_COLOR[_int % 16]));
                        }
                    }
                }

                return typed;
            }
        );
    }
}
