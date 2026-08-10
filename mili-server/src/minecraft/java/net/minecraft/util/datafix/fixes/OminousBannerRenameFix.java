package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.Util;

public class OminousBannerRenameFix extends ItemStackTagFix {
    public OminousBannerRenameFix(Schema schema) {
        super(schema, "OminousBannerRenameFix", string -> string.equals("minecraft:white_banner"));
    }

    private <T> Dynamic<T> fixItemStackTag(Dynamic<T> data) {
        return data.update(
            "display",
            dynamic -> dynamic.update(
                "Name",
                dynamic1 -> {
                    Optional<String> optional = dynamic1.asString().result();
                    return optional.isPresent()
                        ? dynamic1.createString(
                            optional.get().replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\"")
                        )
                        : dynamic1;
                }
            )
        );
    }

    @Override
    protected Typed<?> fixItemStackTag(Typed<?> data) {
        return Util.writeAndReadTypedOrThrow(data, data.getType(), this::fixItemStackTag);
    }
}
