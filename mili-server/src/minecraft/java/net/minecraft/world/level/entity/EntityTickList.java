package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public class EntityTickList {
    private final ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<net.minecraft.world.entity.Entity> entities = new ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet<>(); // Paper - rewrite chunk system

    private void ensureActiveIsNotIterated() {
        // Paper - rewrite chunk system
    }

    public void add(Entity entity) {
        this.ensureActiveIsNotIterated();
        this.entities.add(entity); // Paper - rewrite chunk system
    }

    public void remove(Entity entity) {
        this.ensureActiveIsNotIterated();
        this.entities.remove(entity); // Paper - rewrite chunk system
    }

    public boolean contains(Entity entity) {
        return this.entities.contains(entity); // Paper - rewrite chunk system
    }

    public void forEach(Consumer<Entity> entity) {
        // Paper start - rewrite chunk system
        // To ensure nothing weird happens with dimension travelling, do not iterate over new entries...
        // (by dfl iterator() is configured to not iterate over new entries)
        final ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.entities.iterator();
        try {
            while (iterator.hasNext()) {
                entity.accept(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
        // Paper end - rewrite chunk system
    }
}
