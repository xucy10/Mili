package net.minecraft.world.inventory;

import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface RemoteSlot {
    RemoteSlot PLACEHOLDER = new RemoteSlot() {
        @Override
        public void receive(HashedStack stack) {
        }

        @Override
        public void force(ItemStack stack) {
        }

        @Override
        public boolean matches(ItemStack stack) {
            return true;
        }
    };

    void force(ItemStack stack);

    void receive(HashedStack stack);

    boolean matches(ItemStack stack);

    public static class Synchronized implements RemoteSlot {
        private final HashedPatchMap.HashGenerator hasher;
        private @Nullable ItemStack remoteStack = null;
        private @Nullable HashedStack remoteHash = null;

        public Synchronized(HashedPatchMap.HashGenerator hasher) {
            this.hasher = hasher;
        }

        @Override
        public void force(ItemStack stack) {
            this.remoteStack = stack.copy();
            this.remoteHash = null;
        }

        @Override
        public void receive(HashedStack stack) {
            this.remoteStack = null;
            this.remoteHash = stack;
        }

        @Override
        public boolean matches(ItemStack stack) {
            if (this.remoteStack != null) {
                return ItemStack.matches(this.remoteStack, stack);
            } else if (this.remoteHash != null && this.remoteHash.matches(stack, this.hasher)) {
                this.remoteStack = stack.copy();
                return true;
            } else {
                return false;
            }
        }

        public void copyFrom(RemoteSlot.Synchronized other) {
            this.remoteStack = other.remoteStack;
            this.remoteHash = other.remoteHash;
        }
    }
}
