package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;

public class PrimedTntBlockStateFixer extends NamedEntityWriteReadFix {
    public PrimedTntBlockStateFixer(Schema outputSchema) {
        super(outputSchema, true, "PrimedTnt BlockState fixer", References.ENTITY, "minecraft:tnt");
    }

    private static <T> Dynamic<T> renameFuse(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("Fuse").get().result();
        return optional.isPresent() ? tag.set("fuse", optional.get()) : tag;
    }

    private static <T> Dynamic<T> insertBlockState(Dynamic<T> tag) {
        return tag.set("block_state", tag.createMap(Map.of(tag.createString("Name"), tag.createString("minecraft:tnt"))));
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        return renameFuse(insertBlockState(tag));
    }
}
