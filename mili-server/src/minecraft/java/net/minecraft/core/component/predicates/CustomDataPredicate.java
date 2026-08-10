package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.core.component.DataComponentGetter;

public record CustomDataPredicate(NbtPredicate value) implements DataComponentPredicate {
    public static final Codec<CustomDataPredicate> CODEC = NbtPredicate.CODEC.xmap(CustomDataPredicate::new, CustomDataPredicate::value);

    @Override
    public boolean matches(DataComponentGetter componentGetter) {
        return this.value.matches(componentGetter);
    }

    public static CustomDataPredicate customData(NbtPredicate value) {
        return new CustomDataPredicate(value);
    }
}
