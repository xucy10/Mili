package net.minecraft.server.network;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TextFilter {
    TextFilter DUMMY = new TextFilter() {
        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String text) {
            return CompletableFuture.completedFuture(FilteredText.passThrough(text));
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts) {
            return CompletableFuture.completedFuture(texts.stream().map(FilteredText::passThrough).collect(ImmutableList.toImmutableList()));
        }
    };

    default void join() {
    }

    default void leave() {
    }

    CompletableFuture<FilteredText> processStreamMessage(String text);

    CompletableFuture<List<FilteredText>> processMessageBundle(List<String> texts);
}
