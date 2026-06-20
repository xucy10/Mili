package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class BlockRenameFix extends DataFix {
    private final String name;

    public BlockRenameFix(Schema outputSchema, String name) {
        super(outputSchema, false);
        this.name = name;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_NAME);
        Type<Pair<String, String>> type1 = DSL.named(References.BLOCK_NAME.typeName(), NamespacedSchema.namespacedString());
        if (!Objects.equals(type, type1)) {
            throw new IllegalStateException("block type is not what was expected.");
        } else {
            TypeRewriteRule typeRewriteRule = this.fixTypeEverywhere(this.name + " for block", type1, dynamicOps -> pair -> pair.mapSecond(this::renameBlock));
            TypeRewriteRule typeRewriteRule1 = this.fixTypeEverywhereTyped(
                this.name + " for block_state",
                this.getInputSchema().getType(References.BLOCK_STATE),
                typed -> typed.update(DSL.remainderFinder(), this::fixBlockState)
            );
            TypeRewriteRule typeRewriteRule2 = this.fixTypeEverywhereTyped(
                this.name + " for flat_block_state",
                this.getInputSchema().getType(References.FLAT_BLOCK_STATE),
                typed -> typed.update(
                    DSL.remainderFinder(),
                    dynamic -> DataFixUtils.orElse(dynamic.asString().result().map(this::fixFlatBlockState).map(dynamic::createString), dynamic)
                )
            );
            return TypeRewriteRule.seq(typeRewriteRule, typeRewriteRule1, typeRewriteRule2);
        }
    }

    private Dynamic<?> fixBlockState(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.get("Name").asString().result();
        return optional.isPresent() ? dynamic.set("Name", dynamic.createString(this.renameBlock(optional.get()))) : dynamic;
    }

    private String fixFlatBlockState(String name) {
        int index = name.indexOf(91);
        int index1 = name.indexOf(123);
        int len = name.length();
        if (index > 0) {
            len = index;
        }

        if (index1 > 0) {
            len = Math.min(len, index1);
        }

        String sub = name.substring(0, len);
        String string = this.renameBlock(sub);
        return string + name.substring(len);
    }

    protected abstract String renameBlock(String name);

    public static DataFix create(Schema outputSchema, String name, final Function<String, String> renamer) {
        return new BlockRenameFix(outputSchema, name) {
            @Override
            protected String renameBlock(String name1) {
                return renamer.apply(name1);
            }
        };
    }
}
