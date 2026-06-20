package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.BitSet;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class LastSeenMessagesTracker {
    private final @Nullable LastSeenTrackedEntry[] trackedMessages;
    private int tail;
    private int offset;
    private @Nullable MessageSignature lastTrackedMessage;

    public LastSeenMessagesTracker(int size) {
        this.trackedMessages = new LastSeenTrackedEntry[size];
    }

    public boolean addPending(MessageSignature signature, boolean acknowledged) {
        if (Objects.equals(signature, this.lastTrackedMessage)) {
            return false;
        } else {
            this.lastTrackedMessage = signature;
            this.addEntry(acknowledged ? new LastSeenTrackedEntry(signature, true) : null);
            return true;
        }
    }

    private void addEntry(@Nullable LastSeenTrackedEntry entry) {
        int i = this.tail;
        this.tail = (i + 1) % this.trackedMessages.length;
        this.offset++;
        this.trackedMessages[i] = entry;
    }

    public void ignorePending(MessageSignature signature) {
        for (int i = 0; i < this.trackedMessages.length; i++) {
            LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages[i];
            if (lastSeenTrackedEntry != null && lastSeenTrackedEntry.pending() && signature.equals(lastSeenTrackedEntry.signature())) {
                this.trackedMessages[i] = null;
                break;
            }
        }
    }

    public int getAndClearOffset() {
        int i = this.offset;
        this.offset = 0;
        return i;
    }

    public LastSeenMessagesTracker.Update generateAndApplyUpdate() {
        int andClearOffset = this.getAndClearOffset();
        BitSet bitSet = new BitSet(this.trackedMessages.length);
        ObjectList<MessageSignature> list = new ObjectArrayList<>(this.trackedMessages.length);

        for (int i = 0; i < this.trackedMessages.length; i++) {
            int i1 = (this.tail + i) % this.trackedMessages.length;
            LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages[i1];
            if (lastSeenTrackedEntry != null) {
                bitSet.set(i, true);
                list.add(lastSeenTrackedEntry.signature());
                this.trackedMessages[i1] = lastSeenTrackedEntry.acknowledge();
            }
        }

        LastSeenMessages lastSeenMessages = new LastSeenMessages(list);
        LastSeenMessages.Update update = new LastSeenMessages.Update(andClearOffset, bitSet, lastSeenMessages.computeChecksum());
        return new LastSeenMessagesTracker.Update(lastSeenMessages, update);
    }

    public int offset() {
        return this.offset;
    }

    public record Update(LastSeenMessages lastSeen, LastSeenMessages.Update update) {
    }
}
