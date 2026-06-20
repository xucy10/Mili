package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class HeightmapRenamingFix extends DataFix {
    public HeightmapRenamingFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        return this.fixTypeEverywhereTyped(
            "HeightmapRenamingFix", type, typed -> typed.updateTyped(opticFinder, typed1 -> typed1.update(DSL.remainderFinder(), this::fix))
        );
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = dynamic.get("Heightmaps").result();
        if (optional.isEmpty()) {
            return dynamic;
        } else {
            Dynamic<?> dynamic1 = (Dynamic<?>)optional.get();
            Optional<? extends Dynamic<?>> optional1 = dynamic1.get("LIQUID").result();
            if (optional1.isPresent()) {
                dynamic1 = dynamic1.remove("LIQUID");
                dynamic1 = dynamic1.set("WORLD_SURFACE_WG", (Dynamic<?>)optional1.get());
            }

            Optional<? extends Dynamic<?>> optional2 = dynamic1.get("SOLID").result();
            if (optional2.isPresent()) {
                dynamic1 = dynamic1.remove("SOLID");
                dynamic1 = dynamic1.set("OCEAN_FLOOR_WG", (Dynamic<?>)optional2.get());
                dynamic1 = dynamic1.set("OCEAN_FLOOR", (Dynamic<?>)optional2.get());
            }

            Optional<? extends Dynamic<?>> optional3 = dynamic1.get("LIGHT").result();
            if (optional3.isPresent()) {
                dynamic1 = dynamic1.remove("LIGHT");
                dynamic1 = dynamic1.set("LIGHT_BLOCKING", (Dynamic<?>)optional3.get());
            }

            Optional<? extends Dynamic<?>> optional4 = dynamic1.get("RAIN").result();
            if (optional4.isPresent()) {
                dynamic1 = dynamic1.remove("RAIN");
                dynamic1 = dynamic1.set("MOTION_BLOCKING", (Dynamic<?>)optional4.get());
                dynamic1 = dynamic1.set("MOTION_BLOCKING_NO_LEAVES", (Dynamic<?>)optional4.get());
            }

            return dynamic.set("Heightmaps", dynamic1);
        }
    }
}
