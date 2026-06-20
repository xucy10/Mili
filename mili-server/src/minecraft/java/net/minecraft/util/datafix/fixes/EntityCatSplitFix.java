package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

public class EntityCatSplitFix extends SimpleEntityRenameFix {
    public EntityCatSplitFix(Schema outputSchema, boolean changesType) {
        super("EntityCatSplitFix", outputSchema, changesType);
    }

    @Override
    protected Pair<String, Dynamic<?>> getNewNameAndTag(String name, Dynamic<?> tag) {
        if (Objects.equals("minecraft:ocelot", name)) {
            int _int = tag.get("CatType").asInt(0);
            if (_int == 0) {
                String string = tag.get("Owner").asString("");
                String string1 = tag.get("OwnerUUID").asString("");
                if (!string.isEmpty() || !string1.isEmpty()) {
                    tag.set("Trusting", tag.createBoolean(true));
                }
            } else if (_int > 0 && _int < 4) {
                tag = tag.set("CatType", tag.createInt(_int));
                tag = tag.set("OwnerUUID", tag.createString(tag.get("OwnerUUID").asString("")));
                return Pair.of("minecraft:cat", tag);
            }
        }

        return Pair.of(name, tag);
    }
}
