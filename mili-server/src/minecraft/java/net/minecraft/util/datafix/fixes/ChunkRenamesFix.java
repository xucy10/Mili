package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.function.Function;

public class ChunkRenamesFix extends DataFix {
    public ChunkRenamesFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Structures");
        Type<?> type1 = this.getOutputSchema().getType(References.CHUNK);
        Type<?> type2 = type1.findFieldType("structures");
        return this.fixTypeEverywhereTyped("Chunk Renames; purge Level-tag", type, type1, typed -> {
            Typed<?> typed1 = typed.getTyped(opticFinder);
            Typed<?> typed2 = appendChunkName(typed1);
            typed2 = typed2.set(DSL.remainderFinder(), mergeRemainders(typed, typed1.get(DSL.remainderFinder())));
            typed2 = renameField(typed2, "TileEntities", "block_entities");
            typed2 = renameField(typed2, "TileTicks", "block_ticks");
            typed2 = renameField(typed2, "Entities", "entities");
            typed2 = renameField(typed2, "Sections", "sections");
            typed2 = typed2.updateTyped(opticFinder1, type2, typed3 -> renameField(typed3, "Starts", "starts"));
            typed2 = renameField(typed2, "Structures", "structures");
            return typed2.update(DSL.remainderFinder(), dynamic -> dynamic.remove("Level"));
        });
    }

    private static Typed<?> renameField(Typed<?> typed, String oldName, String newName) {
        return renameFieldHelper(typed, oldName, newName, typed.getType().findFieldType(oldName))
            .update(DSL.remainderFinder(), dynamic -> dynamic.remove(oldName));
    }

    private static <A> Typed<?> renameFieldHelper(Typed<?> typed, String oldName, String newName, Type<A> type) {
        Type<Either<A, Unit>> type1 = DSL.optional(DSL.field(oldName, type));
        Type<Either<A, Unit>> type2 = DSL.optional(DSL.field(newName, type));
        return typed.update(type1.finder(), type2, Function.identity());
    }

    private static <A> Typed<Pair<String, A>> appendChunkName(Typed<A> typed) {
        return new Typed<>(DSL.named("chunk", typed.getType()), typed.getOps(), Pair.of("chunk", typed.getValue()));
    }

    private static <T> Dynamic<T> mergeRemainders(Typed<?> typed, Dynamic<T> dynamic) {
        DynamicOps<T> ops = dynamic.getOps();
        Dynamic<T> dynamic1 = typed.get(DSL.remainderFinder()).convert(ops);
        DataResult<T> dataResult = ops.getMap(dynamic.getValue()).flatMap(mapLike -> ops.mergeToMap(dynamic1.getValue(), (MapLike<T>)mapLike));
        return dataResult.result().map(object -> new Dynamic<>(ops, (T)object)).orElse(dynamic);
    }
}
