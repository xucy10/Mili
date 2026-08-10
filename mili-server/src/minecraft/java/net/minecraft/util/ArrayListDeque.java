package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public class ArrayListDeque<T> extends AbstractList<T> implements ListAndDeque<T> {
    private static final int MIN_GROWTH = 1;
    private @Nullable Object[] contents;
    private int head;
    private int size;

    public ArrayListDeque() {
        this(1);
    }

    public ArrayListDeque(int size) {
        this.contents = new Object[size];
        this.head = 0;
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    @VisibleForTesting
    public int capacity() {
        return this.contents.length;
    }

    private int getIndex(int index) {
        return (index + this.head) % this.contents.length;
    }

    @Override
    public T get(int index) {
        this.verifyIndexInRange(index);
        return this.getInner(this.getIndex(index));
    }

    private static void verifyIndexInRange(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void verifyIndexInRange(int index) {
        verifyIndexInRange(index, this.size);
    }

    private T getInner(int index) {
        return (T)this.contents[index];
    }

    @Override
    public T set(int index, T value) {
        this.verifyIndexInRange(index);
        Objects.requireNonNull(value);
        int index1 = this.getIndex(index);
        T inner = this.getInner(index1);
        this.contents[index1] = value;
        return inner;
    }

    @Override
    public void add(int index, T element) {
        verifyIndexInRange(index, this.size + 1);
        Objects.requireNonNull(element);
        if (this.size == this.contents.length) {
            this.grow();
        }

        int index1 = this.getIndex(index);
        if (index == this.size) {
            this.contents[index1] = element;
        } else if (index == 0) {
            this.head--;
            if (this.head < 0) {
                this.head = this.head + this.contents.length;
            }

            this.contents[this.getIndex(0)] = element;
        } else {
            for (int i = this.size - 1; i >= index; i--) {
                this.contents[this.getIndex(i + 1)] = this.contents[this.getIndex(i)];
            }

            this.contents[index1] = element;
        }

        this.modCount++;
        this.size++;
    }

    private void grow() {
        int i = this.contents.length + Math.max(this.contents.length >> 1, 1);
        Object[] objects = new Object[i];
        this.copyCount(objects, this.size);
        this.head = 0;
        this.contents = objects;
    }

    @Override
    public T remove(int index) {
        this.verifyIndexInRange(index);
        int index1 = this.getIndex(index);
        T inner = this.getInner(index1);
        if (index == 0) {
            this.contents[index1] = null;
            this.head++;
        } else if (index == this.size - 1) {
            this.contents[index1] = null;
        } else {
            for (int i = index + 1; i < this.size; i++) {
                this.contents[this.getIndex(i - 1)] = this.get(i);
            }

            this.contents[this.getIndex(this.size - 1)] = null;
        }

        this.modCount++;
        this.size--;
        return inner;
    }

    @Override
    public boolean removeIf(Predicate<? super T> predicate) {
        int i = 0;

        for (int i1 = 0; i1 < this.size; i1++) {
            T object = this.get(i1);
            if (predicate.test(object)) {
                i++;
            } else if (i != 0) {
                this.contents[this.getIndex(i1 - i)] = object;
                this.contents[this.getIndex(i1)] = null;
            }
        }

        this.modCount += i;
        this.size -= i;
        return i != 0;
    }

    private void copyCount(Object[] output, int count) {
        for (int i = 0; i < count; i++) {
            output[i] = this.get(i);
        }
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        for (int i = 0; i < this.size; i++) {
            int index = this.getIndex(i);
            this.contents[index] = Objects.requireNonNull(operator.apply(this.getInner(i)));
        }
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (int i = 0; i < this.size; i++) {
            action.accept(this.get(i));
        }
    }

    @Override
    public void addFirst(T element) {
        this.add(0, element);
    }

    @Override
    public void addLast(T element) {
        this.add(this.size, element);
    }

    @Override
    public boolean offerFirst(T element) {
        this.addFirst(element);
        return true;
    }

    @Override
    public boolean offerLast(T element) {
        this.addLast(element);
        return true;
    }

    @Override
    public T removeFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.remove(0);
        }
    }

    @Override
    public T removeLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.remove(this.size - 1);
        }
    }

    @Override
    public ListAndDeque<T> reversed() {
        return new ArrayListDeque.ReversedView(this);
    }

    @Override
    public @Nullable T pollFirst() {
        return this.size == 0 ? null : this.removeFirst();
    }

    @Override
    public @Nullable T pollLast() {
        return this.size == 0 ? null : this.removeLast();
    }

    @Override
    public T getFirst() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.get(0);
        }
    }

    @Override
    public T getLast() {
        if (this.size == 0) {
            throw new NoSuchElementException();
        } else {
            return this.get(this.size - 1);
        }
    }

    @Override
    public @Nullable T peekFirst() {
        return this.size == 0 ? null : this.getFirst();
    }

    @Override
    public @Nullable T peekLast() {
        return this.size == 0 ? null : this.getLast();
    }

    @Override
    public boolean removeFirstOccurrence(Object element) {
        for (int i = 0; i < this.size; i++) {
            T object = this.get(i);
            if (Objects.equals(element, object)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean removeLastOccurrence(Object element) {
        for (int i = this.size - 1; i >= 0; i--) {
            T object = this.get(i);
            if (Objects.equals(element, object)) {
                this.remove(i);
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<T> descendingIterator() {
        return new ArrayListDeque.DescendingIterator();
    }

    class DescendingIterator implements Iterator<T> {
        private int index = ArrayListDeque.this.size() - 1;

        public DescendingIterator() {
        }

        @Override
        public boolean hasNext() {
            return this.index >= 0;
        }

        @Override
        public T next() {
            return ArrayListDeque.this.get(this.index--);
        }

        @Override
        public void remove() {
            ArrayListDeque.this.remove(this.index + 1);
        }
    }

    class ReversedView extends AbstractList<T> implements ListAndDeque<T> {
        private final ArrayListDeque<T> source;

        public ReversedView(final ArrayListDeque<T> source) {
            this.source = source;
        }

        @Override
        public ListAndDeque<T> reversed() {
            return this.source;
        }

        @Override
        public T getFirst() {
            return this.source.getLast();
        }

        @Override
        public T getLast() {
            return this.source.getFirst();
        }

        @Override
        public void addFirst(T element) {
            this.source.addLast(element);
        }

        @Override
        public void addLast(T element) {
            this.source.addFirst(element);
        }

        @Override
        public boolean offerFirst(T element) {
            return this.source.offerLast(element);
        }

        @Override
        public boolean offerLast(T element) {
            return this.source.offerFirst(element);
        }

        @Override
        public @Nullable T pollFirst() {
            return this.source.pollLast();
        }

        @Override
        public @Nullable T pollLast() {
            return this.source.pollFirst();
        }

        @Override
        public @Nullable T peekFirst() {
            return this.source.peekLast();
        }

        @Override
        public @Nullable T peekLast() {
            return this.source.peekFirst();
        }

        @Override
        public T removeFirst() {
            return this.source.removeLast();
        }

        @Override
        public T removeLast() {
            return this.source.removeFirst();
        }

        @Override
        public boolean removeFirstOccurrence(Object element) {
            return this.source.removeLastOccurrence(element);
        }

        @Override
        public boolean removeLastOccurrence(Object element) {
            return this.source.removeFirstOccurrence(element);
        }

        @Override
        public Iterator<T> descendingIterator() {
            return this.source.iterator();
        }

        @Override
        public int size() {
            return this.source.size();
        }

        @Override
        public boolean isEmpty() {
            return this.source.isEmpty();
        }

        @Override
        public boolean contains(Object element) {
            return this.source.contains(element);
        }

        @Override
        public T get(int index) {
            return this.source.get(this.reverseIndex(index));
        }

        @Override
        public T set(int index, T element) {
            return this.source.set(this.reverseIndex(index), element);
        }

        @Override
        public void add(int index, T element) {
            this.source.add(this.reverseIndex(index) + 1, element);
        }

        @Override
        public T remove(int index) {
            return this.source.remove(this.reverseIndex(index));
        }

        @Override
        public int indexOf(Object element) {
            return this.reverseIndex(this.source.lastIndexOf(element));
        }

        @Override
        public int lastIndexOf(Object element) {
            return this.reverseIndex(this.source.indexOf(element));
        }

        @Override
        public List<T> subList(int startIndex, int endIndex) {
            return this.source.subList(this.reverseIndex(endIndex) + 1, this.reverseIndex(startIndex) + 1).reversed();
        }

        @Override
        public Iterator<T> iterator() {
            return this.source.descendingIterator();
        }

        @Override
        public void clear() {
            this.source.clear();
        }

        private int reverseIndex(int index) {
            return index == -1 ? -1 : this.source.size() - 1 - index;
        }
    }
}
