package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveEmptyItemInBrushableBlockFix extends NamedEntityWriteReadFix {
    public RemoveEmptyItemInBrushableBlockFix(Schema outputSchema) {
        super(outputSchema, false, "RemoveEmptyItemInSuspiciousBlockFix", References.BLOCK_ENTITY, "minecraft:brushable_block");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("item").result();
        return optional.isPresent() && isEmptyStack(optional.get()) ? tag.remove("item") : tag;
    }

    private static boolean isEmptyStack(Dynamic<?> tag) {
        String string = NamespacedSchema.ensureNamespaced(tag.get("id").asString("minecraft:air"));
        int _int = tag.get("count").asInt(0);
        return string.equals("minecraft:air") || _int == 0;
    }
}
