package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractUUIDFix extends DataFix {
    protected TypeReference typeReference;

    public AbstractUUIDFix(Schema outputSchema, TypeReference typeReference) {
        super(outputSchema, false);
        this.typeReference = typeReference;
    }

    protected Typed<?> updateNamedChoice(Typed<?> typed, String choiceName, Function<Dynamic<?>, Dynamic<?>> updater) {
        Type<?> choiceType = this.getInputSchema().getChoiceType(this.typeReference, choiceName);
        Type<?> choiceType1 = this.getOutputSchema().getChoiceType(this.typeReference, choiceName);
        return typed.updateTyped(DSL.namedChoice(choiceName, choiceType), choiceType1, typed1 -> typed1.update(DSL.remainderFinder(), updater));
    }

    protected static Optional<Dynamic<?>> replaceUUIDString(Dynamic<?> dynamic, String oldKey, String newKey) {
        return createUUIDFromString(dynamic, oldKey).map(dynamic1 -> dynamic.remove(oldKey).set(newKey, (Dynamic<?>)dynamic1));
    }

    protected static Optional<Dynamic<?>> replaceUUIDMLTag(Dynamic<?> dynamic, String oldKey, String newKey) {
        return dynamic.get(oldKey)
            .result()
            .flatMap(AbstractUUIDFix::createUUIDFromML)
            .map(dynamic1 -> dynamic.remove(oldKey).set(newKey, (Dynamic<?>)dynamic1));
    }

    protected static Optional<Dynamic<?>> replaceUUIDLeastMost(Dynamic<?> dynamic, String oldKey, String newKey) {
        String string = oldKey + "Most";
        String string1 = oldKey + "Least";
        return createUUIDFromLongs(dynamic, string, string1).map(dynamic1 -> dynamic.remove(string).remove(string1).set(newKey, (Dynamic<?>)dynamic1));
    }

    protected static Optional<Dynamic<?>> createUUIDFromString(Dynamic<?> dynamic, String uuidKey) {
        return dynamic.get(uuidKey).result().flatMap(dynamic1 -> {
            String string = dynamic1.asString(null);
            if (string != null) {
                try {
                    UUID uuid = UUID.fromString(string);
                    return createUUIDTag(dynamic, uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                } catch (IllegalArgumentException var4) {
                }
            }

            return Optional.empty();
        });
    }

    protected static Optional<Dynamic<?>> createUUIDFromML(Dynamic<?> dynamic) {
        return createUUIDFromLongs(dynamic, "M", "L");
    }

    protected static Optional<Dynamic<?>> createUUIDFromLongs(Dynamic<?> dynamic, String mostKey, String leastKey) {
        long _long = dynamic.get(mostKey).asLong(0L);
        long _long1 = dynamic.get(leastKey).asLong(0L);
        return _long != 0L && _long1 != 0L ? createUUIDTag(dynamic, _long, _long1) : Optional.empty();
    }

    protected static Optional<Dynamic<?>> createUUIDTag(Dynamic<?> dynamic, long most, long least) {
        return Optional.of(dynamic.createIntList(Arrays.stream(new int[]{(int)(most >> 32), (int)most, (int)(least >> 32), (int)least})));
    }
}
