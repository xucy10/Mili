package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.Map;

public class PlayerEquipmentFix extends DataFix {
    private static final Map<Integer, String> SLOT_TRANSLATIONS = Map.of(100, "feet", 101, "legs", 102, "chest", 103, "head", -106, "offhand");

    public PlayerEquipmentFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> typeRaw = this.getInputSchema().getTypeRaw(References.PLAYER);
        Type<?> typeRaw1 = this.getOutputSchema().getTypeRaw(References.PLAYER);
        return this.writeFixAndRead("Player Equipment Fix", typeRaw, typeRaw1, dynamic -> {
            Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
            dynamic = dynamic.update("Inventory", dynamic1 -> dynamic1.createList(dynamic1.asStream().filter(dynamic2 -> {
                int _int = dynamic2.get("Slot").asInt(-1);
                String string = SLOT_TRANSLATIONS.get(_int);
                if (string != null) {
                    map.put(dynamic1.createString(string), dynamic2.remove("Slot"));
                }

                return string == null;
            })));
            return dynamic.set("equipment", dynamic.createMap(map));
        });
    }
}
