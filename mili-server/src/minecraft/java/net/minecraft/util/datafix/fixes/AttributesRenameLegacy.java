package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.UnaryOperator;

public class AttributesRenameLegacy extends DataFix {
    private final String name;
    private final UnaryOperator<String> renames;

    public AttributesRenameLegacy(Schema outputSchema, String name, UnaryOperator<String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(this.name + " (ItemStack)", type, typed -> typed.updateTyped(opticFinder, this::fixItemStackTag)),
            this.fixTypeEverywhereTyped(this.name + " (Entity)", this.getInputSchema().getType(References.ENTITY), this::fixEntity),
            this.fixTypeEverywhereTyped(this.name + " (Player)", this.getInputSchema().getType(References.PLAYER), this::fixEntity)
        );
    }

    private Dynamic<?> fixName(Dynamic<?> name) {
        return DataFixUtils.orElse(name.asString().result().map(this.renames).map(name::createString), name);
    }

    private Typed<?> fixItemStackTag(Typed<?> itemStackTag) {
        return itemStackTag.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "AttributeModifiers",
                dynamic1 -> DataFixUtils.orElse(
                    dynamic1.asStreamOpt()
                        .result()
                        .map(stream -> stream.map(dynamic2 -> dynamic2.update("AttributeName", this::fixName)))
                        .map(dynamic1::createList),
                    dynamic1
                )
            )
        );
    }

    private Typed<?> fixEntity(Typed<?> entityTag) {
        return entityTag.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "Attributes",
                dynamic1 -> DataFixUtils.orElse(
                    dynamic1.asStreamOpt().result().map(stream -> stream.map(dynamic2 -> dynamic2.update("Name", this::fixName))).map(dynamic1::createList),
                    dynamic1
                )
            )
        );
    }
}
