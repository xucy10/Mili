package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

public class ChunkProtoTickListFix extends DataFix {
    private static final int SECTION_WIDTH = 16;
    private static final ImmutableSet<String> ALWAYS_WATERLOGGED = ImmutableSet.of(
        "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass"
    );

    public ChunkProtoTickListFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Sections");
        OpticFinder<?> opticFinder2 = ((ListType)opticFinder1.type()).getElement().finder();
        OpticFinder<?> opticFinder3 = opticFinder2.type().findField("block_states");
        OpticFinder<?> opticFinder4 = opticFinder2.type().findField("biomes");
        OpticFinder<?> opticFinder5 = opticFinder3.type().findField("palette");
        OpticFinder<?> opticFinder6 = opticFinder.type().findField("TileTicks");
        return this.fixTypeEverywhereTyped(
            "ChunkProtoTickListFix",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> {
                    typed1 = typed1.update(
                        DSL.remainderFinder(),
                        dynamic3 -> DataFixUtils.orElse(
                            dynamic3.get("LiquidTicks").result().map(dynamic4 -> dynamic3.set("fluid_ticks", (Dynamic<?>)dynamic4).remove("LiquidTicks")),
                            dynamic3
                        )
                    );
                    Dynamic<?> dynamic = typed1.get(DSL.remainderFinder());
                    MutableInt mutableInt = new MutableInt();
                    Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> map = new Int2ObjectArrayMap<>();
                    typed1.getOptionalTyped(opticFinder1)
                        .ifPresent(
                            typed2 -> typed2.getAllTyped(opticFinder2)
                                .forEach(
                                    typed3 -> {
                                        Dynamic<?> dynamic3 = typed3.get(DSL.remainderFinder());
                                        int _int2 = dynamic3.get("Y").asInt(Integer.MAX_VALUE);
                                        if (_int2 != Integer.MAX_VALUE) {
                                            if (typed3.getOptionalTyped(opticFinder4).isPresent()) {
                                                mutableInt.setValue(Math.min(_int2, mutableInt.intValue()));
                                            }

                                            typed3.getOptionalTyped(opticFinder3)
                                                .ifPresent(
                                                    typed4 -> map.put(
                                                        _int2,
                                                        Suppliers.memoize(
                                                            () -> {
                                                                List<? extends Dynamic<?>> list = typed4.getOptionalTyped(opticFinder5)
                                                                    .map(
                                                                        typed5 -> typed5.write()
                                                                            .result()
                                                                            .map(dynamic4 -> dynamic4.asList(Function.identity()))
                                                                            .orElse(Collections.emptyList())
                                                                    )
                                                                    .orElse(Collections.emptyList());
                                                                long[] longs = typed4.get(DSL.remainderFinder()).get("data").asLongStream().toArray();
                                                                return new ChunkProtoTickListFix.PoorMansPalettedContainer(list, longs);
                                                            }
                                                        )
                                                    )
                                                );
                                        }
                                    }
                                )
                        );
                    byte b = mutableInt.byteValue();
                    typed1 = typed1.update(DSL.remainderFinder(), dynamic3 -> dynamic3.update("yPos", dynamic4 -> dynamic4.createByte(b)));
                    if (!typed1.getOptionalTyped(opticFinder6).isPresent() && !dynamic.get("fluid_ticks").result().isPresent()) {
                        int _int = dynamic.get("xPos").asInt(0);
                        int _int1 = dynamic.get("zPos").asInt(0);
                        Dynamic<?> dynamic1 = this.makeTickList(dynamic, map, b, _int, _int1, "LiquidsToBeTicked", ChunkProtoTickListFix::getLiquid);
                        Dynamic<?> dynamic2 = this.makeTickList(dynamic, map, b, _int, _int1, "ToBeTicked", ChunkProtoTickListFix::getBlock);
                        Optional<? extends Pair<? extends Typed<?>, ?>> optional = opticFinder6.type().readTyped(dynamic2).result();
                        if (optional.isPresent()) {
                            typed1 = typed1.set(opticFinder6, (Typed<?>)optional.get().getFirst());
                        }

                        return typed1.update(
                            DSL.remainderFinder(), dynamic3 -> dynamic3.remove("ToBeTicked").remove("LiquidsToBeTicked").set("fluid_ticks", dynamic1)
                        );
                    } else {
                        return typed1;
                    }
                }
            )
        );
    }

    private Dynamic<?> makeTickList(
        Dynamic<?> data,
        Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> palette,
        byte y,
        int x,
        int z,
        String name,
        Function<Dynamic<?>, String> idGetter
    ) {
        Stream<Dynamic<?>> stream = Stream.empty();
        List<? extends Dynamic<?>> list = data.get(name).asList(Function.identity());

        for (int i = 0; i < list.size(); i++) {
            int i1 = i + y;
            Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> supplier = palette.get(i1);
            Stream<? extends Dynamic<?>> stream1 = list.get(i)
                .asStream()
                .mapToInt(dynamic -> dynamic.asShort((short)-1))
                .filter(i2 -> i2 > 0)
                .mapToObj(i2 -> this.createTick(data, supplier, x, i1, z, i2, idGetter));
            stream = Stream.concat(stream, stream1);
        }

        return data.createList(stream);
    }

    private static String getBlock(@Nullable Dynamic<?> data) {
        return data != null ? data.get("Name").asString("minecraft:air") : "minecraft:air";
    }

    private static String getLiquid(@Nullable Dynamic<?> data) {
        if (data == null) {
            return "minecraft:empty";
        } else {
            String string = data.get("Name").asString("");
            if ("minecraft:water".equals(string)) {
                return data.get("Properties").get("level").asInt(0) == 0 ? "minecraft:water" : "minecraft:flowing_water";
            } else if ("minecraft:lava".equals(string)) {
                return data.get("Properties").get("level").asInt(0) == 0 ? "minecraft:lava" : "minecraft:flowing_lava";
            } else {
                return !ALWAYS_WATERLOGGED.contains(string) && !data.get("Properties").get("waterlogged").asBoolean(false)
                    ? "minecraft:empty"
                    : "minecraft:water";
            }
        }
    }

    private Dynamic<?> createTick(
        Dynamic<?> data,
        @Nullable Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> palette,
        int x,
        int y,
        int z,
        int index,
        Function<Dynamic<?>, String> idGetter
    ) {
        int i = index & 15;
        int i1 = index >>> 4 & 15;
        int i2 = index >>> 8 & 15;
        String string = idGetter.apply(palette != null ? palette.get().get(i, i1, i2) : null);
        return data.createMap(
            ImmutableMap.<Dynamic<?>, Dynamic<?>>builder()
                .put(data.createString("i"), data.createString(string))
                .put(data.createString("x"), data.createInt(x * 16 + i))
                .put(data.createString("y"), data.createInt(y * 16 + i1))
                .put(data.createString("z"), data.createInt(z * 16 + i2))
                .put(data.createString("t"), data.createInt(0))
                .put(data.createString("p"), data.createInt(0))
                .build()
        );
    }

    public static final class PoorMansPalettedContainer {
        private static final long SIZE_BITS = 4L;
        private final List<? extends Dynamic<?>> palette;
        private final long[] data;
        private final int bits;
        private final long mask;
        private final int valuesPerLong;

        public PoorMansPalettedContainer(List<? extends Dynamic<?>> palette, long[] data) {
            this.palette = palette;
            this.data = data;
            this.bits = Math.max(4, ChunkHeightAndBiomeFix.ceillog2(palette.size()));
            this.mask = (1L << this.bits) - 1L;
            this.valuesPerLong = (char)(64 / this.bits);
        }

        public @Nullable Dynamic<?> get(int x, int y, int z) {
            int size = this.palette.size();
            if (size < 1) {
                return null;
            } else if (size == 1) {
                return (Dynamic<?>)this.palette.getFirst();
            } else {
                int index = this.getIndex(x, y, z);
                int i = index / this.valuesPerLong;
                if (i >= 0 && i < this.data.length) {
                    long l = this.data[i];
                    int i1 = (index - i * this.valuesPerLong) * this.bits;
                    int i2 = (int)(l >> i1 & this.mask);
                    return (Dynamic<?>)(i2 >= 0 && i2 < size ? this.palette.get(i2) : null);
                } else {
                    return null;
                }
            }
        }

        private int getIndex(int x, int y, int z) {
            return (y << 4 | z) << 4 | x;
        }

        public List<? extends Dynamic<?>> palette() {
            return this.palette;
        }

        public long[] data() {
            return this.data;
        }
    }
}
