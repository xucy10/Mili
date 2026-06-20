package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class MapBannerBlockPosFormatFix extends DataFix {
    public MapBannerBlockPosFormatFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA);
        OpticFinder<?> opticFinder = type.findField("data");
        OpticFinder<?> opticFinder1 = opticFinder.type().findField("banners");
        OpticFinder<?> opticFinder2 = DSL.typeFinder(((ListType)opticFinder1.type()).getElement());
        return this.fixTypeEverywhereTyped(
            "MapBannerBlockPosFormatFix",
            type,
            typed -> typed.updateTyped(
                opticFinder,
                typed1 -> typed1.updateTyped(
                    opticFinder1,
                    typed2 -> typed2.updateTyped(
                        opticFinder2, typed3 -> typed3.update(DSL.remainderFinder(), dynamic -> dynamic.update("Pos", ExtraDataFixUtils::fixBlockPos))
                    )
                )
            )
        );
    }
}
