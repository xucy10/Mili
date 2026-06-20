package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

public class EntityElderGuardianSplitFix extends SimpleEntityRenameFix {
    public EntityElderGuardianSplitFix(Schema outputSchema, boolean changesType) {
        super("EntityElderGuardianSplitFix", outputSchema, changesType);
    }

    @Override
    protected Pair<String, Dynamic<?>> getNewNameAndTag(String name, Dynamic<?> tag) {
        return Pair.of(Objects.equals(name, "Guardian") && tag.get("Elder").asBoolean(false) ? "ElderGuardian" : name, tag);
    }
}
