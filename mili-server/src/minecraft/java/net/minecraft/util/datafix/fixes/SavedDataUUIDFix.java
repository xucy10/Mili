package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import org.slf4j.Logger;

public class SavedDataUUIDFix extends AbstractUUIDFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public SavedDataUUIDFix(Schema outputSchema) {
        super(outputSchema, References.SAVED_DATA_RAIDS);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "SavedDataUUIDFix",
            this.getInputSchema().getType(this.typeReference),
            typed -> typed.update(
                DSL.remainderFinder(),
                dynamic -> dynamic.update(
                    "data",
                    dynamic1 -> dynamic1.update(
                        "Raids",
                        dynamic2 -> dynamic2.createList(
                            dynamic2.asStream()
                                .map(
                                    dynamic3 -> dynamic3.update(
                                        "HeroesOfTheVillage",
                                        dynamic4 -> dynamic4.createList(
                                            dynamic4.asStream()
                                                .map(dynamic5 -> createUUIDFromLongs((Dynamic<?>)dynamic5, "UUIDMost", "UUIDLeast").orElseGet(() -> {
                                                    LOGGER.warn("HeroesOfTheVillage contained invalid UUIDs.");
                                                    return dynamic5;
                                                }))
                                        )
                                    )
                                )
                        )
                    )
                )
            )
        );
    }
}
