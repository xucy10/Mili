package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PoiTypeRemoveFix extends AbstractPoiSectionFix {
    private final Predicate<String> typesToKeep;

    public PoiTypeRemoveFix(Schema outputSchema, String name, Predicate<String> typesToRemove) {
        super(outputSchema, name);
        this.typesToKeep = typesToRemove.negate();
    }

    @Override
    protected <T> Stream<Dynamic<T>> processRecords(Stream<Dynamic<T>> records) {
        return records.filter(this::shouldKeepRecord);
    }

    private <T> boolean shouldKeepRecord(Dynamic<T> _record) {
        return _record.get("type").asString().result().filter(this.typesToKeep).isPresent();
    }
}
