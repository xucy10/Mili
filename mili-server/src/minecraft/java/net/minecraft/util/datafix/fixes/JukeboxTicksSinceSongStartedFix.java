package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class JukeboxTicksSinceSongStartedFix extends NamedEntityFix {
    public JukeboxTicksSinceSongStartedFix(Schema outputSchema) {
        super(outputSchema, false, "JukeboxTicksSinceSongStartedFix", References.BLOCK_ENTITY, "minecraft:jukebox");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        long l = tag.get("TickCount").asLong(0L) - tag.get("RecordStartTick").asLong(0L);
        Dynamic<?> dynamic = tag.remove("IsPlaying").remove("TickCount").remove("RecordStartTick");
        return l > 0L ? dynamic.set("ticks_since_song_started", tag.createLong(l)) : dynamic;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
