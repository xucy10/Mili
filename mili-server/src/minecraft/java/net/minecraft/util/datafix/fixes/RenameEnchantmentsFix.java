package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RenameEnchantmentsFix extends DataFix {
    final String name;
    final Map<String, String> renames;

    public RenameEnchantmentsFix(Schema outputSchema, String name, Map<String, String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            this.name, type, typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), this::fixTag))
        );
    }

    private Dynamic<?> fixTag(Dynamic<?> tag) {
        tag = this.fixEnchantmentList(tag, "Enchantments");
        return this.fixEnchantmentList(tag, "StoredEnchantments");
    }

    private Dynamic<?> fixEnchantmentList(Dynamic<?> tag, String key) {
        return tag.update(
            key,
            dynamic -> dynamic.asStreamOpt()
                .map(
                    stream -> stream.map(
                        dynamic1 -> dynamic1.update(
                            "id",
                            dynamic2 -> dynamic2.asString()
                                .map(string -> dynamic1.createString(this.renames.getOrDefault(NamespacedSchema.ensureNamespaced(string), string)))
                                .mapOrElse(Function.identity(), error -> dynamic2)
                        )
                    )
                )
                .map(dynamic::createList)
                .mapOrElse(Function.identity(), error -> dynamic)
        );
    }
}
