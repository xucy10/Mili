package net.minecraft.util.datafix.fixes;

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
import java.util.List;
import java.util.stream.LongStream;
import net.minecraft.util.Mth;

public class BitStorageAlignFix extends DataFix {
    private static final int BIT_TO_LONG_SHIFT = 6;
    private static final int SECTION_WIDTH = 16;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_SIZE = 4096;
    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_SIZE = 256;

    public BitStorageAlignFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = type.findFieldType("Level");
        OpticFinder<?> opticFinder = DSL.fieldFinder("Level", type1);
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("Sections");
        Type<?> element = ((ListType)opticFinder1.type()).getElement();
        OpticFinder<?> opticFinder2 = DSL.typeFinder(element);
        Type<Pair<String, Dynamic<?>>> type2 = DSL.named(References.BLOCK_STATE.typeName(), DSL.remainderType());
        OpticFinder<List<Pair<String, Dynamic<?>>>> opticFinder3 = DSL.fieldFinder("Palette", DSL.list(type2));
        return this.fixTypeEverywhereTyped(
            "BitStorageAlignFix",
            type,
            this.getOutputSchema().getType(References.CHUNK),
            typed -> typed.updateTyped(opticFinder, typed1 -> this.updateHeightmaps(updateSections(opticFinder1, opticFinder2, opticFinder3, typed1)))
        );
    }

    private Typed<?> updateHeightmaps(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "Heightmaps", dynamic1 -> dynamic1.updateMapValues(pair -> pair.mapSecond(dynamic2 -> updateBitStorage(dynamic, (Dynamic<?>)dynamic2, 256, 9)))
            )
        );
    }

    private static Typed<?> updateSections(
        OpticFinder<?> sectionsFinder, OpticFinder<?> sectionElementFinder, OpticFinder<List<Pair<String, Dynamic<?>>>> paletteFinder, Typed<?> data
    ) {
        return data.updateTyped(
            sectionsFinder,
            typed -> typed.updateTyped(
                sectionElementFinder,
                typed1 -> {
                    int i = typed1.getOptional(paletteFinder).map(list -> Math.max(4, DataFixUtils.ceillog2(list.size()))).orElse(0);
                    return i != 0 && !Mth.isPowerOfTwo(i)
                        ? typed1.update(
                            DSL.remainderFinder(), dynamic -> dynamic.update("BlockStates", dynamic1 -> updateBitStorage(dynamic, dynamic1, 4096, i))
                        )
                        : typed1;
                }
            )
        );
    }

    private static Dynamic<?> updateBitStorage(Dynamic<?> output, Dynamic<?> data, int numBits, int bitWidth) {
        long[] longs = data.asLongStream().toArray();
        long[] longs1 = addPadding(numBits, bitWidth, longs);
        return output.createLongList(LongStream.of(longs1));
    }

    public static long[] addPadding(int numBits, int bitWidth, long[] inputData) {
        int i = inputData.length;
        if (i == 0) {
            return inputData;
        } else {
            long l = (1L << bitWidth) - 1L;
            int i1 = 64 / bitWidth;
            int i2 = (numBits + i1 - 1) / i1;
            long[] longs = new long[i2];
            int i3 = 0;
            int i4 = 0;
            long l1 = 0L;
            int i5 = 0;
            long l2 = inputData[0];
            long l3 = i > 1 ? inputData[1] : 0L;

            for (int i6 = 0; i6 < numBits; i6++) {
                int i7 = i6 * bitWidth;
                int i8 = i7 >> 6;
                int i9 = (i6 + 1) * bitWidth - 1 >> 6;
                int i10 = i7 ^ i8 << 6;
                if (i8 != i5) {
                    l2 = l3;
                    l3 = i8 + 1 < i ? inputData[i8 + 1] : 0L;
                    i5 = i8;
                }

                long l4;
                if (i8 == i9) {
                    l4 = l2 >>> i10 & l;
                } else {
                    int i11 = 64 - i10;
                    l4 = (l2 >>> i10 | l3 << i11) & l;
                }

                int i11 = i4 + bitWidth;
                if (i11 >= 64) {
                    longs[i3++] = l1;
                    l1 = l4;
                    i4 = bitWidth;
                } else {
                    l1 |= l4 << i4;
                    i4 = i11;
                }
            }

            if (l1 != 0L) {
                longs[i3] = l1;
            }

            return longs;
        }
    }
}
