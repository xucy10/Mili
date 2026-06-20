package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
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
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;

public class FurnaceRecipeFix extends DataFix {
    public FurnaceRecipeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.cap(this.getOutputSchema().getTypeRaw(References.RECIPE));
    }

    private <R> TypeRewriteRule cap(Type<R> type) {
        Type<Pair<Either<Pair<List<Pair<R, Integer>>, Dynamic<?>>, Unit>, Dynamic<?>>> type1 = DSL.and(
            DSL.optional(DSL.field("RecipesUsed", DSL.and(DSL.compoundList(type, DSL.intType()), DSL.remainderType()))), DSL.remainderType()
        );
        OpticFinder<?> opticFinder = DSL.namedChoice("minecraft:furnace", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:furnace"));
        OpticFinder<?> opticFinder1 = DSL.namedChoice(
            "minecraft:blast_furnace", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:blast_furnace")
        );
        OpticFinder<?> opticFinder2 = DSL.namedChoice("minecraft:smoker", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:smoker"));
        Type<?> choiceType = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:furnace");
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:blast_furnace");
        Type<?> choiceType2 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:smoker");
        Type<?> type2 = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type3 = this.getOutputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "FurnaceRecipesFix",
            type2,
            type3,
            typed -> typed.updateTyped(opticFinder, choiceType, typed1 -> this.updateFurnaceContents(type, type1, typed1))
                .updateTyped(opticFinder1, choiceType1, typed1 -> this.updateFurnaceContents(type, type1, typed1))
                .updateTyped(opticFinder2, choiceType2, typed1 -> this.updateFurnaceContents(type, type1, typed1))
        );
    }

    private <R> Typed<?> updateFurnaceContents(
        Type<R> type, Type<Pair<Either<Pair<List<Pair<R, Integer>>, Dynamic<?>>, Unit>, Dynamic<?>>> recipesUsed, Typed<?> data
    ) {
        Dynamic<?> dynamic = data.getOrCreate(DSL.remainderFinder());
        int _int = dynamic.get("RecipesUsedSize").asInt(0);
        dynamic = dynamic.remove("RecipesUsedSize");
        List<Pair<R, Integer>> list = Lists.newArrayList();

        for (int i = 0; i < _int; i++) {
            String string = "RecipeLocation" + i;
            String string1 = "RecipeAmount" + i;
            Optional<? extends Dynamic<?>> optional = dynamic.get(string).result();
            int _int1 = dynamic.get(string1).asInt(0);
            if (_int1 > 0) {
                optional.ifPresent(dynamic1 -> {
                    Optional<? extends Pair<R, ? extends Dynamic<?>>> optional1 = type.read((Dynamic<?>)dynamic1).result();
                    optional1.ifPresent(pair -> list.add(Pair.of(pair.getFirst(), _int1)));
                });
            }

            dynamic = dynamic.remove(string).remove(string1);
        }

        return data.set(DSL.remainderFinder(), recipesUsed, Pair.of(Either.left(Pair.of(list, dynamic.emptyMap())), dynamic));
    }
}
