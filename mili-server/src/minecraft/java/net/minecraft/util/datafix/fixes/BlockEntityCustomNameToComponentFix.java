package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;
import net.minecraft.util.datafix.LegacyComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class BlockEntityCustomNameToComponentFix extends DataFix {
    private static final Set<String> NAMEABLE_BLOCK_ENTITIES = Set.of(
        "minecraft:beacon",
        "minecraft:banner",
        "minecraft:brewing_stand",
        "minecraft:chest",
        "minecraft:trapped_chest",
        "minecraft:dispenser",
        "minecraft:dropper",
        "minecraft:enchanting_table",
        "minecraft:furnace",
        "minecraft:hopper",
        "minecraft:shulker_box"
    );

    public BlockEntityCustomNameToComponentFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    public TypeRewriteRule makeRule() {
        OpticFinder<String> opticFinder = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type2 = ExtraDataFixUtils.patchSubType(type, type, type1);
        return this.fixTypeEverywhereTyped(
            "BlockEntityCustomNameToComponentFix",
            type,
            type1,
            typed -> {
                Optional<String> optional = typed.getOptional(opticFinder);
                return optional.isPresent() && !NAMEABLE_BLOCK_ENTITIES.contains(optional.get())
                    ? ExtraDataFixUtils.cast(type1, typed)
                    : Util.writeAndReadTypedOrThrow(ExtraDataFixUtils.cast(type2, typed), type1, BlockEntityCustomNameToComponentFix::fixTagCustomName);
            }
        );
    }

    public static <T> Dynamic<T> fixTagCustomName(Dynamic<T> tag) {
        String string = tag.get("CustomName").asString("");
        return string.isEmpty() ? tag.remove("CustomName") : tag.set("CustomName", LegacyComponentDataFixUtils.createPlainTextComponent(tag.getOps(), string));
    }
}
