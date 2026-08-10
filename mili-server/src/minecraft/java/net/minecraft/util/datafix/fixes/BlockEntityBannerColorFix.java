package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityBannerColorFix extends NamedEntityFix {
    public BlockEntityBannerColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBannerColorFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        tag = tag.update("Base", dynamic -> dynamic.createInt(15 - dynamic.asInt(0)));
        return tag.update(
            "Patterns",
            dynamic -> DataFixUtils.orElse(
                dynamic.asStreamOpt()
                    .map(stream -> stream.map(dynamic1 -> dynamic1.update("Color", dynamic2 -> dynamic2.createInt(15 - dynamic2.asInt(0)))))
                    .map(dynamic::createList)
                    .result(),
                dynamic
            )
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
