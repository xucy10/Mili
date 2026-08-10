package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

public class ChunkBiomeFix extends DataFix {
    public ChunkBiomeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        return this.fixTypeEverywhereTyped(
            "Leaves fix", type, typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), dynamic -> {
                Optional<IntStream> optional = dynamic.get("Biomes").asIntStreamOpt().result();
                if (optional.isEmpty()) {
                    return dynamic;
                } else {
                    int[] ints = optional.get().toArray();
                    if (ints.length != 256) {
                        return dynamic;
                    } else {
                        int[] ints1 = new int[1024];

                        for (int i = 0; i < 4; i++) {
                            for (int i1 = 0; i1 < 4; i1++) {
                                int i2 = (i1 << 2) + 2;
                                int i3 = (i << 2) + 2;
                                int i4 = i3 << 4 | i2;
                                ints1[i << 2 | i1] = ints[i4];
                            }
                        }

                        for (int i = 1; i < 64; i++) {
                            System.arraycopy(ints1, 0, ints1, i * 16, 16);
                        }

                        return dynamic.set("Biomes", dynamic.createIntList(Arrays.stream(ints1)));
                    }
                }
            }))
        );
    }
}
