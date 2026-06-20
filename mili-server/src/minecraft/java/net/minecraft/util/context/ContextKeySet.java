package net.minecraft.util.context;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import java.util.Set;

public class ContextKeySet {
    private final Set<ContextKey<?>> required;
    private final Set<ContextKey<?>> allowed;

    ContextKeySet(Set<ContextKey<?>> required, Set<ContextKey<?>> allowed) {
        this.required = Set.copyOf(required);
        this.allowed = Set.copyOf(Sets.union(required, allowed));
    }

    public Set<ContextKey<?>> required() {
        return this.required;
    }

    public Set<ContextKey<?>> allowed() {
        return this.allowed;
    }

    @Override
    public String toString() {
        return "[" + Joiner.on(", ").join(this.allowed.stream().map(key -> (this.required.contains(key) ? "!" : "") + key.name()).iterator()) + "]";
    }

    public static class Builder {
        private final Set<ContextKey<?>> required = Sets.newIdentityHashSet();
        private final Set<ContextKey<?>> optional = Sets.newIdentityHashSet();

        public ContextKeySet.Builder required(ContextKey<?> key) {
            if (this.optional.contains(key)) {
                throw new IllegalArgumentException("Parameter " + key.name() + " is already optional");
            } else {
                this.required.add(key);
                return this;
            }
        }

        public ContextKeySet.Builder optional(ContextKey<?> key) {
            if (this.required.contains(key)) {
                throw new IllegalArgumentException("Parameter " + key.name() + " is already required");
            } else {
                this.optional.add(key);
                return this;
            }
        }

        public ContextKeySet build() {
            return new ContextKeySet(this.required, this.optional);
        }
    }
}
