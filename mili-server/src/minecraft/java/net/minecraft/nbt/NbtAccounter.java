package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;

public class NbtAccounter {
    public static final int DEFAULT_NBT_QUOTA = 2097152;
    public static final int UNCOMPRESSED_NBT_QUOTA = 104857600;
    private static final int MAX_STACK_DEPTH = 512;
    private final long quota;
    private long usage;
    private final int maxDepth;
    private int depth;

    public NbtAccounter(long quota, int maxDepth) {
        this.quota = quota;
        this.maxDepth = maxDepth;
    }

    public static NbtAccounter create(long quota) {
        return new NbtAccounter(quota, MAX_STACK_DEPTH);
    }

    public static NbtAccounter defaultQuota() {
        return new NbtAccounter(DEFAULT_NBT_QUOTA, MAX_STACK_DEPTH);
    }

    public static NbtAccounter uncompressedQuota() {
        return new NbtAccounter(UNCOMPRESSED_NBT_QUOTA, MAX_STACK_DEPTH);
    }

    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter(Long.MAX_VALUE, MAX_STACK_DEPTH);
    }

    public void accountBytes(long bytesPerItem, long items) {
        this.accountBytes(bytesPerItem * items);
    }

    public void accountBytes(long bytes) {
        if (bytes < 0L) {
            throw new IllegalArgumentException("Tried to account NBT tag with negative size: " + bytes);
        } else if (this.usage + bytes > this.quota) {
            throw new NbtAccounterException(
                "Tried to read NBT tag that was too big; tried to allocate: " + this.usage + " + " + bytes + " bytes where max allowed: " + this.quota
            );
        } else {
            this.usage += bytes;
        }
    }

    public void pushDepth() {
        if (this.depth >= this.maxDepth) {
            throw new NbtAccounterException("Tried to read NBT tag with too high complexity, depth > " + this.maxDepth);
        } else {
            this.depth++;
        }
    }

    public void popDepth() {
        if (this.depth <= 0) {
            throw new NbtAccounterException("NBT-Accounter tried to pop stack-depth at top-level");
        } else {
            this.depth--;
        }
    }

    @VisibleForTesting
    public long getUsage() {
        return this.usage;
    }

    @VisibleForTesting
    public int getDepth() {
        return this.depth;
    }
}
