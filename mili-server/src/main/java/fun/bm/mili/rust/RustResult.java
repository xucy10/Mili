package fun.bm.mili.rust;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Rust-style Result&lt;T, E&gt; for safe error handling without exceptions.
 * <p>
 * Mirrors Rust's {@code Result<T, E>} enum: either {@code Ok(T)} or {@code Err(E)}.
 * Zero-cost in the JIT: both variants are final value classes that the compiler
 * can scalarize onto the stack.
 * <p>
 * Usage:
 * <pre>{@code
 *   Result<Chunk, String> result = loadChunk(cx, cz);
 *   result.match(
 *       chunk -> processChunk(chunk),
 *       error -> logError(error)
 *   );
 * }</pre>
 */
public sealed interface RustResult<T, E> {

    static <T, E> RustResult<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> RustResult<T, E> err(E error) {
        return new Err<>(error);
    }

    boolean isOk();
    boolean isErr();

    T unwrap();
    E unwrapErr();
    T unwrapOr(T defaultValue);
    T expect(String message);

    <U> RustResult<U, E> map(Function<? super T, ? extends U> mapper);
    <F> RustResult<T, F> mapErr(Function<? super E, ? extends F> mapper);
    <U> RustResult<U, E> andThen(Function<? super T, RustResult<U, E>> mapper);
    RustResult<T, E> orElse(Supplier<RustResult<T, E>> supplier);

    void match(Consumer<? super T> onOk, Consumer<? super E> onErr);

    record Ok<T, E>(T value) implements RustResult<T, E> {
        public Ok {
            Objects.requireNonNull(value, "Ok value must not be null");
        }

        @Override public boolean isOk() { return true; }
        @Override public boolean isErr() { return false; }
        @Override public T unwrap() { return value; }
        @Override public E unwrapErr() { throw new NoSuchElementException("Called unwrapErr on Ok"); }
        @Override public T unwrapOr(T defaultValue) { return value; }
        @Override public T expect(String message) { return value; }

        @Override @SuppressWarnings("unchecked")
        public <U> RustResult<U, E> map(Function<? super T, ? extends U> mapper) {
            return (RustResult<U, E>) RustResult.ok(mapper.apply(value));
        }

        @Override @SuppressWarnings("unchecked")
        public <F> RustResult<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            return (RustResult<T, F>) this;
        }

        @Override
        public <U> RustResult<U, E> andThen(Function<? super T, RustResult<U, E>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public RustResult<T, E> orElse(Supplier<RustResult<T, E>> supplier) {
            return this;
        }

        @Override
        public void match(Consumer<? super T> onOk, Consumer<? super E> onErr) {
            onOk.accept(value);
        }
    }

    record Err<T, E>(E error) implements RustResult<T, E> {
        public Err {
            Objects.requireNonNull(error, "Err value must not be null");
        }

        @Override public boolean isOk() { return false; }
        @Override public boolean isErr() { return true; }
        @Override public T unwrap() { throw new NoSuchElementException("Called unwrap on Err: " + error); }
        @Override public E unwrapErr() { return error; }
        @Override public T unwrapOr(T defaultValue) { return defaultValue; }
        @Override public T expect(String message) { throw new NoSuchElementException(message + ": " + error); }

        @Override @SuppressWarnings("unchecked")
        public <U> RustResult<U, E> map(Function<? super T, ? extends U> mapper) {
            return (RustResult<U, E>) this;
        }

        @Override @SuppressWarnings("unchecked")
        public <F> RustResult<T, F> mapErr(Function<? super E, ? extends F> mapper) {
            return (RustResult<T, F>) RustResult.err(mapper.apply(error));
        }

        @Override
        public <U> RustResult<U, E> andThen(Function<? super T, RustResult<U, E>> mapper) {
            return RustResult.err(error);
        }

        @Override
        public RustResult<T, E> orElse(Supplier<RustResult<T, E>> supplier) {
            return supplier.get();
        }

        @Override
        public void match(Consumer<? super T> onOk, Consumer<? super E> onErr) {
            onErr.accept(error);
        }
    }
}