package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public class InvalidLockComponentFix extends DataComponentRemainderFix {
    private static final Optional<String> INVALID_LOCK_CUSTOM_NAME = Optional.of("\"\"");

    public InvalidLockComponentFix(Schema outputSchema) {
        super(outputSchema, "InvalidLockComponentPredicateFix", "minecraft:lock");
    }

    @Override
    protected <T> @Nullable Dynamic<T> fixComponent(Dynamic<T> component) {
        return fixLock(component);
    }

    public static <T> @Nullable Dynamic<T> fixLock(Dynamic<T> tag) {
        return isBrokenLock(tag) ? null : tag;
    }

    private static <T> boolean isBrokenLock(Dynamic<T> tag) {
        return isMapWithOneField(
            tag,
            "components",
            dynamic -> isMapWithOneField(dynamic, "minecraft:custom_name", dynamic1 -> dynamic1.asString().result().equals(INVALID_LOCK_CUSTOM_NAME))
        );
    }

    private static <T> boolean isMapWithOneField(Dynamic<T> tag, String key, Predicate<Dynamic<T>> predicate) {
        Optional<Map<Dynamic<T>, Dynamic<T>>> optional = tag.getMapValues().result();
        return !optional.isEmpty() && optional.get().size() == 1 && tag.get(key).result().filter(predicate).isPresent();
    }
}
