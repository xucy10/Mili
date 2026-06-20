package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EntityRidingToPassengersFix extends DataFix {
    public EntityRidingToPassengersFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Schema inputSchema = this.getInputSchema();
        Schema outputSchema = this.getOutputSchema();
        Type<?> typeRaw = inputSchema.getTypeRaw(References.ENTITY_TREE);
        Type<?> typeRaw1 = outputSchema.getTypeRaw(References.ENTITY_TREE);
        Type<?> typeRaw2 = inputSchema.getTypeRaw(References.ENTITY);
        return this.cap(inputSchema, outputSchema, typeRaw, typeRaw1, typeRaw2);
    }

    private <OldEntityTree, NewEntityTree, Entity> TypeRewriteRule cap(
        Schema inputSchema, Schema outputSchema, Type<OldEntityTree> oldEntityTreeType, Type<NewEntityTree> newEntityTreeType, Type<Entity> entityType
    ) {
        Type<Pair<String, Pair<Either<OldEntityTree, Unit>, Entity>>> type = DSL.named(
            References.ENTITY_TREE.typeName(), DSL.and(DSL.optional(DSL.field("Riding", oldEntityTreeType)), entityType)
        );
        Type<Pair<String, Pair<Either<List<NewEntityTree>, Unit>, Entity>>> type1 = DSL.named(
            References.ENTITY_TREE.typeName(), DSL.and(DSL.optional(DSL.field("Passengers", DSL.list(newEntityTreeType))), entityType)
        );
        Type<?> type2 = inputSchema.getType(References.ENTITY_TREE);
        Type<?> type3 = outputSchema.getType(References.ENTITY_TREE);
        if (!Objects.equals(type2, type)) {
            throw new IllegalStateException("Old entity type is not what was expected.");
        } else if (!type3.equals(type1, true, true)) {
            throw new IllegalStateException("New entity type is not what was expected.");
        } else {
            OpticFinder<Pair<String, Pair<Either<OldEntityTree, Unit>, Entity>>> opticFinder = DSL.typeFinder(type);
            OpticFinder<Pair<String, Pair<Either<List<NewEntityTree>, Unit>, Entity>>> opticFinder1 = DSL.typeFinder(type1);
            OpticFinder<NewEntityTree> opticFinder2 = DSL.typeFinder(newEntityTreeType);
            Type<?> type4 = inputSchema.getType(References.PLAYER);
            Type<?> type5 = outputSchema.getType(References.PLAYER);
            return TypeRewriteRule.seq(
                this.fixTypeEverywhere(
                    "EntityRidingToPassengerFix",
                    type,
                    type1,
                    dynamicOps -> pair -> {
                        Optional<Pair<String, Pair<Either<List<NewEntityTree>, Unit>, Entity>>> optional = Optional.empty();
                        Pair<String, Pair<Either<OldEntityTree, Unit>, Entity>> pair1 = pair;

                        while (true) {
                            Either<List<NewEntityTree>, Unit> either = DataFixUtils.orElse(
                                optional.map(
                                    pair2 -> {
                                        Typed<NewEntityTree> typed = newEntityTreeType.pointTyped(dynamicOps)
                                            .orElseThrow(() -> new IllegalStateException("Could not create new entity tree"));
                                        NewEntityTree object = typed.set(opticFinder1, (Pair<String, Pair<Either<List<NewEntityTree>, Unit>, Entity>>)pair2)
                                            .getOptional(opticFinder2)
                                            .orElseThrow(() -> new IllegalStateException("Should always have an entity tree here"));
                                        return Either.left(ImmutableList.of(object));
                                    }
                                ),
                                Either.right(DSL.unit())
                            );
                            optional = Optional.of(Pair.of(References.ENTITY_TREE.typeName(), Pair.of(either, pair1.getSecond().getSecond())));
                            Optional<OldEntityTree> optional1 = pair1.getSecond().getFirst().left();
                            if (optional1.isEmpty()) {
                                return optional.orElseThrow(() -> new IllegalStateException("Should always have an entity tree here"));
                            }

                            pair1 = new Typed<>(oldEntityTreeType, dynamicOps, optional1.get())
                                .getOptional(opticFinder)
                                .orElseThrow(() -> new IllegalStateException("Should always have an entity here"));
                        }
                    }
                ),
                this.writeAndRead("player RootVehicle injecter", type4, type5)
            );
        }
    }
}
