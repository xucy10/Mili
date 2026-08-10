package net.minecraft.world.level.storage.loot.entries;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;

@FunctionalInterface
interface ComposableEntryContainer {
    ComposableEntryContainer ALWAYS_FALSE = (lootContext, entryConsumer) -> false;
    ComposableEntryContainer ALWAYS_TRUE = (lootContext, entryConsumer) -> true;

    boolean expand(LootContext lootContext, Consumer<LootPoolEntry> entryConsumer);

    default ComposableEntryContainer and(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (lootContext, entryConsumer) -> this.expand(lootContext, entryConsumer) && entry.expand(lootContext, entryConsumer);
    }

    default ComposableEntryContainer or(ComposableEntryContainer entry) {
        Objects.requireNonNull(entry);
        return (lootContext, entryConsumer) -> this.expand(lootContext, entryConsumer) || entry.expand(lootContext, entryConsumer);
    }
}
