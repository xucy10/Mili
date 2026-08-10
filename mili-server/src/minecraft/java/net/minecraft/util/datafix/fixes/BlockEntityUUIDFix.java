package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityUUIDFix extends AbstractUUIDFix {
    public BlockEntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.BLOCK_ENTITY);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("BlockEntityUUIDFix", this.getInputSchema().getType(this.typeReference), typed -> {
            typed = this.updateNamedChoice(typed, "minecraft:conduit", this::updateConduit);
            return this.updateNamedChoice(typed, "minecraft:skull", this::updateSkull);
        });
    }

    private Dynamic<?> updateSkull(Dynamic<?> skullTag) {
        return skullTag.get("Owner")
            .get()
            .map(dynamic -> replaceUUIDString((Dynamic<?>)dynamic, "Id", "Id").orElse((Dynamic<?>)dynamic))
            .map(dynamic -> skullTag.remove("Owner").set("SkullOwner", (Dynamic<?>)dynamic))
            .result()
            .orElse((Dynamic) skullTag);
    }

    private Dynamic<?> updateConduit(Dynamic<?> conduitTag) {
        return replaceUUIDMLTag(conduitTag, "target_uuid", "Target").orElse(conduitTag);
    }
}
