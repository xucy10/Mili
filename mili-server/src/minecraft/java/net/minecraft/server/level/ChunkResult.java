package net.minecraft.server.level;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface ChunkResult<T> {
    static <T> ChunkResult<T> of(T value) {
        return new ChunkResult.Success<>(value);
    }

    static <T> ChunkResult<T> error(String error) {
        return error(() -> error);
    }

    static <T> ChunkResult<T> error(Supplier<String> errorSupplier) {
        return new ChunkResult.Fail<>(errorSupplier);
    }

    boolean isSuccess();

    @Nullable T orElse(@Nullable T value);

    static <R> @Nullable R orElse(ChunkResult<? extends R> chunkResult, @Nullable R orElse) {
        R object = (R)chunkResult.orElse(null);
        return object != null ? object : orElse;
    }

    @Nullable String getError();

    ChunkResult<T> ifSuccess(Consumer<T> action);

    <R> ChunkResult<R> map(Function<T, R> mappingFunction);

    <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E;

    public record Fail<T>(Supplier<String> error) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public @Nullable T orElse(@Nullable T value) {
            return value;
        }

        @Override
        public String getError() {
            return this.error.get();
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> action) {
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> mappingFunction) {
            return new ChunkResult.Fail(this.error);
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            throw exceptionSupplier.get();
        }
    }

    public record Success<T>(T value) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T orElse(@Nullable T value) {
            return this.value;
        }

        @Override
        public @Nullable String getError() {
            return null;
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> action) {
            action.accept(this.value);
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> mappingFunction) {
            return new ChunkResult.Success<>(mappingFunction.apply(this.value));
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            return this.value;
        }
    }
}
