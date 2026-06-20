package net.minecraft.world.item;

import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class ItemStackLinkedSet {
    public static final Strategy<? super ItemStack> TYPE_AND_TAG = new Strategy<ItemStack>() {
        @Override
        public int hashCode(@Nullable ItemStack stack) {
            return ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(@Nullable ItemStack first, @Nullable ItemStack second) {
            return first == second
                || first != null && second != null && first.isEmpty() == second.isEmpty() && ItemStack.isSameItemSameComponents(first, second);
        }
    };

    public static Set<ItemStack> createTypeAndComponentsSet() {
        return new ObjectLinkedOpenCustomHashSet<>(TYPE_AND_TAG);
    }
}
