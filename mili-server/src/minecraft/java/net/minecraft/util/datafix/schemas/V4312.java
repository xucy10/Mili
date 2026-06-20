package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.util.datafix.fixes.References;

public class V4312 extends NamespacedSchema {
    public V4312(int versionKey, Schema parent) {
        super(versionKey, parent);
    }

    @Override
    public void registerTypes(Schema outputSchema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
        super.registerTypes(outputSchema, entityTypes, blockEntityTypes);
        outputSchema.registerType(
            false,
            References.PLAYER,
            () -> DSL.and(
                References.ENTITY_EQUIPMENT.in(outputSchema),
                DSL.optionalFields(
                    Pair.of("RootVehicle", DSL.optionalFields("Entity", References.ENTITY_TREE.in(outputSchema))),
                    Pair.of("ender_pearls", DSL.list(References.ENTITY_TREE.in(outputSchema))),
                    Pair.of("Inventory", DSL.list(References.ITEM_STACK.in(outputSchema))),
                    Pair.of("EnderItems", DSL.list(References.ITEM_STACK.in(outputSchema))),
                    Pair.of("ShoulderEntityLeft", References.ENTITY_TREE.in(outputSchema)),
                    Pair.of("ShoulderEntityRight", References.ENTITY_TREE.in(outputSchema)),
                    Pair.of(
                        "recipeBook",
                        DSL.optionalFields(
                            "recipes", DSL.list(References.RECIPE.in(outputSchema)), "toBeDisplayed", DSL.list(References.RECIPE.in(outputSchema))
                        )
                    )
                )
            )
        );
    }
}
