package fun.bm.mili.rust;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Rust-style Cow&lt;T&gt; (Copy-on-Write) for snapshot caching.
 * <p>
 * Wraps a value that may be shared (borrowed) or exclusively owned.
 * The {@code get()} method returns the current value without cloning.
 * {@code mutate()} triggers a copy if the value is shared.
 * <p>
 * Useful for caching expensive-to-compute snapshots that are read
 * frequently but updated infrequently.
 */
public final class RustCow<T> {

    private volatile T value;
    private volatile boolean owned;

    private RustCow(T value, boolean owned) {
        this.value = value;
        this.owned = owned;
    }

    public static <T> RustCow<T> owned(T value) {
        Objects.requireNonNull(value);
        return new RustCow<>(value, true);
    }

    public static <T> RustCow<T> borrowed(T value) {
        Objects.requireNonNull(value);
        return new RustCow<>(value, false);
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        Objects.requireNonNull(newValue);
        this.value = newValue;
        this.owned = true;
    }

    public void setIfChanged(T newValue, Supplier<T> copier) {
        Objects.requireNonNull(newValue);
        if (!owned) {
            this.value = copier.get();
            this.owned = true;
        }
        this.value = newValue;
    }

    public boolean isOwned() {
        return owned;
    }

    public void makeOwned(Supplier<T> copier) {
        if (!owned) {
            this.value = copier.get();
            this.owned = true;
        }
    }
}