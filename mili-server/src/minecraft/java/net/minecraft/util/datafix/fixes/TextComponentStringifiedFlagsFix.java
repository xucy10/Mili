package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class TextComponentStringifiedFlagsFix extends DataFix {
    public TextComponentStringifiedFlagsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, Either<?, Pair<?, Pair<?, Pair<?, Dynamic<?>>>>>>> type = (Type<Pair<String, Either<?, Pair<?, Pair<?, Pair<?, Dynamic<?>>>>>>>)this.getInputSchema()
            .getType(References.TEXT_COMPONENT);
        return this.fixTypeEverywhere(
            "TextComponentStringyFlagsFix",
            type,
            dynamicOps -> pair -> pair.mapSecond(
                either -> either.mapRight(
                    pair1 -> pair1.mapSecond(
                        pair2 -> pair2.mapSecond(
                            pair3 -> pair3.mapSecond(
                                dynamic -> dynamic.update("bold", TextComponentStringifiedFlagsFix::stringToBool)
                                    .update("italic", TextComponentStringifiedFlagsFix::stringToBool)
                                    .update("underlined", TextComponentStringifiedFlagsFix::stringToBool)
                                    .update("strikethrough", TextComponentStringifiedFlagsFix::stringToBool)
                                    .update("obfuscated", TextComponentStringifiedFlagsFix::stringToBool)
                            )
                        )
                    )
                )
            )
        );
    }

    private static <T> Dynamic<T> stringToBool(Dynamic<T> data) {
        Optional<String> optional = data.asString().result();
        return optional.isPresent() ? data.createBoolean(Boolean.parseBoolean(optional.get())) : data;
    }
}
