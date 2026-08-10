package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class ForcedChunkToTicketFix extends DataFix {
    public ForcedChunkToTicketFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "ForcedChunkToTicketFix",
            this.getInputSchema().getType(References.SAVED_DATA_TICKETS),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.update(
                    "data",
                    dynamic1 -> dynamic1.renameAndFixField(
                        "Forced",
                        "tickets",
                        dynamic2 -> dynamic2.createList(
                            dynamic2.asLongStream()
                                .mapToObj(
                                    l -> dynamic.emptyMap()
                                        .set("type", dynamic.createString("minecraft:forced"))
                                        .set("level", dynamic.createInt(31))
                                        .set("ticks_left", dynamic.createLong(0L))
                                        .set("chunk_pos", dynamic.createLong(l))
                                )
                        )
                    )
                )
            )
        );
    }
}
