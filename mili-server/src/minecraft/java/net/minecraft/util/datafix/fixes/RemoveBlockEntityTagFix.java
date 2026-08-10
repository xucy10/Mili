package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveBlockEntityTagFix extends DataFix {
    private final Set<String> blockEntityIdsToDrop;

    public RemoveBlockEntityTagFix(Schema outputSchema, Set<String> blockEntityIdsToDrop) {
        super(outputSchema, true);
        this.blockEntityIdsToDrop = blockEntityIdsToDrop;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("BlockEntityTag");
        Type<?> type1 = this.getInputSchema().getType(References.ENTITY);
        OpticFinder<?> opticFinder2 = DSL.namedChoice(
            "minecraft:falling_block", this.getInputSchema().getChoiceType(References.ENTITY, "minecraft:falling_block")
        );
        OpticFinder<?> opticFinder3 = opticFinder2.type().findField("TileEntityData");
        Type<?> type2 = this.getInputSchema().getType(References.STRUCTURE);
        OpticFinder<?> opticFinder4 = type2.findField("blocks");
        OpticFinder<?> opticFinder5 = DSL.typeFinder(((ListType)opticFinder4.type()).getElement());
        OpticFinder<?> opticFinder6 = opticFinder5.type().findField("nbt");
        OpticFinder<String> opticFinder7 = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(
                "ItemRemoveBlockEntityTagFix",
                type,
                typed -> typed.updateTyped(opticFinder, typed1 -> this.removeBlockEntity(typed1, opticFinder1, opticFinder7, "BlockEntityTag"))
            ),
            this.fixTypeEverywhereTyped(
                "FallingBlockEntityRemoveBlockEntityTagFix",
                type1,
                typed -> typed.updateTyped(opticFinder2, typed1 -> this.removeBlockEntity(typed1, opticFinder3, opticFinder7, "TileEntityData"))
            ),
            this.fixTypeEverywhereTyped(
                "StructureRemoveBlockEntityTagFix",
                type2,
                typed -> typed.updateTyped(
                    opticFinder4, typed1 -> typed1.updateTyped(opticFinder5, typed2 -> this.removeBlockEntity(typed2, opticFinder6, opticFinder7, "nbt"))
                )
            ),
            this.convertUnchecked(
                "ItemRemoveBlockEntityTagFix - update block entity type",
                this.getInputSchema().getType(References.BLOCK_ENTITY),
                this.getOutputSchema().getType(References.BLOCK_ENTITY)
            )
        );
    }

    private Typed<?> removeBlockEntity(Typed<?> data, OpticFinder<?> tagFinder, OpticFinder<String> idFinder, String key) {
        Optional<? extends Typed<?>> optionalTyped = data.getOptionalTyped(tagFinder);
        if (optionalTyped.isEmpty()) {
            return data;
        } else {
            String string = optionalTyped.get().getOptional(idFinder).orElse("");
            return !this.blockEntityIdsToDrop.contains(string) ? data : Util.writeAndReadTypedOrThrow(data, data.getType(), dynamic -> dynamic.remove(key));
        }
    }
}
