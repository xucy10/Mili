package net.minecraft.commands.arguments.blocks;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public class BlockStateParser {
    public static final SimpleCommandExceptionType ERROR_NO_TAGS_ALLOWED = new SimpleCommandExceptionType(
        Component.translatable("argument.block.tag.disallowed")
    );
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_BLOCK = new DynamicCommandExceptionType(
        block -> Component.translatableEscape("argument.block.id.invalid", block)
    );
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_PROPERTY = new Dynamic2CommandExceptionType(
        (block, property) -> Component.translatableEscape("argument.block.property.unknown", block, property)
    );
    public static final Dynamic2CommandExceptionType ERROR_DUPLICATE_PROPERTY = new Dynamic2CommandExceptionType(
        (property, block) -> Component.translatableEscape("argument.block.property.duplicate", block, property)
    );
    public static final Dynamic3CommandExceptionType ERROR_INVALID_VALUE = new Dynamic3CommandExceptionType(
        (block, value, property) -> Component.translatableEscape("argument.block.property.invalid", block, property, value)
    );
    public static final Dynamic2CommandExceptionType ERROR_EXPECTED_VALUE = new Dynamic2CommandExceptionType(
        (property, block) -> Component.translatableEscape("argument.block.property.novalue", block, property)
    );
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_PROPERTIES = new SimpleCommandExceptionType(
        Component.translatable("argument.block.property.unclosed")
    );
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        blockTag -> Component.translatableEscape("arguments.block.tag.unknown", blockTag)
    );
    private static final char SYNTAX_START_PROPERTIES = '[';
    private static final char SYNTAX_START_NBT = '{';
    private static final char SYNTAX_END_PROPERTIES = ']';
    private static final char SYNTAX_EQUALS = '=';
    private static final char SYNTAX_PROPERTY_SEPARATOR = ',';
    private static final char SYNTAX_TAG = '#';
    private static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    private final HolderLookup<Block> blocks;
    private final StringReader reader;
    private final boolean forTesting;
    private final boolean allowNbt;
    private final Map<Property<?>, Comparable<?>> properties = Maps.newLinkedHashMap(); // CraftBukkit - stable
    private final Map<String, String> vagueProperties = Maps.newHashMap();
    private Identifier id = Identifier.withDefaultNamespace("");
    private @Nullable StateDefinition<Block, BlockState> definition;
    private @Nullable BlockState state;
    private @Nullable CompoundTag nbt;
    private @Nullable HolderSet<Block> tag;
    private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions = SUGGEST_NOTHING;

    private BlockStateParser(HolderLookup<Block> blocks, StringReader reader, boolean forTesting, boolean allowNbt) {
        this.blocks = blocks;
        this.reader = reader;
        this.forTesting = forTesting;
        this.allowNbt = allowNbt;
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> lookup, String input, boolean allowNbt) throws CommandSyntaxException {
        return parseForBlock(lookup, new StringReader(input), allowNbt);
    }

    public static BlockStateParser.BlockResult parseForBlock(HolderLookup<Block> lookup, StringReader reader, boolean allowNbt) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        try {
            BlockStateParser blockStateParser = new BlockStateParser(lookup, reader, false, allowNbt);
            blockStateParser.parse();
            return new BlockStateParser.BlockResult(blockStateParser.state, blockStateParser.properties, blockStateParser.nbt);
        } catch (CommandSyntaxException var5) {
            reader.setCursor(cursor);
            throw var5;
        }
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(HolderLookup<Block> lookup, String input, boolean allowNbt) throws CommandSyntaxException {
        return parseForTesting(lookup, new StringReader(input), allowNbt);
    }

    public static Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parseForTesting(
        HolderLookup<Block> lookup, StringReader reader, boolean allowNbt
    ) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        try {
            BlockStateParser blockStateParser = new BlockStateParser(lookup, reader, true, allowNbt);
            blockStateParser.parse();
            return blockStateParser.tag != null
                ? Either.right(new BlockStateParser.TagResult(blockStateParser.tag, blockStateParser.vagueProperties, blockStateParser.nbt))
                : Either.left(new BlockStateParser.BlockResult(blockStateParser.state, blockStateParser.properties, blockStateParser.nbt));
        } catch (CommandSyntaxException var5) {
            reader.setCursor(cursor);
            throw var5;
        }
    }

    public static CompletableFuture<Suggestions> fillSuggestions(HolderLookup<Block> lookup, SuggestionsBuilder builder, boolean forTesting, boolean allowNbt) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        BlockStateParser blockStateParser = new BlockStateParser(lookup, stringReader, forTesting, allowNbt);

        try {
            blockStateParser.parse();
        } catch (CommandSyntaxException var7) {
        }

        return blockStateParser.suggestions.apply(builder.createOffset(stringReader.getCursor()));
    }

    private void parse() throws CommandSyntaxException {
        if (this.forTesting) {
            this.suggestions = this::suggestBlockIdOrTag;
        } else {
            this.suggestions = this::suggestItem;
        }

        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.readTag();
            this.suggestions = this::suggestOpenVaguePropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readVagueProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        } else {
            this.readBlock();
            this.suggestions = this::suggestOpenPropertiesOrNbt;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.readProperties();
                this.suggestions = this::suggestOpenNbt;
            }
        }

        if (this.allowNbt && this.reader.canRead() && this.reader.peek() == '{') {
            this.suggestions = SUGGEST_NOTHING;
            this.readNbt();
        }
    }

    private CompletableFuture<Suggestions> suggestPropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestPropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyNameOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        return this.suggestVaguePropertyName(builder);
    }

    private CompletableFuture<Suggestions> suggestPropertyName(SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Property<?> property : this.state.getProperties()) {
            if (!this.properties.containsKey(property) && property.getName().startsWith(string)) {
                builder.suggest(property.getName() + "=");
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyName(SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (this.tag != null) {
            for (Holder<Block> holder : this.tag) {
                for (Property<?> property : holder.value().getStateDefinition().getProperties()) {
                    if (!this.vagueProperties.containsKey(property.getName()) && property.getName().startsWith(string)) {
                        builder.suggest(property.getName() + "=");
                    }
                }
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.hasBlockEntity()) {
            builder.suggest(String.valueOf('{'));
        }

        return builder.buildFuture();
    }

    private boolean hasBlockEntity() {
        if (this.state != null) {
            return this.state.hasBlockEntity();
        } else {
            if (this.tag != null) {
                for (Holder<Block> holder : this.tag) {
                    if (holder.value().defaultBlockState().hasBlockEntity()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf('='));
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestNextPropertyOrEnd(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(String.valueOf(']'));
        }

        if (builder.getRemaining().isEmpty() && this.properties.size() < this.state.getProperties().size()) {
            builder.suggest(String.valueOf(','));
        }

        return builder.buildFuture();
    }

    private static <T extends Comparable<T>> SuggestionsBuilder addSuggestions(SuggestionsBuilder builder, Property<T> property) {
        for (T comparable : property.getPossibleValues()) {
            if (comparable instanceof Integer integer) {
                builder.suggest(integer);
            } else {
                builder.suggest(property.getName(comparable));
            }
        }

        return builder;
    }

    private CompletableFuture<Suggestions> suggestVaguePropertyValue(SuggestionsBuilder builder, String propertyName) {
        boolean flag = false;
        if (this.tag != null) {
            for (Holder<Block> holder : this.tag) {
                Block block = holder.value();
                Property<?> property = block.getStateDefinition().getProperty(propertyName);
                if (property != null) {
                    addSuggestions(builder, property);
                }

                if (!flag) {
                    for (Property<?> property1 : block.getStateDefinition().getProperties()) {
                        if (!this.vagueProperties.containsKey(property1.getName())) {
                            flag = true;
                            break;
                        }
                    }
                }
            }
        }

        if (flag) {
            builder.suggest(String.valueOf(','));
        }

        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenVaguePropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty() && this.tag != null) {
            boolean flag = false;
            boolean flag1 = false;

            for (Holder<Block> holder : this.tag) {
                Block block = holder.value();
                flag |= !block.getStateDefinition().getProperties().isEmpty();
                flag1 |= block.defaultBlockState().hasBlockEntity();
                if (flag && flag1) {
                    break;
                }
            }

            if (flag) {
                builder.suggest(String.valueOf('['));
            }

            if (flag1) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenPropertiesOrNbt(SuggestionsBuilder builder) {
        if (builder.getRemaining().isEmpty()) {
            if (!this.definition.getProperties().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            if (this.state.hasBlockEntity()) {
                builder.suggest(String.valueOf('{'));
            }
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTag(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listTagIds().map(TagKey::location), builder, String.valueOf('#'));
    }

    private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(this.blocks.listElementIds().map(ResourceKey::identifier), builder);
    }

    private CompletableFuture<Suggestions> suggestBlockIdOrTag(SuggestionsBuilder builder) {
        this.suggestTag(builder);
        this.suggestItem(builder);
        return builder.buildFuture();
    }

    private void readBlock() throws CommandSyntaxException {
        int cursor = this.reader.getCursor();
        this.id = Identifier.read(this.reader);
        Block block = this.blocks.get(ResourceKey.create(Registries.BLOCK, this.id)).orElseThrow(() -> {
            this.reader.setCursor(cursor);
            return ERROR_UNKNOWN_BLOCK.createWithContext(this.reader, this.id.toString());
        }).value();
        this.definition = block.getStateDefinition();
        this.state = block.defaultBlockState();
    }

    private void readTag() throws CommandSyntaxException {
        if (!this.forTesting) {
            throw ERROR_NO_TAGS_ALLOWED.createWithContext(this.reader);
        } else {
            int cursor = this.reader.getCursor();
            this.reader.expect('#');
            this.suggestions = this::suggestTag;
            Identifier identifier = Identifier.read(this.reader);
            this.tag = this.blocks.get(TagKey.create(Registries.BLOCK, identifier)).orElseThrow(() -> {
                this.reader.setCursor(cursor);
                return ERROR_UNKNOWN_TAG.createWithContext(this.reader, identifier.toString());
            });
        }
    }

    private void readProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestPropertyNameOrEnd;
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != ']') {
            this.reader.skipWhitespace();
            int cursor = this.reader.getCursor();
            String string = this.reader.readString();
            Property<?> property = this.definition.getProperty(string);
            if (property == null) {
                this.reader.setCursor(cursor);
                throw ERROR_UNKNOWN_PROPERTY.createWithContext(this.reader, this.id.toString(), string);
            }

            if (this.properties.containsKey(property)) {
                this.reader.setCursor(cursor);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), string);
            }

            this.reader.skipWhitespace();
            this.suggestions = this::suggestEquals;
            if (!this.reader.canRead() || this.reader.peek() != '=') {
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), string);
            }

            this.reader.skip();
            this.reader.skipWhitespace();
            this.suggestions = builder -> addSuggestions(builder, property).buildFuture();
            int cursor1 = this.reader.getCursor();
            this.setValue(property, this.reader.readString(), cursor1);
            this.suggestions = this::suggestNextPropertyOrEnd;
            this.reader.skipWhitespace();
            if (this.reader.canRead()) {
                if (this.reader.peek() != ',') {
                    if (this.reader.peek() != ']') {
                        throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                    }
                    break;
                }

                this.reader.skip();
                this.suggestions = this::suggestPropertyName;
            }
        }

        if (this.reader.canRead()) {
            this.reader.skip();
        } else {
            throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readVagueProperties() throws CommandSyntaxException {
        this.reader.skip();
        this.suggestions = this::suggestVaguePropertyNameOrEnd;
        int i = -1;
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != ']') {
            this.reader.skipWhitespace();
            int cursor = this.reader.getCursor();
            String string = this.reader.readString();
            if (this.vagueProperties.containsKey(string)) {
                this.reader.setCursor(cursor);
                throw ERROR_DUPLICATE_PROPERTY.createWithContext(this.reader, this.id.toString(), string);
            }

            this.reader.skipWhitespace();
            if (!this.reader.canRead() || this.reader.peek() != '=') {
                this.reader.setCursor(cursor);
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader, this.id.toString(), string);
            }

            this.reader.skip();
            this.reader.skipWhitespace();
            this.suggestions = builder -> this.suggestVaguePropertyValue(builder, string);
            i = this.reader.getCursor();
            String string1 = this.reader.readString();
            this.vagueProperties.put(string, string1);
            this.reader.skipWhitespace();
            if (this.reader.canRead()) {
                i = -1;
                if (this.reader.peek() != ',') {
                    if (this.reader.peek() != ']') {
                        throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
                    }
                    break;
                }

                this.reader.skip();
                this.suggestions = this::suggestVaguePropertyName;
            }
        }

        if (this.reader.canRead()) {
            this.reader.skip();
        } else {
            if (i >= 0) {
                this.reader.setCursor(i);
            }

            throw ERROR_EXPECTED_END_OF_PROPERTIES.createWithContext(this.reader);
        }
    }

    private void readNbt() throws CommandSyntaxException {
        this.nbt = TagParser.parseCompoundAsArgument(this.reader);
    }

    private <T extends Comparable<T>> void setValue(Property<T> property, String value, int valuePosition) throws CommandSyntaxException {
        Optional<T> value1 = property.getValue(value);
        if (value1.isPresent()) {
            this.state = this.state.setValue(property, value1.get());
            this.properties.put(property, value1.get());
        } else {
            this.reader.setCursor(valuePosition);
            throw ERROR_INVALID_VALUE.createWithContext(this.reader, this.id.toString(), property.getName(), value);
        }
    }

    public static String serialize(BlockState state) {
        StringBuilder stringBuilder = new StringBuilder(state.getBlockHolder().unwrapKey().map(blockKey -> blockKey.identifier().toString()).orElse("air"));
        if (!state.getProperties().isEmpty()) {
            stringBuilder.append('[');
            boolean flag = false;

            for (Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (flag) {
                    stringBuilder.append(',');
                }

                appendProperty(stringBuilder, entry.getKey(), entry.getValue());
                flag = true;
            }

            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }

    private static <T extends Comparable<T>> void appendProperty(StringBuilder builder, Property<T> property, Comparable<?> value) {
        builder.append(property.getName());
        builder.append('=');
        builder.append(property.getName((T)value));
    }

    public record BlockResult(BlockState blockState, Map<Property<?>, Comparable<?>> properties, @Nullable CompoundTag nbt) {
    }

    public record TagResult(HolderSet<Block> tag, Map<String, String> vagueProperties, @Nullable CompoundTag nbt) {
    }
}
