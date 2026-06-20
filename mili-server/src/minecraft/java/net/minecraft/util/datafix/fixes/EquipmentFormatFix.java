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
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentFormatFix extends DataFix {
    public EquipmentFormatFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> typeRaw = this.getInputSchema().getTypeRaw(References.ITEM_STACK);
        Type<?> typeRaw1 = this.getOutputSchema().getTypeRaw(References.ITEM_STACK);
        OpticFinder<?> opticFinder = typeRaw.findField("id");
        return this.fix(typeRaw, typeRaw1, opticFinder);
    }

    private <ItemStackOld, ItemStackNew> TypeRewriteRule fix(Type<ItemStackOld> oldItemStackType, Type<ItemStackNew> newItemStackType, OpticFinder<?> optic) {
        Type<Pair<String, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<ItemStackOld, Unit>, Either<ItemStackOld, Unit>>>>>> type = DSL.named(
            References.ENTITY_EQUIPMENT.typeName(),
            DSL.and(
                DSL.optional(DSL.field("ArmorItems", DSL.list(oldItemStackType))),
                DSL.optional(DSL.field("HandItems", DSL.list(oldItemStackType))),
                DSL.optional(DSL.field("body_armor_item", oldItemStackType)),
                DSL.optional(DSL.field("saddle", oldItemStackType))
            )
        );
        Type<Pair<String, Either<Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Pair<Either<ItemStackNew, Unit>, Dynamic<?>>>>>>>>>, Unit>>> type1 = DSL.named(
            References.ENTITY_EQUIPMENT.typeName(),
            DSL.optional(
                DSL.field(
                    "equipment",
                    DSL.and(
                        DSL.optional(DSL.field("mainhand", newItemStackType)),
                        DSL.optional(DSL.field("offhand", newItemStackType)),
                        DSL.optional(DSL.field("feet", newItemStackType)),
                        DSL.and(
                            DSL.optional(DSL.field("legs", newItemStackType)),
                            DSL.optional(DSL.field("chest", newItemStackType)),
                            DSL.optional(DSL.field("head", newItemStackType)),
                            DSL.and(DSL.optional(DSL.field("body", newItemStackType)), DSL.optional(DSL.field("saddle", newItemStackType)), DSL.remainderType())
                        )
                    )
                )
            )
        );
        if (!type.equals(this.getInputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Input entity_equipment type does not match expected");
        } else if (!type1.equals(this.getOutputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Output entity_equipment type does not match expected");
        } else {
            return this.fixTypeEverywhere(
                "EquipmentFormatFix",
                type,
                type1,
                dynamicOps -> {
                    Predicate<ItemStackOld> predicate = object -> {
                        Typed<ItemStackOld> typed = new Typed<>(oldItemStackType, dynamicOps, object);
                        return typed.getOptional(optic).isEmpty();
                    };
                    return pair -> {
                        String string = pair.getFirst();
                        Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<List<ItemStackOld>, Unit>, Pair<Either<ItemStackOld, Unit>, Either<ItemStackOld, Unit>>>> pair1 = pair.getSecond();
                        List<ItemStackOld> list = pair1.getFirst().map(Function.identity(), unit -> List.of());
                        List<ItemStackOld> list1 = pair1.getSecond().getFirst().map(Function.identity(), unit -> List.of());
                        Either<ItemStackOld, Unit> either = pair1.getSecond().getSecond().getFirst();
                        Either<ItemStackOld, Unit> either1 = pair1.getSecond().getSecond().getSecond();
                        Either<ItemStackOld, Unit> itemFromList = getItemFromList(0, list, predicate);
                        Either<ItemStackOld, Unit> itemFromList1 = getItemFromList(1, list, predicate);
                        Either<ItemStackOld, Unit> itemFromList2 = getItemFromList(2, list, predicate);
                        Either<ItemStackOld, Unit> itemFromList3 = getItemFromList(3, list, predicate);
                        Either<ItemStackOld, Unit> itemFromList4 = getItemFromList(0, list1, predicate);
                        Either<ItemStackOld, Unit> itemFromList5 = getItemFromList(1, list1, predicate);
                        return areAllEmpty(either, either1, itemFromList, itemFromList1, itemFromList2, itemFromList3, itemFromList4, itemFromList5)
                            ? Pair.of(string, Either.right(Unit.INSTANCE))
                            : Pair.of(
                                string,
                                Either.left(
                                    Pair.of(
                                        (Either<ItemStackNew, Unit>)itemFromList4,
                                        Pair.of(
                                            (Either<ItemStackNew, Unit>)itemFromList5,
                                            Pair.of(
                                                (Either<ItemStackNew, Unit>)itemFromList,
                                                Pair.of(
                                                    (Either<ItemStackNew, Unit>)itemFromList1,
                                                    Pair.of(
                                                        (Either<ItemStackNew, Unit>)itemFromList2,
                                                        Pair.of(
                                                            (Either<ItemStackNew, Unit>)itemFromList3,
                                                            Pair.of(
                                                                (Either<ItemStackNew, Unit>)either,
                                                                Pair.of((Either<ItemStackNew, Unit>)either1, new Dynamic(dynamicOps))
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            );
                    };
                }
            );
        }
    }

    @SafeVarargs
    private static boolean areAllEmpty(Either<?, Unit>... items) {
        for (Either<?, Unit> either : items) {
            if (either.right().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static <ItemStack> Either<ItemStack, Unit> getItemFromList(int index, List<ItemStack> list, Predicate<ItemStack> predicate) {
        if (index >= list.size()) {
            return Either.right(Unit.INSTANCE);
        } else {
            ItemStack object = list.get(index);
            return predicate.test(object) ? Either.right(Unit.INSTANCE) : Either.left(object);
        }
    }
}
