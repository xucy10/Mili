package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.stream.IntStream;

public class ChunkTicketUnpackPosFix extends DataFix {
    private static final long CHUNK_COORD_BITS = 32L;
    private static final long CHUNK_COORD_MASK = 4294967295L;

    public ChunkTicketUnpackPosFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "ChunkTicketUnpackPosFix",
            this.getInputSchema().getType(References.SAVED_DATA_TICKETS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.update(
                    "data",
                    dynamic1 -> dynamic1.update(
                        "tickets", dynamic2 -> dynamic2.createList(dynamic2.asStream().map(dynamic3 -> dynamic3.update("chunk_pos", dynamic4 -> {
                            long _long = dynamic4.asLong(0L);
                            int i = (int)(_long & 4294967295L);
                            int i1 = (int)(_long >>> 32 & 4294967295L);
                            return dynamic4.createIntList(IntStream.of(i, i1));
                        })))
                    )
                )
            )
        );
    }
}
