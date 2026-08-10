package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class PlayerHeadBlockProfileFix extends NamedEntityFix {
    public PlayerHeadBlockProfileFix(Schema outputSchema) {
        super(outputSchema, false, "PlayerHeadBlockProfileFix", References.BLOCK_ENTITY, "minecraft:skull");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("SkullOwner").result();
        Optional<Dynamic<T>> optional1 = tag.get("ExtraType").result();
        Optional<Dynamic<T>> optional2 = optional.or(() -> optional1);
        if (optional2.isEmpty()) {
            return tag;
        } else {
            tag = tag.remove("SkullOwner").remove("ExtraType");
            return tag.set("profile", ItemStackComponentizationFix.fixProfile(optional2.get()));
        }
    }
}
