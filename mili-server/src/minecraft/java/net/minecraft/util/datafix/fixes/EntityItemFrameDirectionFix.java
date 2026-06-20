package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityItemFrameDirectionFix extends NamedEntityFix {
    public EntityItemFrameDirectionFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityItemFrameDirectionFix", References.ENTITY, "minecraft:item_frame");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.set("Facing", tag.createByte(direction2dTo3d(tag.get("Facing").asByte((byte)0))));
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }

    private static byte direction2dTo3d(byte direction2d) {
        switch (direction2d) {
            case 0:
                return 3;
            case 1:
                return 4;
            case 2:
            default:
                return 2;
            case 3:
                return 5;
        }
    }
}
