package net.minecraft.commands.arguments;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType(
        path -> Component.translatableEscape("arguments.nbtpath.nothing_found", path)
    );
    static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType(
        actualType -> Component.translatableEscape("commands.data.modify.expected_list", actualType)
    );
    static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType(
        invalidIndex -> Component.translatableEscape("commands.data.modify.invalid_index", invalidIndex)
    );
    private static final char INDEX_MATCH_START = '[';
    private static final char INDEX_MATCH_END = ']';
    private static final char KEY_MATCH_START = '{';
    private static final char KEY_MATCH_END = '}';
    private static final char QUOTED_KEY_START = '"';
    private static final char SINGLE_QUOTED_KEY_START = '\'';

    public static NbtPathArgument nbtPath() {
        return new NbtPathArgument();
    }

    public static NbtPathArgument.NbtPath getPath(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, NbtPathArgument.NbtPath.class);
    }

    @Override
    public NbtPathArgument.NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<NbtPathArgument.Node> list = Lists.newArrayList();
        int cursor = reader.getCursor();
        Object2IntMap<NbtPathArgument.Node> map = new Object2IntOpenHashMap<>();
        boolean flag = true;

        while (reader.canRead() && reader.peek() != ' ') {
            NbtPathArgument.Node node = parseNode(reader, flag);
            list.add(node);
            map.put(node, reader.getCursor() - cursor);
            flag = false;
            if (reader.canRead()) {
                char c = reader.peek();
                if (c != ' ' && c != '[' && c != '{') {
                    reader.expect('.');
                }
            }
        }

        return new NbtPathArgument.NbtPath(reader.getString().substring(cursor, reader.getCursor()), list.toArray(new NbtPathArgument.Node[0]), map);
    }

    private static NbtPathArgument.Node parseNode(StringReader reader, boolean first) throws CommandSyntaxException {
        return (NbtPathArgument.Node)(switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                int i = reader.peek();
                if (i == 123) {
                    CompoundTag compoundTag1 = TagParser.parseCompoundAsArgument(reader);
                    reader.expect(']');
                    yield new NbtPathArgument.MatchElementNode(compoundTag1);
                } else if (i == 93) {
                    reader.skip();
                    yield NbtPathArgument.AllElementsNode.INSTANCE;
                } else {
                    int _int = reader.readInt();
                    reader.expect(']');
                    yield new NbtPathArgument.IndexedElementNode(_int);
                }
            }
            case '{' -> {
                if (!first) {
                    throw ERROR_INVALID_NODE.createWithContext(reader);
                }

                CompoundTag compoundTag = TagParser.parseCompoundAsArgument(reader);
                yield new NbtPathArgument.MatchRootObjectNode(compoundTag);
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        });
    }

    private static NbtPathArgument.Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (name.isEmpty()) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        } else if (reader.canRead() && reader.peek() == '{') {
            CompoundTag compoundTag = TagParser.parseCompoundAsArgument(reader);
            return new NbtPathArgument.MatchObjectNode(name, compoundTag);
        } else {
            return new NbtPathArgument.CompoundChildNode(name);
        }
    }

    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();

        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }

        if (reader.getCursor() == cursor) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        } else {
            return reader.getString().substring(cursor, reader.getCursor());
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static boolean isAllowedInUnquotedName(char ch) {
        return ch != ' ' && ch != '"' && ch != '\'' && ch != '[' && ch != ']' && ch != '.' && ch != '{' && ch != '}';
    }

    static Predicate<Tag> createTagPredicate(CompoundTag tag) {
        return other -> NbtUtils.compareNbt(tag, other, true);
    }

    static class AllElementsNode implements NbtPathArgument.Node {
        public static final NbtPathArgument.AllElementsNode INSTANCE = new NbtPathArgument.AllElementsNode();

        private AllElementsNode() {
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag collectionTag) {
                Iterables.addAll(tags, collectionTag);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CollectionTag collectionTag) {
                if (collectionTag.isEmpty()) {
                    Tag tag1 = supplier.get();
                    if (collectionTag.addTag(0, tag1)) {
                        tags.add(tag1);
                    }
                } else {
                    Iterables.addAll(tags, collectionTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (!(tag instanceof CollectionTag collectionTag)) {
                return 0;
            } else {
                int size = collectionTag.size();
                if (size == 0) {
                    collectionTag.addTag(0, supplier.get());
                    return 1;
                } else {
                    Tag tag1 = supplier.get();
                    int i = size - (int)collectionTag.stream().filter(tag1::equals).count();
                    if (i == 0) {
                        return 0;
                    } else {
                        collectionTag.clear();
                        if (!collectionTag.addTag(0, tag1)) {
                            return 0;
                        } else {
                            for (int i1 = 1; i1 < size; i1++) {
                                collectionTag.addTag(i1, supplier.get());
                            }

                            return i;
                        }
                    }
                }
            }
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag collectionTag) {
                int size = collectionTag.size();
                if (size > 0) {
                    collectionTag.clear();
                    return size;
                }
            }

            return 0;
        }
    }

    static class CompoundChildNode implements NbtPathArgument.Node {
        private final String name;

        public CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag) {
                Tag tag1 = ((CompoundTag)tag).get(this.name);
                if (tag1 != null) {
                    tags.add(tag1);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag1;
                if (compoundTag.contains(this.name)) {
                    tag1 = compoundTag.get(this.name);
                } else {
                    tag1 = supplier.get();
                    compoundTag.put(this.name, tag1);
                }

                tags.add(tag1);
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag1 = supplier.get();
                Tag tag2 = compoundTag.put(this.name, tag1);
                if (!tag1.equals(tag2)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CompoundTag compoundTag && compoundTag.contains(this.name)) {
                compoundTag.remove(this.name);
                return 1;
            } else {
                return 0;
            }
        }
    }

    static class IndexedElementNode implements NbtPathArgument.Node {
        private final int index;

        public IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CollectionTag collectionTag) {
                int size = collectionTag.size();
                int i = this.index < 0 ? size + this.index : this.index;
                if (0 <= i && i < size) {
                    tags.add(collectionTag.get(i));
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CollectionTag collectionTag) {
                int size = collectionTag.size();
                int i = this.index < 0 ? size + this.index : this.index;
                if (0 <= i && i < size) {
                    Tag tag1 = collectionTag.get(i);
                    Tag tag2 = supplier.get();
                    if (!tag2.equals(tag1) && collectionTag.setTag(i, tag2)) {
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CollectionTag collectionTag) {
                int size = collectionTag.size();
                int i = this.index < 0 ? size + this.index : this.index;
                if (0 <= i && i < size) {
                    collectionTag.remove(i);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchElementNode implements NbtPathArgument.Node {
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchElementNode(CompoundTag pattern) {
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(tags::add);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            MutableBoolean mutableBoolean = new MutableBoolean();
            if (tag instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(currentTag -> {
                    tags.add(currentTag);
                    mutableBoolean.setTrue();
                });
                if (mutableBoolean.isFalse()) {
                    CompoundTag compoundTag = this.pattern.copy();
                    listTag.add(compoundTag);
                    tags.add(compoundTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            int i = 0;
            if (tag instanceof ListTag listTag) {
                int size = listTag.size();
                if (size == 0) {
                    listTag.add(supplier.get());
                    i++;
                } else {
                    for (int i1 = 0; i1 < size; i1++) {
                        Tag tag1 = listTag.get(i1);
                        if (this.predicate.test(tag1)) {
                            Tag tag2 = supplier.get();
                            if (!tag2.equals(tag1) && listTag.setTag(i1, tag2)) {
                                i++;
                            }
                        }
                    }
                }
            }

            return i;
        }

        @Override
        public int removeTag(Tag tag) {
            int i = 0;
            if (tag instanceof ListTag listTag) {
                for (int i1 = listTag.size() - 1; i1 >= 0; i1--) {
                    if (this.predicate.test(listTag.get(i1))) {
                        listTag.remove(i1);
                        i++;
                    }
                }
            }

            return i;
        }
    }

    static class MatchObjectNode implements NbtPathArgument.Node {
        private final String name;
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchObjectNode(String name, CompoundTag pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = NbtPathArgument.createTagPredicate(pattern);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag) {
                Tag tag1 = ((CompoundTag)tag).get(this.name);
                if (this.predicate.test(tag1)) {
                    tags.add(tag1);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag1 = compoundTag.get(this.name);
                if (tag1 == null) {
                    Tag var6 = this.pattern.copy();
                    compoundTag.put(this.name, var6);
                    tags.add(var6);
                } else if (this.predicate.test(tag1)) {
                    tags.add(tag1);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag1 = compoundTag.get(this.name);
                if (this.predicate.test(tag1)) {
                    Tag tag2 = supplier.get();
                    if (!tag2.equals(tag1)) {
                        compoundTag.put(this.name, tag2);
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            if (tag instanceof CompoundTag compoundTag) {
                Tag tag1 = compoundTag.get(this.name);
                if (this.predicate.test(tag1)) {
                    compoundTag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchRootObjectNode implements NbtPathArgument.Node {
        private final Predicate<Tag> predicate;

        public MatchRootObjectNode(CompoundTag tag) {
            this.predicate = NbtPathArgument.createTagPredicate(tag);
        }

        @Override
        public void getTag(Tag tag, List<Tag> tags) {
            if (tag instanceof CompoundTag && this.predicate.test(tag)) {
                tags.add(tag);
            }
        }

        @Override
        public void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags) {
            this.getTag(tag, tags);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag tag, Supplier<Tag> supplier) {
            return 0;
        }

        @Override
        public int removeTag(Tag tag) {
            return 0;
        }
    }

    public static class NbtPath {
        private final String original;
        private final Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition;
        private final NbtPathArgument.Node[] nodes;
        public static final Codec<NbtPathArgument.NbtPath> CODEC = Codec.STRING.comapFlatMap(string -> {
            try {
                NbtPathArgument.NbtPath nbtPath = new NbtPathArgument().parse(new StringReader(string));
                return DataResult.success(nbtPath);
            } catch (CommandSyntaxException var2) {
                return DataResult.error(() -> "Failed to parse path " + string + ": " + var2.getMessage());
            }
        }, NbtPathArgument.NbtPath::asString);

        public static NbtPathArgument.NbtPath of(String path) throws CommandSyntaxException {
            return new NbtPathArgument().parse(new StringReader(path));
        }

        public NbtPath(String original, NbtPathArgument.Node[] nodes, Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition) {
            this.original = original;
            this.nodes = nodes;
            this.nodeToOriginalPosition = nodeToOriginalPosition;
        }

        public List<Tag> get(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public int countMatching(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    return 0;
                }
            }

            return list.size();
        }

        private List<Tag> getOrCreateParents(Tag tag) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                NbtPathArgument.Node node = this.nodes[i];
                int i1 = i + 1;
                list = node.getOrCreate(list, this.nodes[i1]::createPreferredParentTag);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public List<Tag> getOrCreate(Tag tag, Supplier<Tag> supplier) throws CommandSyntaxException {
            List<Tag> parents = this.getOrCreateParents(tag);
            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return node.getOrCreate(parents, supplier);
        }

        private static int apply(List<Tag> tags, Function<Tag, Integer> function) {
            return tags.stream().map(function).reduce(0, (integer, integer1) -> integer + integer1);
        }

        public static boolean isTooDeep(Tag tag, int currentDepth) {
            if (currentDepth >= 512) {
                return true;
            } else {
                if (tag instanceof CompoundTag compoundTag) {
                    for (Tag tag1 : compoundTag.values()) {
                        if (isTooDeep(tag1, currentDepth + 1)) {
                            return true;
                        }
                    }
                } else if (tag instanceof ListTag) {
                    for (Tag tag1x : (ListTag)tag) {
                        if (isTooDeep(tag1x, currentDepth + 1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        public int set(Tag tag, Tag other) throws CommandSyntaxException {
            if (isTooDeep(other, this.estimatePathDepth())) {
                throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            } else {
                Tag tag1 = other.copy();
                List<Tag> parents = this.getOrCreateParents(tag);
                if (parents.isEmpty()) {
                    return 0;
                } else {
                    NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
                    MutableBoolean mutableBoolean = new MutableBoolean(false);
                    return apply(parents, tag2 -> node.setTag(tag2, () -> {
                        if (mutableBoolean.isFalse()) {
                            mutableBoolean.setTrue();
                            return tag1;
                        } else {
                            return tag1.copy();
                        }
                    }));
                }
            }
        }

        private int estimatePathDepth() {
            return this.nodes.length;
        }

        public int insert(int index, CompoundTag rootTag, List<Tag> tagsToInsert) throws CommandSyntaxException {
            List<Tag> list = new ArrayList<>(tagsToInsert.size());

            for (Tag tag : tagsToInsert) {
                Tag tag1 = tag.copy();
                list.add(tag1);
                if (isTooDeep(tag1, this.estimatePathDepth())) {
                    throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                }
            }

            Collection<Tag> collection = this.getOrCreate(rootTag, ListTag::new);
            int i = 0;
            boolean flag = false;

            for (Tag tag2 : collection) {
                if (!(tag2 instanceof CollectionTag collectionTag)) {
                    throw NbtPathArgument.ERROR_EXPECTED_LIST.create(tag2);
                }

                boolean flag1 = false;
                int i1 = index < 0 ? collectionTag.size() + index + 1 : index;

                for (Tag tag3 : list) {
                    try {
                        if (collectionTag.addTag(i1, flag ? tag3.copy() : tag3)) {
                            i1++;
                            flag1 = true;
                        }
                    } catch (IndexOutOfBoundsException var16) {
                        throw NbtPathArgument.ERROR_INVALID_INDEX.create(i1);
                    }
                }

                flag = true;
                i += flag1 ? 1 : 0;
            }

            return i;
        }

        public int remove(Tag tag) {
            List<Tag> list = Collections.singletonList(tag);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                list = this.nodes[i].get(list);
            }

            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return apply(list, node::removeTag);
        }

        private CommandSyntaxException createNotFoundException(NbtPathArgument.Node node) {
            int _int = this.nodeToOriginalPosition.getInt(node);
            return NbtPathArgument.ERROR_NOTHING_FOUND.create(this.original.substring(0, _int));
        }

        @Override
        public String toString() {
            return this.original;
        }

        public String asString() {
            return this.original;
        }
    }

    interface Node {
        void getTag(Tag tag, List<Tag> tags);

        void getOrCreateTag(Tag tag, Supplier<Tag> supplier, List<Tag> tags);

        Tag createPreferredParentTag();

        int setTag(Tag tag, Supplier<Tag> supplier);

        int removeTag(Tag tag);

        default List<Tag> get(List<Tag> tags) {
            return this.collect(tags, this::getTag);
        }

        default List<Tag> getOrCreate(List<Tag> tags, Supplier<Tag> supplier) {
            return this.collect(tags, (tag, tagList) -> this.getOrCreateTag(tag, supplier, tagList));
        }

        default List<Tag> collect(List<Tag> tags, BiConsumer<Tag, List<Tag>> consumer) {
            List<Tag> list = Lists.newArrayList();

            for (Tag tag : tags) {
                consumer.accept(tag, list);
            }

            return list;
        }
    }
}
