package fun.bm.mili.rust;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Rust-style Option&lt;T&gt; for null-safe value handling.
 * <p>
 * Mirrors Rust's {@code Option<T>} enum: either {@code Some(T)} or {@code None}.
 * Eliminates NullPointerException risk by forcing explicit unwrap.
 * The JIT compiler can scalarize both variants onto the stack.
 * <p>
 * Usage:
 * <pre>{@code
 *   Option<Chunk> chunk = getChunk(cx, cz);
 *   chunk.match(
 *       c -> tickChunk(c),
 *       () -> skipChunk()
 *   );
 * }</pre>
 */
public sealed interface RustOption<T> {

    @SuppressWarnings("unchecked")
    static <T> RustOption<T> some(T value) {
        Objects.requireNonNull(value, "Some value must not be null");
        return (RustOption<T>) new Some<>(value);
    }

    @SuppressWarnings("unchecked")
    static <T> RustOption<T> none() {
        return (RustOption<T>) None.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <T> RustOption<T> ofNullable(T value) {
        return value == null ? (RustOption<T>) None.INSTANCE : new Some<>(value);
    }

    boolean isSome();
    boolean isNone();

    T unwrap();
    T unwrapOr(T defaultValue);
    T unwrapOrElse(Supplier<? extends T> supplier);
    T expect(String message);

    <U> RustOption<U> map(Function<? super T, ? extends U> mapper);
    <U> RustOption<U> flatMap(Function<? super T, RustOption<U>> mapper);
    RustOption<T> orElse(Supplier<RustOption<T>> supplier);
    RustOption<T> filter(java.util.function.Predicate<? super T> predicate);

    void match(Consumer<? super T> onSome, Runnable onNone);

    record Some<T>(T value) implements RustOption<T> {
        public Some {
            Objects.requireNonNull(value, "Some value must not be null");
        }

        @Override public boolean isSome() { return true; }
        @Override public boolean isNone() { return false; }
        @Override public T unwrap() { return value; }
        @Override public T unwrapOr(T defaultValue) { return value; }
        @Override public T unwrapOrElse(Supplier<? extends T> supplier) { return value; }
        @Override public T expect(String message) { return value; }

        @Override @SuppressWarnings("unchecked")
        public <U> RustOption<U> map(Function<? super T, ? extends U> mapper) {
            return (RustOption<U>) RustOption.some(mapper.apply(value));
        }

        @Override
        public <U> RustOption<U> flatMap(Function<? super T, RustOption<U>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public RustOption<T> orElse(Supplier<RustOption<T>> supplier) {
            return this;
        }

        @Override
        public RustOption<T> filter(java.util.function.Predicate<? super T> predicate) {
            return predicate.test(value) ? this : RustOption.none();
        }

        @Override
        public void match(Consumer<? super T> onSome, Runnable onNone) {
            onSome.accept(value);
        }
    }

    final class None implements RustOption<Object> {
        static final None INSTANCE = new None();
        private None() {}

        @Override public boolean isSome() { return false; }
        @Override public boolean isNone() { return true; }
        @Override public Object unwrap() { throw new NoSuchElementException("Called unwrap on None"); }
        @Override public Object unwrapOr(Object defaultValue) { return defaultValue; }
        @Override public Object unwrapOrElse(Supplier<?> supplier) { return supplier.get(); }
        @Override public Object expect(String message) { throw new NoSuchElementException(message); }

        @Override public <U> RustOption<U> map(Function<? super Object, ? extends U> mapper) { return RustOption.none(); }
        @Override public <U> RustOption<U> flatMap(Function<? super Object, RustOption<U>> mapper) { return RustOption.none(); }
        @Override public RustOption<Object> orElse(Supplier<RustOption<Object>> supplier) { return supplier.get(); }
        @Override public RustOption<Object> filter(java.util.function.Predicate<? super Object> predicate) { return this; }

        @Override public void match(Consumer<? super Object> onSome, Runnable onNone) {
            onNone.run();
        }
    }
}