package net.minecraft.commands;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;

public interface SharedSuggestionProvider extends PermissionSetSupplier {
    CharMatcher MATCH_SPLITTER = CharMatcher.anyOf("._/");

    Collection<String> getOnlinePlayerNames();

    default Collection<String> getCustomTabSugggestions() {
        return this.getOnlinePlayerNames();
    }

    default Collection<String> getSelectedEntities() {
        return Collections.emptyList();
    }

    Collection<String> getAllTeams();

    Stream<Identifier> getAvailableSounds();

    CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context);

    default Collection<SharedSuggestionProvider.TextCoordinates> getRelevantCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    default Collection<SharedSuggestionProvider.TextCoordinates> getAbsoluteCoordinates() {
        return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
    }

    Set<ResourceKey<Level>> levels();

    RegistryAccess registryAccess();

    FeatureFlagSet enabledFeatures();

    default void suggestRegistryElements(HolderLookup<?> lookup, SharedSuggestionProvider.ElementSuggestionType type, SuggestionsBuilder builder) {
        if (type.shouldSuggestTags()) {
            suggestResource(lookup.listTagIds().map(TagKey::location), builder, "#");
        }

        if (type.shouldSuggestElements()) {
            suggestResource(lookup.listElementIds().map(ResourceKey::identifier), builder);
        }
    }

    static <S> CompletableFuture<Suggestions> listSuggestions(
        CommandContext<S> context,
        SuggestionsBuilder builder,
        ResourceKey<? extends Registry<?>> registryKey,
        SharedSuggestionProvider.ElementSuggestionType type
    ) {
        return context.getSource() instanceof SharedSuggestionProvider sharedSuggestionProvider
            ? sharedSuggestionProvider.suggestRegistryElements(registryKey, type, builder, context)
            : builder.buildFuture();
    }

    CompletableFuture<Suggestions> suggestRegistryElements(
        ResourceKey<? extends Registry<?>> registryKey,
        SharedSuggestionProvider.ElementSuggestionType type,
        SuggestionsBuilder builder,
        CommandContext<?> context
    );

    static <T> void filterResources(Iterable<T> resources, String input, Function<T, Identifier> locationFunction, Consumer<T> resourceConsumer) {
        boolean flag = input.indexOf(58) > -1;

        for (T object : resources) {
            Identifier identifier = locationFunction.apply(object);
            if (flag) {
                String string = identifier.toString();
                if (matchesSubStr(input, string)) {
                    resourceConsumer.accept(object);
                }
            } else if (matchesSubStr(input, identifier.getNamespace()) || matchesSubStr(input, identifier.getPath())) {
                resourceConsumer.accept(object);
            }
        }
    }

    static <T> void filterResources(
        Iterable<T> resources, String remaining, String prefix, Function<T, Identifier> locationFunction, Consumer<T> resourceConsumer
    ) {
        if (remaining.isEmpty()) {
            resources.forEach(resourceConsumer);
        } else {
            String string = Strings.commonPrefix(remaining, prefix);
            if (!string.isEmpty()) {
                String sub = remaining.substring(string.length());
                filterResources(resources, sub, locationFunction, resourceConsumer);
            }
        }
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> resources, SuggestionsBuilder builder, String prefix) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, string, prefix, identifier -> identifier, identifier -> builder.suggest(prefix + identifier));
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> resources, SuggestionsBuilder builder, String prefix) {
        return suggestResource(resources::iterator, builder, prefix);
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> resources, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(resources, string, identifier -> identifier, identifier -> builder.suggest(identifier.toString()));
        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, Identifier> locationFunction, Function<T, Message> suggestionFunction
    ) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        filterResources(
            resources, string, locationFunction, object -> builder.suggest(locationFunction.apply(object).toString(), suggestionFunction.apply(object))
        );
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> resourceLocations, SuggestionsBuilder builder) {
        return suggestResource(resourceLocations::iterator, builder);
    }

    static <T> CompletableFuture<Suggestions> suggestResource(
        Stream<T> resources, SuggestionsBuilder builder, Function<T, Identifier> locationFunction, Function<T, Message> suggestionFunction
    ) {
        return suggestResource(resources::iterator, builder, locationFunction, suggestionFunction);
    }

    static CompletableFuture<Suggestions> suggestCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates textCoordinates : coordinates) {
                String string = textCoordinates.x + " " + textCoordinates.y + " " + textCoordinates.z;
                if (validator.test(string)) {
                    list.add(textCoordinates.x);
                    list.add(textCoordinates.x + " " + textCoordinates.y);
                    list.add(string);
                }
            }
        } else {
            String[] parts = remaining.split(" ");
            if (parts.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates1 : coordinates) {
                    String string1 = parts[0] + " " + textCoordinates1.y + " " + textCoordinates1.z;
                    if (validator.test(string1)) {
                        list.add(parts[0] + " " + textCoordinates1.y);
                        list.add(string1);
                    }
                }
            } else if (parts.length == 2) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates1x : coordinates) {
                    String string1 = parts[0] + " " + parts[1] + " " + textCoordinates1x.z;
                    if (validator.test(string1)) {
                        list.add(string1);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest2DCoordinates(
        String remaining, Collection<SharedSuggestionProvider.TextCoordinates> coordinates, SuggestionsBuilder builder, Predicate<String> validator
    ) {
        List<String> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(remaining)) {
            for (SharedSuggestionProvider.TextCoordinates textCoordinates : coordinates) {
                String string = textCoordinates.x + " " + textCoordinates.z;
                if (validator.test(string)) {
                    list.add(textCoordinates.x);
                    list.add(string);
                }
            }
        } else {
            String[] parts = remaining.split(" ");
            if (parts.length == 1) {
                for (SharedSuggestionProvider.TextCoordinates textCoordinates1 : coordinates) {
                    String string1 = parts[0] + " " + textCoordinates1.z;
                    if (validator.test(string1)) {
                        list.add(string1);
                    }
                }
            }
        }

        return suggest(list, builder);
    }

    static CompletableFuture<Suggestions> suggest(Iterable<String> strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String string1 : strings) {
            if (matchesSubStr(string, string1.toLowerCase(Locale.ROOT))) {
                builder.suggest(string1);
            }
        }

        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        strings.filter(string1 -> matchesSubStr(string, string1.toLowerCase(Locale.ROOT))).forEach(builder::suggest);
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(String[] strings, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String string1 : strings) {
            if (matchesSubStr(string, string1.toLowerCase(Locale.ROOT))) {
                builder.suggest(string1);
            }
        }

        return builder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggest(
        Iterable<T> resources, SuggestionsBuilder builder, Function<T, String> stringFunction, Function<T, Message> suggestionFunction
    ) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (T object : resources) {
            String string1 = stringFunction.apply(object);
            if (matchesSubStr(string, string1.toLowerCase(Locale.ROOT))) {
                builder.suggest(string1, suggestionFunction.apply(object));
            }
        }

        return builder.buildFuture();
    }

    static boolean matchesSubStr(String input, String substring) {
        int i = 0;

        while (!substring.startsWith(input, i)) {
            int i1 = MATCH_SPLITTER.indexIn(substring, i);
            if (i1 < 0) {
                return false;
            }

            i = i1 + 1;
        }

        return true;
    }

    public static enum ElementSuggestionType {
        TAGS,
        ELEMENTS,
        ALL;

        public boolean shouldSuggestTags() {
            return this == TAGS || this == ALL;
        }

        public boolean shouldSuggestElements() {
            return this == ELEMENTS || this == ALL;
        }
    }

    public static class TextCoordinates {
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_LOCAL = new SharedSuggestionProvider.TextCoordinates("^", "^", "^");
        public static final SharedSuggestionProvider.TextCoordinates DEFAULT_GLOBAL = new SharedSuggestionProvider.TextCoordinates("~", "~", "~");
        public final String x;
        public final String y;
        public final String z;

        public TextCoordinates(String x, String y, String z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
