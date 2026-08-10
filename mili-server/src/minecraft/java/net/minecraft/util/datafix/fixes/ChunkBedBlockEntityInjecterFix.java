package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.types.templates.TaggedChoice;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ChunkBedBlockEntityInjecterFix extends DataFix {
    public ChunkBedBlockEntityInjecterFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        if (!(type1.findFieldType("TileEntities") instanceof ListType<?> listType)) {
            throw new IllegalStateException("Tile entity type is not a list type.");
        } else {
            return this.cap(type1, listType);
        }
    }

    private <TE> TypeRewriteRule cap(Type<?> levelType, ListType<TE> tileEntityTypes) {
        Type<TE> element = tileEntityTypes.getElement();
        OpticFinder<?> opticFinder = DSL.fieldFinder("Level", levelType);
        OpticFinder<List<TE>> opticFinder1 = DSL.fieldFinder("TileEntities", tileEntityTypes);
        int i = 416;
        return TypeRewriteRule.seq(
            this.fixTypeEverywhere(
                "InjectBedBlockEntityType",
                (TaggedChoice.TaggedChoiceType<String>) this.getInputSchema().findChoiceType(References.BLOCK_ENTITY),
                (TaggedChoice.TaggedChoiceType<String>) this.getOutputSchema().findChoiceType(References.BLOCK_ENTITY),
                dynamicOps -> pair -> pair
            ),
            this.fixTypeEverywhereTyped(
                "BedBlockEntityInjecter",
                this.getOutputSchema().getType(References.CHUNK),
                typed -> {
                    Typed<?> typed1 = typed.getTyped(opticFinder);
                    Dynamic<?> dynamic = typed1.get(DSL.remainderFinder());
                    int _int = dynamic.get("xPos").asInt(0);
                    int _int1 = dynamic.get("zPos").asInt(0);
                    List<TE> list = Lists.newArrayList(typed1.getOrCreate(opticFinder1));

                    for (Dynamic<?> dynamic1 : dynamic.get("Sections").asList(Function.identity())) {
                        int _int2 = dynamic1.get("Y").asInt(0);
                        Streams.mapWithIndex(dynamic1.get("Blocks").asIntStream(), (i1, l) -> {
                                if (416 == (i1 & 0xFF) << 4) {
                                    int i2 = (int)l;
                                    int i3 = i2 & 15;
                                    int i4 = i2 >> 8 & 15;
                                    int i5 = i2 >> 4 & 15;
                                    Map<Dynamic<?>, Dynamic<?>> map = Maps.newHashMap();
                                    map.put(dynamic1.createString("id"), dynamic1.createString("minecraft:bed"));
                                    map.put(dynamic1.createString("x"), dynamic1.createInt(i3 + (_int << 4)));
                                    map.put(dynamic1.createString("y"), dynamic1.createInt(i4 + (_int2 << 4)));
                                    map.put(dynamic1.createString("z"), dynamic1.createInt(i5 + (_int1 << 4)));
                                    map.put(dynamic1.createString("color"), dynamic1.createShort((short)14));
                                    return map;
                                } else {
                                    return null;
                                }
                            })
                            .forEachOrdered(
                                map -> {
                                    if (map != null) {
                                        list.add(
                                            element.read(dynamic1.createMap((Map<? extends Dynamic<?>, ? extends Dynamic<?>>)map))
                                                .result()
                                                .orElseThrow(() -> new IllegalStateException("Could not parse newly created bed block entity."))
                                                .getFirst()
                                        );
                                    }
                                }
                            );
                    }

                    return !list.isEmpty() ? typed.set(opticFinder, typed1.set(opticFinder1, list)) : typed;
                }
            )
        );
    }
}
