package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class EntityEquipmentToArmorAndHandFix extends DataFix {
    public EntityEquipmentToArmorAndHandFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.cap(this.getInputSchema().getTypeRaw(References.ITEM_STACK), this.getOutputSchema().getTypeRaw(References.ITEM_STACK));
    }

    private <ItemStackOld, ItemStackNew> TypeRewriteRule cap(Type<ItemStackOld> oldItemStackType, Type<ItemStackNew> newItemStackType) {
        Type<Pair<String, Either<List<ItemStackOld>, Unit>>> type = DSL.named(
            References.ENTITY_EQUIPMENT.typeName(), DSL.optional(DSL.field("Equipment", DSL.list(oldItemStackType)))
        );
        Type<Pair<String, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<List<ItemStackNew>, Unit>, Pair<Either<ItemStackNew, Unit>, Either<ItemStackNew, Unit>>>>>> type1 = DSL.named(
            References.ENTITY_EQUIPMENT.typeName(),
            DSL.and(
                DSL.optional(DSL.field("ArmorItems", DSL.list(newItemStackType))),
                DSL.optional(DSL.field("HandItems", DSL.list(newItemStackType))),
                DSL.optional(DSL.field("body_armor_item", newItemStackType)),
                DSL.optional(DSL.field("saddle", newItemStackType))
            )
        );
        if (!type.equals(this.getInputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Input entity_equipment type does not match expected");
        } else if (!type1.equals(this.getOutputSchema().getType(References.ENTITY_EQUIPMENT))) {
            throw new IllegalStateException("Output entity_equipment type does not match expected");
        } else {
            return TypeRewriteRule.seq(
                this.fixTypeEverywhereTyped(
                    "EntityEquipmentToArmorAndHandFix - drop chances",
                    this.getInputSchema().getType(References.ENTITY),
                    typed -> typed.update(DSL.remainderFinder(), EntityEquipmentToArmorAndHandFix::fixDropChances)
                ),
                this.fixTypeEverywhere(
                    "EntityEquipmentToArmorAndHandFix - equipment",
                    type,
                    type1,
                    dynamicOps -> {
                        ItemStackNew first = newItemStackType.read(new Dynamic<>(dynamicOps).emptyMap())
                            .result()
                            .orElseThrow(() -> new IllegalStateException("Could not parse newly created empty itemstack."))
                            .getFirst();
                        Either<ItemStackNew, Unit> either = Either.right(DSL.unit());
                        return pair -> pair.mapSecond(either1 -> {
                            List<ItemStackOld> list = either1.map(Function.identity(), unit -> List.of());
                            Either<List<ItemStackNew>, Unit> either2 = Either.right(DSL.unit());
                            Either<List<ItemStackNew>, Unit> either3 = Either.right(DSL.unit());
                            if (!list.isEmpty()) {
                                either2 = Either.left(Lists.newArrayList((ItemStackNew[])(new Object[]{list.getFirst(), first})));
                            }

                            if (list.size() > 1) {
                                List<ItemStackNew> list1 = Lists.newArrayList(first, first, first, first);

                                for (int i = 1; i < Math.min(list.size(), 5); i++) {
                                    list1.set(i - 1, (ItemStackNew)list.get(i));
                                }

                                either3 = Either.left(list1);
                            }

                            return Pair.of(either3, Pair.of(either2, Pair.of(either, either)));
                        });
                    }
                )
            );
        }
    }

    private static Dynamic<?> fixDropChances(Dynamic<?> data) {
        Optional<? extends Stream<? extends Dynamic<?>>> optional = data.get("DropChances").asStreamOpt().result();
        data = data.remove("DropChances");
        if (optional.isPresent()) {
            Iterator<Float> iterator = Stream.concat(optional.get().map(dynamic -> dynamic.asFloat(0.0F)), Stream.generate(() -> 0.0F)).iterator();
            float f = iterator.next();
            if (data.get("HandDropChances").result().isEmpty()) {
                data = data.set("HandDropChances", data.createList(Stream.of(f, 0.0F).map(data::createFloat)));
            }

            if (data.get("ArmorDropChances").result().isEmpty()) {
                data = data.set(
                    "ArmorDropChances", data.createList(Stream.of(iterator.next(), iterator.next(), iterator.next(), iterator.next()).map(data::createFloat))
                );
            }
        }

        return data;
    }
}
