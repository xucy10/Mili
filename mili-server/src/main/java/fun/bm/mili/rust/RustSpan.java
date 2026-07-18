package fun.bm.mili.rust;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Rust-style Span&lt;T&gt; for zero-copy array views.
 * <p>
 * A Span is a lightweight view into a contiguous region of an array,
 * analogous to Rust's {@code &[T]}. It does not own the data and
 * does not allocate - it is a pure reference.
 * <p>
 * Usage:
 * <pre>{@code
 *   long[] data = new long[1000];
 *   Span<Long> span = Span.of(data, 10, 50);  // view elements 10-59
 *   for (long v : span) { process(v); }
 * }</pre>
 */
public final class RustSpan<T> implements Iterable<T> {

    private final T[] array;
    private final int offset;
    private final int length;

    private RustSpan(T[] array, int offset, int length) {
        this.array = array;
        this.offset = offset;
        this.length = length;
    }

    @SafeVarargs
    public static <T> RustSpan<T> of(T... array) {
        return new RustSpan<>(array, 0, array.length);
    }

    public static <T> RustSpan<T> of(T[] array, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new IndexOutOfBoundsException(
                    "offset=" + offset + ", length=" + length + ", array.length=" + array.length);
        }
        return new RustSpan<>(array, offset, length);
    }

    public static RustSpan<Long> ofLongs(long[] array, int offset, int length) {
        Long[] boxed = new Long[length];
        for (int i = 0; i < length; i++) {
            boxed[i] = array[offset + i];
        }
        return new RustSpan<>(boxed, 0, length);
    }

    public T get(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index=" + index + ", length=" + length);
        }
        return array[offset + index];
    }

    public int length() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public RustSpan<T> subspan(int start, int len) {
        if (start < 0 || len < 0 || start + len > length) {
            throw new IndexOutOfBoundsException(
                    "start=" + start + ", len=" + len + ", span.length=" + length);
        }
        return new RustSpan<>(array, offset + start, len);
    }

    public RustSpan<T> subspan(int start) {
        return subspan(start, length - start);
    }

    public T first() {
        if (length == 0) throw new NoSuchElementException("Span is empty");
        return array[offset];
    }

    public T last() {
        if (length == 0) throw new NoSuchElementException("Span is empty");
        return array[offset + length - 1];
    }

    public T[] toArray() {
        return Arrays.copyOfRange(array, offset, offset + length);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < length;
            }

            @Override
            public T next() {
                if (pos >= length) throw new NoSuchElementException();
                return array[offset + pos++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(length, 10); i++) {
            if (i > 0) sb.append(", ");
            sb.append(array[offset + i]);
        }
        if (length > 10) sb.append(", ...");
        sb.append("]");
        return sb.toString();
    }
}