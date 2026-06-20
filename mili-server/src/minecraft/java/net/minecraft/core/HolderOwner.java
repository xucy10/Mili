package net.minecraft.core;

public interface HolderOwner<T> {
    default boolean canSerializeIn(HolderOwner<T> owner) {
        return owner == this;
    }
}
