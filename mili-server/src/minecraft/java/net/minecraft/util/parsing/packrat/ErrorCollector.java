package net.minecraft.util.parsing.packrat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public interface ErrorCollector<S> {
    void store(int cursor, SuggestionSupplier<S> suggestions, Object reason);

    default void store(int cursor, Object reason) {
        this.store(cursor, SuggestionSupplier.empty(), reason);
    }

    void finish(int cursor);

    public static class LongestOnly<S> implements ErrorCollector<S> {
        private ErrorCollector.LongestOnly.@Nullable MutableErrorEntry<S>[] entries = new ErrorCollector.LongestOnly.MutableErrorEntry[16];
        private int nextErrorEntry;
        private int lastCursor = -1;

        private void discardErrorsFromShorterParse(int cursor) {
            if (cursor > this.lastCursor) {
                this.lastCursor = cursor;
                this.nextErrorEntry = 0;
            }
        }

        @Override
        public void finish(int cursor) {
            this.discardErrorsFromShorterParse(cursor);
        }

        @Override
        public void store(int cursor, SuggestionSupplier<S> suggestions, Object reason) {
            this.discardErrorsFromShorterParse(cursor);
            if (cursor == this.lastCursor) {
                this.addErrorEntry(suggestions, reason);
            }
        }

        private void addErrorEntry(SuggestionSupplier<S> suggestions, Object reason) {
            int i = this.entries.length;
            if (this.nextErrorEntry >= i) {
                int i1 = Util.growByHalf(i, this.nextErrorEntry + 1);
                ErrorCollector.LongestOnly.MutableErrorEntry<S>[] mutableErrorEntrys = new ErrorCollector.LongestOnly.MutableErrorEntry[i1];
                System.arraycopy(this.entries, 0, mutableErrorEntrys, 0, i);
                this.entries = mutableErrorEntrys;
            }

            int i1 = this.nextErrorEntry++;
            ErrorCollector.LongestOnly.MutableErrorEntry<S> mutableErrorEntry = this.entries[i1];
            if (mutableErrorEntry == null) {
                mutableErrorEntry = new ErrorCollector.LongestOnly.MutableErrorEntry<>();
                this.entries[i1] = mutableErrorEntry;
            }

            mutableErrorEntry.suggestions = suggestions;
            mutableErrorEntry.reason = reason;
        }

        public List<ErrorEntry<S>> entries() {
            int i = this.nextErrorEntry;
            if (i == 0) {
                return List.of();
            } else {
                List<ErrorEntry<S>> list = new ArrayList<>(i);

                for (int i1 = 0; i1 < i; i1++) {
                    ErrorCollector.LongestOnly.MutableErrorEntry<S> mutableErrorEntry = this.entries[i1];
                    list.add(new ErrorEntry<>(this.lastCursor, mutableErrorEntry.suggestions, mutableErrorEntry.reason));
                }

                return list;
            }
        }

        public int cursor() {
            return this.lastCursor;
        }

        static class MutableErrorEntry<S> {
            SuggestionSupplier<S> suggestions = SuggestionSupplier.empty();
            Object reason = "empty";
        }
    }

    public static class Nop<S> implements ErrorCollector<S> {
        @Override
        public void store(int cursor, SuggestionSupplier<S> suggestions, Object reason) {
        }

        @Override
        public void finish(int cursor) {
        }
    }
}
