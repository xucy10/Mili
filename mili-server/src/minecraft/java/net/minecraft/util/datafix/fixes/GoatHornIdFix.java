package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class GoatHornIdFix extends ItemStackTagRemainderFix {
    private static final String[] INSTRUMENTS = new String[]{
        "minecraft:ponder_goat_horn",
        "minecraft:sing_goat_horn",
        "minecraft:seek_goat_horn",
        "minecraft:feel_goat_horn",
        "minecraft:admire_goat_horn",
        "minecraft:call_goat_horn",
        "minecraft:yearn_goat_horn",
        "minecraft:dream_goat_horn"
    };

    public GoatHornIdFix(Schema outputSchema) {
        super(outputSchema, "GoatHornIdFix", string -> string.equals("minecraft:goat_horn"));
    }

    @Override
    protected <T> Dynamic<T> fixItemStackTag(Dynamic<T> data) {
        int _int = data.get("SoundVariant").asInt(0);
        String string = INSTRUMENTS[_int >= 0 && _int < INSTRUMENTS.length ? _int : 0];
        return data.remove("SoundVariant").set("instrument", data.createString(string));
    }
}
