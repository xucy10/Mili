package net.minecraft.server;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Queue;
import net.minecraft.util.ArrayListDeque;

public class SuppressedExceptionCollector {
    private static final int LATEST_ENTRY_COUNT = 8;
    private final Queue<SuppressedExceptionCollector.LongEntry> latestEntries = new ArrayListDeque<>();
    private final Object2IntLinkedOpenHashMap<SuppressedExceptionCollector.ShortEntry> entryCounts = new Object2IntLinkedOpenHashMap<>();

    private static long currentTimeMs() {
        return System.currentTimeMillis();
    }

    public synchronized void addEntry(String id, Throwable throwable) {
        long l = currentTimeMs();
        String message = throwable.getMessage();
        this.latestEntries.add(new SuppressedExceptionCollector.LongEntry(l, id, (Class<? extends Throwable>)throwable.getClass(), message));

        while (this.latestEntries.size() > 8) {
            this.latestEntries.remove();
        }

        SuppressedExceptionCollector.ShortEntry shortEntry = new SuppressedExceptionCollector.ShortEntry(id, (Class<? extends Throwable>)throwable.getClass());
        int _int = this.entryCounts.getInt(shortEntry);
        this.entryCounts.putAndMoveToFirst(shortEntry, _int + 1);
    }

    public synchronized String dump() {
        long l = currentTimeMs();
        StringBuilder stringBuilder = new StringBuilder();
        if (!this.latestEntries.isEmpty()) {
            stringBuilder.append("\n\t\tLatest entries:\n");

            for (SuppressedExceptionCollector.LongEntry longEntry : this.latestEntries) {
                stringBuilder.append("\t\t\t")
                    .append(longEntry.location)
                    .append(":")
                    .append(longEntry.cls)
                    .append(": ")
                    .append(longEntry.message)
                    .append(" (")
                    .append(l - longEntry.timestampMs)
                    .append("ms ago)")
                    .append("\n");
            }
        }

        if (!this.entryCounts.isEmpty()) {
            if (stringBuilder.isEmpty()) {
                stringBuilder.append("\n");
            }

            stringBuilder.append("\t\tEntry counts:\n");

            for (Entry<SuppressedExceptionCollector.ShortEntry> entry : Object2IntMaps.fastIterable(this.entryCounts)) {
                stringBuilder.append("\t\t\t")
                    .append(entry.getKey().location)
                    .append(":")
                    .append(entry.getKey().cls)
                    .append(" x ")
                    .append(entry.getIntValue())
                    .append("\n");
            }
        }

        return stringBuilder.isEmpty() ? "~~NONE~~" : stringBuilder.toString();
    }

    record LongEntry(long timestampMs, String location, Class<? extends Throwable> cls, String message) {
    }

    record ShortEntry(String location, Class<? extends Throwable> cls) {
    }
}
