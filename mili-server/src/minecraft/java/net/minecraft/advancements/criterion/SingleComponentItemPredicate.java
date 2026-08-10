package net.minecraft.advancements.criterion;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;

public interface SingleComponentItemPredicate<T> extends DataComponentPredicate {
    @Override
    default boolean matches(DataComponentGetter componentGetter) {
        T object = componentGetter.get(this.componentType());
        return object != null && this.matches(object);
    }

    DataComponentType<T> componentType();

    boolean matches(T value);
}
