package fun.bm.mili.perf;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * mili - 有界、无锁、多生产者单消费者环形缓冲区 / Bounded, lock-free, multi-producer
 * single-consumer ring buffer for inter-thread event delivery.
 *
 * <p>适用于 Folia 的常见场景: 多个 tick 线程向单个消费者线程发布小事件 / Designed for
 * the common case in Folia: many tick threads posting small events to a single consumer.
 *
 * <p><b>语义 / Semantics:</b>
 * <ul>
 *   <li>单消费者、多生产者，生产者无等待 / Single consumer, multi-producer, wait-free producers</li>
 *   <li>有界，满时 offer() 返回 false / Bounded, offer() returns false when full</li>
 *   <li>容量为 2 的幂，索引用位掩码而非取模 / Power-of-two capacity, index masked not modulo</li>
 *   <li>acquire/release 语义确保值完全发布 / Acquire/release semantics ensure value is fully published</li>
 * </ul>
 *
 * <p><b>适用场景 / When to use:</b> 生产者远多于消费者且 {@code ConcurrentLinkedQueue} 争用
 * 是主要开销时 / When producers vastly outnumber consumers and CLQ contention dominates.
 *
 * <p><b>不适用 / When NOT to use:</b> 多消费者、无界缓冲或严格 FIFO / Multi-consumer,
 * unbounded buffering, or strict FIFO across more than one consumer.
 *
 * @param <E> 事件类型 / Event type
 */
public final class MiliRingBuffer<E> {
    private final int mask;
    private final VarHandle headHandle;
    private final VarHandle tailHandle;
    @SuppressWarnings("unused")
    private volatile long producerHead; // 对生产者可见 / Visible to producers
    @SuppressWarnings("unused")
    private volatile long consumerTail; // 对消费者可见 / Visible to consumer

    /**
     * 槽位状态机 / Slot state machine.
     * state=0 表示空 (可写)，state=1 表示满 (可读) / state=0 empty (writable), state=1 full (readable).
     */
    private static final class Slot<E> {
        volatile int state;
        E value;

        Slot() {
            this.state = 0;
        }
    }

    private final Slot<E>[] slotArray;

    @SuppressWarnings("unchecked")
    public MiliRingBuffer(int capacity) {
        if (capacity < 2 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("容量必须是 2 的幂且 >= 2 / capacity must be a power of two >= 2, got " + capacity);
        }
        this.mask = capacity - 1;
        this.slotArray = (Slot<E>[]) new Slot<?>[capacity];
        for (int i = 0; i < capacity; i++) {
            slotArray[i] = new Slot<>();
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            this.headHandle = lookup.findVarHandle(MiliRingBuffer.class, "producerHead", long.class);
            this.tailHandle = lookup.findVarHandle(MiliRingBuffer.class, "consumerTail", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        this.producerHead = 0L;
        this.consumerTail = 0L;
    }

    public int capacity() {
        return slotArray.length;
    }

    /**
     * 非阻塞入队 / Best-effort, non-blocking enqueue.
     *
     * @return true 如果成功添加 / true if value was added; false 如果缓冲区已满 / false if full.
     *         任意线程均可安全调用 / Safe to call from any thread.
     */
    public boolean offer(E value) {
        Objects.requireNonNull(value, "value");
        long head;
        long tail;
        int idx;
        do {
            head = (long) headHandle.getAcquire(this);
            tail = (long) tailHandle.getAcquire(this);
            if (head - tail >= slotArray.length) {
                return false; // 已满 / full
            }
            idx = (int) (head & mask);
        } while (!headHandle.compareAndSet(this, head, head + 1L));

        // CAS 成功后 "拥有" 该槽位，发布值然后标记为满
        // After CAS success we "own" the slot. Publish value, then mark full.
        Slot<E> slot = slotArray[idx];
        slot.value = value;
        // release 栅栏: 确保 value 写入在 state=1 之前对消费者可见
        // Release fence: ensure value write is visible before state=1
        VarHandle.releaseFence();
        slot.state = 1;
        return true;
    }

    /**
     * 批量出队到消费者 / Drain up to {@code max} elements into {@code sink}.
     *
     * @return 实际出队的元素数量 / Number actually drained. 0 表示缓冲区为空 / 0 if empty.
     */
    @SuppressWarnings("unchecked")
    public int drainTo(java.util.function.Consumer<? super E> sink, int max) {
        int drained = 0;
        while (drained < max) {
            long tail = (long) tailHandle.getAcquire(this);
            int idx = (int) (tail & mask);
            Slot<E> slot = slotArray[idx];
            if (slot.state == 0) {
                // acquire 栅栏确保看到最新的 state 和 value
                // Acquire fence to see latest state and value
                VarHandle.acquireFence();
                if (slot.state == 0) {
                    break; // 确实为空 / truly empty
                }
            }
            // 此时 state=1 且 acquire 栅栏已过，value 可见
            // At this point state=1 and acquire fence passed, value is visible
            E value = slot.value;
            slot.value = null;  // 清除引用帮助 GC / clear reference for GC
            slot.state = 0;     // 标记为空 / mark as empty
            // release 栅栏将槽位发布给生产者 / Release the slot to producers
            tailHandle.setRelease(this, tail + 1L);
            sink.accept(value);
            drained++;
        }
        return drained;
    }

    /** 当前缓冲的元素数量 / Current number of buffered elements. */
    public int size() {
        long h = (long) headHandle.getAcquire(this);
        long t = (long) tailHandle.getAcquire(this);
        return (int) (h - t);
    }

    /** 缓冲区是否为空 / Whether the buffer is empty. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * 清空缓冲区 (仅限消费者线程调用) / Clear the buffer (consumer thread only).
     * 将丢失所有未消费的元素 / All unconsumed elements will be lost.
     */
    public void clear() {
        drainTo(e -> {}, Integer.MAX_VALUE);
    }

    /**
     * 查看队首元素但不出队 (仅限消费者线程) / Peek at head without dequeuing (consumer thread only).
     *
     * @return 队首元素，空时返回 null / Head element, or null if empty.
     */
    public E peek() {
        long tail = (long) tailHandle.getAcquire(this);
        int idx = (int) (tail & mask);
        Slot<E> slot = slotArray[idx];
        if (slot.state == 0) {
            // acquire 栅栏后二次检查 / Double-check after acquire fence
            VarHandle.acquireFence();
            if (slot.state == 0) return null;
        } else {
            // state!=0 时仍需 acquire 栅栏确保看到生产者写入的 value
            // Acquire fence needed even when state!=0 to see producer's value write
            VarHandle.acquireFence();
        }
        return slot.value;
    }
}
