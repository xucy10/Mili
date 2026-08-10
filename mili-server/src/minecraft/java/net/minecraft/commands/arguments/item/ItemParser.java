package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;

public class ItemParser {
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        itemId -> Component.translatableEscape("argument.item.id.invalid", itemId)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        itemTag -> Component.translatableEscape("arguments.item.component.unknown", itemTag)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (component, value) -> Component.translatableEscape("arguments.item.component.malformed", component, value)
    );
    static final SimpleCommandExceptionType ERROR_EXPECTED_COMPONENT = new SimpleCommandExceptionType(
        Component.translatable("arguments.item.component.expected")
    );
    static final DynamicCommandExceptionType ERROR_REPEATED_COMPONENT = new DynamicCommandExceptionType(
        component -> Component.translatableEscape("arguments.item.component.repeated", component)
    );
    private static final DynamicCommandExceptionType ERROR_MALFORMED_ITEM = new DynamicCommandExceptionType(
        item -> Component.translatableEscape("arguments.item.malformed", item)
    );
    public static final char SYNTAX_START_COMPONENTS = '[';
    public static final char SYNTAX_END_COMPONENTS = ']';
    public static final char SYNTAX_COMPONENT_SEPARATOR = ',';
    public static final char SYNTAX_COMPONENT_ASSIGNMENT = '=';
    public static final char SYNTAX_REMOVED_COMPONENT = '!';
    static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    final HolderLookup.RegistryLookup<Item> items;
    final RegistryOps<Tag> registryOps;
    final TagParser<Tag> tagParser;

    public ItemParser(HolderLookup.Provider registries) {
        this.items = registries.lookupOrThrow(Registries.ITEM);
        this.registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        this.tagParser = TagParser.create(this.registryOps);
    }

    public ItemParser.ItemResult parse(StringReader reader) throws CommandSyntaxException {
        final MutableObject<Holder<Item>> mutableObject = new MutableObject<>();
        final DataComponentPatch.Builder builder = DataComponentPatch.builder();
        this.parse(reader, new ItemParser.Visitor() {
            @Override
            public void visitItem(Holder<Item> item) {
                mutableObject.setValue(item);
            }

            @Override
            public <T> void visitComponent(DataComponentType<T> component, T value) {
                builder.set(component, value);
            }

            @Override
            public <T> void visitRemovedComponent(DataComponentType<T> component) {
                builder.remove(component);
            }
        });
        Holder<Item> holder = Objects.requireNonNull(mutableObject.get(), "Parser gave no item");
        DataComponentPatch dataComponentPatch = builder.build();
        validateComponents(reader, holder, dataComponentPatch);
        return new ItemParser.ItemResult(holder, dataComponentPatch);
    }

    private static void validateComponents(StringReader reader, Holder<Item> item, DataComponentPatch components) throws CommandSyntaxException {
        DataComponentMap dataComponentMap = PatchedDataComponentMap.fromPatch(item.value().components(), components);
        DataResult<Unit> dataResult = ItemStack.validateComponents(dataComponentMap);
        dataResult.getOrThrow(value -> ERROR_MALFORMED_ITEM.createWithContext(reader, value));
    }

    public void parse(StringReader reader, ItemParser.Visitor visitor) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        try {
            new ItemParser.State(reader, visitor).parse();
        } catch (CommandSyntaxException var5) {
            reader.setCursor(cursor);
            throw var5;
        }
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        ItemParser.SuggestionsVisitor suggestionsVisitor = new ItemParser.SuggestionsVisitor();
        ItemParser.State state = new ItemParser.State(stringReader, suggestionsVisitor);

        try {
            state.parse();
        } catch (CommandSyntaxException var6) {
        }

        return suggestionsVisitor.resolveSuggestions(builder, stringReader);
    }

    public record ItemResult(Holder<Item> item, DataComponentPatch components) {
    }

    class State {
        private final StringReader reader;
        private final ItemParser.Visitor visitor;

        State(final StringReader reader, final ItemParser.Visitor visitor) {
            this.reader = reader;
            this.visitor = visitor;
        }

        public void parse() throws CommandSyntaxException {
            this.visitor.visitSuggestions(this::suggestItem);
            this.readItem();
            this.visitor.visitSuggestions(this::suggestStartComponents);
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                this.readComponents();
            }
        }

        private void readItem() throws CommandSyntaxException {
            int cursor = this.reader.getCursor();
            Identifier identifier = Identifier.read(this.reader);
            this.visitor.visitItem(ItemParser.this.items.get(ResourceKey.create(Registries.ITEM, identifier)).orElseThrow(() -> {
                this.reader.setCursor(cursor);
                return ItemParser.ERROR_UNKNOWN_ITEM.createWithContext(this.reader, identifier);
            }));
        }

        private void readComponents() throws CommandSyntaxException {
            this.reader.expect('[');
            this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
            Set<DataComponentType<?>> set = new ReferenceArraySet<>();

            while (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                if (this.reader.canRead() && this.reader.peek() == '!') {
                    this.reader.skip();
                    this.visitor.visitSuggestions(this::suggestComponent);
                    DataComponentType<?> componentType = readComponentType(this.reader);
                    if (!set.add(componentType)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(componentType);
                    }

                    this.visitor.visitRemovedComponent(componentType);
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                } else {
                    DataComponentType<?> componentType = readComponentType(this.reader);
                    if (!set.add(componentType)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(componentType);
                    }

                    this.visitor.visitSuggestions(this::suggestAssignment);
                    this.reader.skipWhitespace();
                    this.reader.expect('=');
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                    this.readComponent(ItemParser.this.tagParser, ItemParser.this.registryOps, componentType);
                    this.reader.skipWhitespace();
                }

                this.visitor.visitSuggestions(this::suggestNextOrEndComponents);
                if (!this.reader.canRead() || this.reader.peek() != ',') {
                    break;
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
                if (!this.reader.canRead()) {
                    throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(this.reader);
                }
            }

            this.reader.expect(']');
            this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
        }

        public static DataComponentType<?> readComponentType(StringReader reader) throws CommandSyntaxException {
            if (!reader.canRead()) {
                throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(reader);
            } else {
                int cursor = reader.getCursor();
                Identifier identifier = Identifier.read(reader);
                DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);
                if (dataComponentType != null && !dataComponentType.isTransient()) {
                    return dataComponentType;
                } else {
                    reader.setCursor(cursor);
                    throw ItemParser.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, identifier);
                }
            }
        }

        private <T, O> void readComponent(TagParser<O> tagParser, RegistryOps<O> ops, DataComponentType<T> component) throws CommandSyntaxException {
            int cursor = this.reader.getCursor();
            O object = tagParser.parseAsArgument(this.reader);
            DataResult<T> dataResult = component.codecOrThrow().parse(ops, object);
            this.visitor.visitComponent(component, dataResult.getOrThrow(string -> {
                this.reader.setCursor(cursor);
                return ItemParser.ERROR_MALFORMED_COMPONENT.createWithContext(this.reader, component.toString(), string);
            }));
        }

        private CompletableFuture<Suggestions> suggestStartComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestNextOrEndComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf(','));
                builder.suggest(String.valueOf(']'));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestAssignment(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('='));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
            return SharedSuggestionProvider.suggestResource(ItemParser.this.items.listElementIds().map(ResourceKey::identifier), builder);
        }

        private CompletableFuture<Suggestions> suggestComponentAssignmentOrRemoval(SuggestionsBuilder builder) {
            builder.suggest(String.valueOf('!'));
            return this.suggestComponent(builder, String.valueOf('='));
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder) {
            return this.suggestComponent(builder, "");
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder, String suffix) {
            String string = builder.getRemaining().toLowerCase(Locale.ROOT);
            SharedSuggestionProvider.filterResources(BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet(), string, entry -> entry.getKey().identifier(), entry -> {
                DataComponentType<?> dataComponentType = entry.getValue();
                if (dataComponentType.codec() != null) {
                    Identifier identifier = entry.getKey().identifier();
                    builder.suggest(identifier + suffix);
                }
            });
            return builder.buildFuture();
        }
    }

    static class SuggestionsVisitor implements ItemParser.Visitor {
        private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions = ItemParser.SUGGEST_NOTHING;

        @Override
        public void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions) {
            this.suggestions = suggestions;
        }

        public CompletableFuture<Suggestions> resolveSuggestions(SuggestionsBuilder builder, StringReader reader) {
            return this.suggestions.apply(builder.createOffset(reader.getCursor()));
        }
    }

    public interface Visitor {
        default void visitItem(Holder<Item> item) {
        }

        default <T> void visitComponent(DataComponentType<T> component, T value) {
        }

        default <T> void visitRemovedComponent(DataComponentType<T> component) {
        }

        default void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions) {
        }
    }
}
