package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TrappedChestBlockEntityFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SIZE = 4096;
    private static final short SIZE_BITS = 12;

    public TrappedChestBlockEntityFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        if (!(type1.findFieldType("TileEntities") instanceof ListType<?> listType)) {
            throw new IllegalStateException("Tile entity type is not a list type.");
        } else {
            OpticFinder<? extends List<?>> opticFinder = DSL.fieldFinder("TileEntities", (Type<? extends List<?>>)listType);
            Type<?> type3 = this.getInputSchema().getType(References.CHUNK);
            OpticFinder<?> opticFinder1 = type3.findField("Level");
            OpticFinder<?> opticFinder2 = opticFinder1.type().findField("Sections");
            Type<?> type4 = opticFinder2.type();
            if (!(type4 instanceof ListType)) {
                throw new IllegalStateException("Expecting sections to be a list.");
            } else {
                Type<?> element = ((ListType)type4).getElement();
                OpticFinder<?> opticFinder3 = DSL.typeFinder(element);
                return TypeRewriteRule.seq(
                    new AddNewChoices(this.getOutputSchema(), "AddTrappedChestFix", References.BLOCK_ENTITY).makeRule(),
                    this.fixTypeEverywhereTyped(
                        "Trapped Chest fix",
                        type3,
                        typed -> typed.updateTyped(
                            opticFinder1,
                            typed1 -> {
                                Optional<? extends Typed<?>> optionalTyped = typed1.getOptionalTyped(opticFinder2);
                                if (optionalTyped.isEmpty()) {
                                    return typed1;
                                } else {
                                    List<? extends Typed<?>> allTyped = optionalTyped.get().getAllTyped(opticFinder3);
                                    IntSet set = new IntOpenHashSet();

                                    for (Typed<?> typed2 : allTyped) {
                                        TrappedChestBlockEntityFix.TrappedChestSection trappedChestSection = new TrappedChestBlockEntityFix.TrappedChestSection(
                                            typed2, this.getInputSchema()
                                        );
                                        if (!trappedChestSection.isSkippable()) {
                                            for (int i = 0; i < 4096; i++) {
                                                int block = trappedChestSection.getBlock(i);
                                                if (trappedChestSection.isTrappedChest(block)) {
                                                    set.add(trappedChestSection.getIndex() << 12 | i);
                                                }
                                            }
                                        }
                                    }

                                    Dynamic<?> dynamic = typed1.get(DSL.remainderFinder());
                                    int _int = dynamic.get("xPos").asInt(0);
                                    int _int1 = dynamic.get("zPos").asInt(0);
                                    TaggedChoiceType<String> taggedChoiceType = (TaggedChoiceType<String>)this.getInputSchema()
                                        .findChoiceType(References.BLOCK_ENTITY);
                                    return typed1.updateTyped(
                                        opticFinder,
                                        typed3 -> typed3.updateTyped(
                                            taggedChoiceType.finder(),
                                            typed4 -> {
                                                Dynamic<?> dynamic1 = typed4.getOrCreate(DSL.remainderFinder());
                                                int i1 = dynamic1.get("x").asInt(0) - (_int << 4);
                                                int _int2 = dynamic1.get("y").asInt(0);
                                                int i2 = dynamic1.get("z").asInt(0) - (_int1 << 4);
                                                return set.contains(LeavesFix.getIndex(i1, _int2, i2))
                                                    ? typed4.update(taggedChoiceType.finder(), pair -> pair.mapFirst(string -> {
                                                        if (!Objects.equals(string, "minecraft:chest")) {
                                                            LOGGER.warn("Block Entity was expected to be a chest");
                                                        }

                                                        return "minecraft:trapped_chest";
                                                    }))
                                                    : typed4;
                                            }
                                        )
                                    );
                                }
                            }
                        )
                    )
                );
            }
        }
    }

    public static final class TrappedChestSection extends LeavesFix.Section {
        private @Nullable IntSet chestIds;

        public TrappedChestSection(Typed<?> data, Schema schema) {
            super(data, schema);
        }

        @Override
        protected boolean skippable() {
            this.chestIds = new IntOpenHashSet();

            for (int i = 0; i < this.palette.size(); i++) {
                Dynamic<?> dynamic = this.palette.get(i);
                String string = dynamic.get("Name").asString("");
                if (Objects.equals(string, "minecraft:trapped_chest")) {
                    this.chestIds.add(i);
                }
            }

            return this.chestIds.isEmpty();
        }

        public boolean isTrappedChest(int id) {
            return this.chestIds.contains(id);
        }
    }
}
