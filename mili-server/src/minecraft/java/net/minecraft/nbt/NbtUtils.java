package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class NbtUtils {
    private static final Comparator<ListTag> YXZ_LISTTAG_INT_COMPARATOR = Comparator.<ListTag>comparingInt(listTag -> listTag.getIntOr(1, 0))
        .thenComparingInt(listTag -> listTag.getIntOr(0, 0))
        .thenComparingInt(listTag -> listTag.getIntOr(2, 0));
    private static final Comparator<ListTag> YXZ_LISTTAG_DOUBLE_COMPARATOR = Comparator.<ListTag>comparingDouble(listTag -> listTag.getDoubleOr(1, 0.0))
        .thenComparingDouble(listTag -> listTag.getDoubleOr(0, 0.0))
        .thenComparingDouble(listTag -> listTag.getDoubleOr(2, 0.0));
    private static final Codec<ResourceKey<Block>> BLOCK_NAME_CODEC = ResourceKey.codec(Registries.BLOCK);
    public static final String SNBT_DATA_TAG = "data";
    private static final char PROPERTIES_START = '{';
    private static final char PROPERTIES_END = '}';
    private static final String ELEMENT_SEPARATOR = ",";
    private static final char KEY_VALUE_SEPARATOR = ':';
    private static final Splitter COMMA_SPLITTER = Splitter.on(",");
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INDENT = 2;
    private static final int NOT_FOUND = -1;

    private NbtUtils() {
    }

    @VisibleForTesting
    public static boolean compareNbt(@Nullable Tag tag, @Nullable Tag other, boolean compareListTag) {
        if (tag == other) {
            return true;
        } else if (tag == null) {
            return true;
        } else if (other == null) {
            return false;
        } else if (!tag.getClass().equals(other.getClass())) {
            return false;
        } else if (tag instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag1 = (CompoundTag)other;
            if (compoundTag1.size() < compoundTag.size()) {
                return false;
            } else {
                for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                    Tag tag1 = entry.getValue();
                    if (!compareNbt(tag1, compoundTag1.get(entry.getKey()), compareListTag)) {
                        return false;
                    }
                }

                return true;
            }
        } else if (tag instanceof ListTag listTag && compareListTag) {
            ListTag listTag1 = (ListTag)other;
            if (listTag.isEmpty()) {
                return listTag1.isEmpty();
            } else if (listTag1.size() < listTag.size()) {
                return false;
            } else {
                for (Tag tag2 : listTag) {
                    boolean flag = false;

                    for (Tag tag3 : listTag1) {
                        if (compareNbt(tag2, tag3, compareListTag)) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }

                return true;
            }
        } else {
            return tag.equals(other);
        }
    }

    public static BlockState readBlockState(HolderGetter<Block> blockGetter, CompoundTag tag) {
        Optional<? extends Holder<Block>> optional = tag.read("Name", BLOCK_NAME_CODEC).flatMap(blockGetter::get);
        if (optional.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        } else {
            Block block = optional.get().value();
            BlockState blockState = block.defaultBlockState();
            Optional<CompoundTag> compound = tag.getCompound("Properties");
            if (compound.isPresent()) {
                StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();

                for (String string : compound.get().keySet()) {
                    Property<?> property = stateDefinition.getProperty(string);
                    if (property != null) {
                        blockState = setValueHelper(blockState, property, string, compound.get(), tag);
                    }
                }
            }

            return blockState;
        }
    }

    private static <S extends StateHolder<?, S>, T extends Comparable<T>> S setValueHelper(
        S stateHolder, Property<T> property, String propertyName, CompoundTag propertiesTag, CompoundTag blockStateTag
    ) {
        Optional<T> optional = propertiesTag.getString(propertyName).flatMap(property::getValue);
        if (optional.isPresent()) {
            return stateHolder.setValue(property, optional.get());
        } else {
            LOGGER.warn("Unable to read property: {} with value: {} for blockstate: {}", propertyName, propertiesTag.get(propertyName), blockStateTag);
            return stateHolder;
        }
    }

    public static CompoundTag writeBlockState(BlockState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (!values.isEmpty()) {
            CompoundTag compoundTag1 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag1.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag1);
        }

        return compoundTag;
    }

    public static CompoundTag writeFluidState(FluidState state) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("Name", BuiltInRegistries.FLUID.getKey(state.getType()).toString());
        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (!values.isEmpty()) {
            CompoundTag compoundTag1 = new CompoundTag();

            for (Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
                Property<?> property = entry.getKey();
                compoundTag1.putString(property.getName(), getName(property, entry.getValue()));
            }

            compoundTag.put("Properties", compoundTag1);
        }

        return compoundTag;
    }

    private static <T extends Comparable<T>> String getName(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }

    public static String prettyPrint(Tag tag) {
        return prettyPrint(tag, false);
    }

    public static String prettyPrint(Tag tag, boolean prettyPrintArray) {
        return prettyPrint(new StringBuilder(), tag, 0, prettyPrintArray).toString();
    }

    public static StringBuilder prettyPrint(StringBuilder stringBuilder, Tag tag, int indentLevel, boolean prettyPrintArray) {
        return switch (tag) {
            case PrimitiveTag primitiveTag -> stringBuilder.append(primitiveTag);
            case EndTag endTag -> stringBuilder;
            case ByteArrayTag byteArrayTag -> {
                byte[] asByteArray = byteArrayTag.getAsByteArray();
                int i = asByteArray.length;
                indent(indentLevel, stringBuilder).append("byte[").append(i).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i1 = 0; i1 < asByteArray.length; i1++) {
                        if (i1 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i1 % 16 == 0 && i1 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i1 < asByteArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i1 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%02X", asByteArray[i1] & 255));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                yield stringBuilder;
            }
            case ListTag listTag -> {
                int i = listTag.size();
                indent(indentLevel, stringBuilder).append("list").append("[").append(i).append("] [");
                if (i != 0) {
                    stringBuilder.append('\n');
                }

                for (int i1 = 0; i1 < i; i1++) {
                    if (i1 != 0) {
                        stringBuilder.append(",\n");
                    }

                    indent(indentLevel + 1, stringBuilder);
                    prettyPrint(stringBuilder, listTag.get(i1), indentLevel + 1, prettyPrintArray);
                }

                if (i != 0) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append(']');
                yield stringBuilder;
            }
            case IntArrayTag intArrayTag -> {
                int[] asIntArray = intArrayTag.getAsIntArray();
                int i2 = 0;

                for (int i3 : asIntArray) {
                    i2 = Math.max(i2, String.format(Locale.ROOT, "%X", i3).length());
                }

                int i4 = asIntArray.length;
                indent(indentLevel, stringBuilder).append("int[").append(i4).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i5 = 0; i5 < asIntArray.length; i5++) {
                        if (i5 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i5 % 16 == 0 && i5 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i5 < asIntArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i5 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + i2 + "X", asIntArray[i5]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                yield stringBuilder;
            }
            case CompoundTag compoundTag -> {
                List<String> list = Lists.newArrayList(compoundTag.keySet());
                Collections.sort(list);
                indent(indentLevel, stringBuilder).append('{');
                if (stringBuilder.length() - stringBuilder.lastIndexOf("\n") > 2 * (indentLevel + 1)) {
                    stringBuilder.append('\n');
                    indent(indentLevel + 1, stringBuilder);
                }

                int i4 = list.stream().mapToInt(String::length).max().orElse(0);
                String repeated = Strings.repeat(" ", i4);

                for (int i6 = 0; i6 < list.size(); i6++) {
                    if (i6 != 0) {
                        stringBuilder.append(",\n");
                    }

                    String string = list.get(i6);
                    indent(indentLevel + 1, stringBuilder)
                        .append('"')
                        .append(string)
                        .append('"')
                        .append(repeated, 0, repeated.length() - string.length())
                        .append(": ");
                    prettyPrint(stringBuilder, compoundTag.get(string), indentLevel + 1, prettyPrintArray);
                }

                if (!list.isEmpty()) {
                    stringBuilder.append('\n');
                }

                indent(indentLevel, stringBuilder).append('}');
                yield stringBuilder;
            }
            case LongArrayTag longArrayTag -> {
                long[] asLongArray = longArrayTag.getAsLongArray();
                long l = 0L;

                for (long l1 : asLongArray) {
                    l = Math.max(l, (long)String.format(Locale.ROOT, "%X", l1).length());
                }

                long l2 = asLongArray.length;
                indent(indentLevel, stringBuilder).append("long[").append(l2).append("] {\n");
                if (prettyPrintArray) {
                    indent(indentLevel + 1, stringBuilder);

                    for (int i7 = 0; i7 < asLongArray.length; i7++) {
                        if (i7 != 0) {
                            stringBuilder.append(',');
                        }

                        if (i7 % 16 == 0 && i7 / 16 > 0) {
                            stringBuilder.append('\n');
                            if (i7 < asLongArray.length) {
                                indent(indentLevel + 1, stringBuilder);
                            }
                        } else if (i7 != 0) {
                            stringBuilder.append(' ');
                        }

                        stringBuilder.append(String.format(Locale.ROOT, "0x%0" + l + "X", asLongArray[i7]));
                    }
                } else {
                    indent(indentLevel + 1, stringBuilder).append(" // Skipped, supply withBinaryBlobs true");
                }

                stringBuilder.append('\n');
                indent(indentLevel, stringBuilder).append('}');
                yield stringBuilder;
            }
            default -> throw new MatchException(null, null);
        };
    }

    private static StringBuilder indent(int indentLevel, StringBuilder stringBuilder) {
        int i = stringBuilder.lastIndexOf("\n") + 1;
        int i1 = stringBuilder.length() - i;

        for (int i2 = 0; i2 < 2 * indentLevel - i1; i2++) {
            stringBuilder.append(' ');
        }

        return stringBuilder;
    }

    public static Component toPrettyComponent(Tag tag) {
        return new TextComponentTagVisitor("").visit(tag);
    }

    public static String structureToSnbt(CompoundTag tag) {
        return new SnbtPrinterTagVisitor().visit(packStructureTemplate(tag));
    }

    public static CompoundTag snbtToStructure(String text) throws CommandSyntaxException {
        return unpackStructureTemplate(TagParser.parseCompoundFully(text));
    }

    @VisibleForTesting
    static CompoundTag packStructureTemplate(CompoundTag tag) {
        Optional<ListTag> list = tag.getList("palettes");
        ListTag listOrEmpty;
        if (list.isPresent()) {
            listOrEmpty = list.get().getListOrEmpty(0);
        } else {
            listOrEmpty = tag.getListOrEmpty("palette");
        }

        ListTag listTag = listOrEmpty.compoundStream().map(NbtUtils::packBlockState).map(StringTag::valueOf).collect(Collectors.toCollection(ListTag::new));
        tag.put("palette", listTag);
        if (list.isPresent()) {
            ListTag listTag1 = new ListTag();
            list.get().stream().flatMap(tag1 -> tag1.asList().stream()).forEach(listTag3 -> {
                CompoundTag compoundTag = new CompoundTag();

                for (int i = 0; i < listTag3.size(); i++) {
                    compoundTag.putString(listTag.getString(i).orElseThrow(), packBlockState(listTag3.getCompound(i).orElseThrow()));
                }

                listTag1.add(compoundTag);
            });
            tag.put("palettes", listTag1);
        }

        Optional<ListTag> list1 = tag.getList("entities");
        if (list1.isPresent()) {
            ListTag listTag2 = list1.get()
                .compoundStream()
                .sorted(Comparator.comparing(compoundTag -> compoundTag.getList("pos"), Comparators.emptiesLast(YXZ_LISTTAG_DOUBLE_COMPARATOR)))
                .collect(Collectors.toCollection(ListTag::new));
            tag.put("entities", listTag2);
        }

        ListTag listTag2 = tag.getList("blocks")
            .stream()
            .flatMap(ListTag::compoundStream)
            .sorted(Comparator.comparing(compoundTag -> compoundTag.getList("pos"), Comparators.emptiesLast(YXZ_LISTTAG_INT_COMPARATOR)))
            .peek(compoundTag -> compoundTag.putString("state", listTag.getString(compoundTag.getIntOr("state", 0)).orElseThrow()))
            .collect(Collectors.toCollection(ListTag::new));
        tag.put("data", listTag2);
        tag.remove("blocks");
        return tag;
    }

    @VisibleForTesting
    static CompoundTag unpackStructureTemplate(CompoundTag tag) {
        ListTag listOrEmpty = tag.getListOrEmpty("palette");
        Map<String, Tag> map = listOrEmpty.stream()
            .flatMap(tag1 -> tag1.asString().stream())
            .collect(ImmutableMap.toImmutableMap(Function.identity(), NbtUtils::unpackBlockState));
        Optional<ListTag> list = tag.getList("palettes");
        if (list.isPresent()) {
            tag.put(
                "palettes",
                list.get()
                    .compoundStream()
                    .map(
                        compoundTag1 -> map.keySet()
                            .stream()
                            .map(string1 -> compoundTag1.getString(string1).orElseThrow())
                            .map(NbtUtils::unpackBlockState)
                            .collect(Collectors.toCollection(ListTag::new))
                    )
                    .collect(Collectors.toCollection(ListTag::new))
            );
            tag.remove("palette");
        } else {
            tag.put("palette", map.values().stream().collect(Collectors.toCollection(ListTag::new)));
        }

        Optional<ListTag> list1 = tag.getList("data");
        if (list1.isPresent()) {
            Object2IntMap<String> map1 = new Object2IntOpenHashMap<>();
            map1.defaultReturnValue(-1);

            for (int i = 0; i < listOrEmpty.size(); i++) {
                map1.put(listOrEmpty.getString(i).orElseThrow(), i);
            }

            ListTag listTag = list1.get();

            for (int i1 = 0; i1 < listTag.size(); i1++) {
                CompoundTag compoundTag = listTag.getCompound(i1).orElseThrow();
                String string = compoundTag.getString("state").orElseThrow();
                int _int = map1.getInt(string);
                if (_int == -1) {
                    throw new IllegalStateException("Entry " + string + " missing from palette");
                }

                compoundTag.putInt("state", _int);
            }

            tag.put("blocks", listTag);
            tag.remove("data");
        }

        return tag;
    }

    @VisibleForTesting
    static String packBlockState(CompoundTag tag) {
        StringBuilder stringBuilder = new StringBuilder(tag.getString("Name").orElseThrow());
        tag.getCompound("Properties")
            .ifPresent(
                compoundTag -> {
                    String string = compoundTag.entrySet()
                        .stream()
                        .sorted(Entry.comparingByKey())
                        .map(entry -> entry.getKey() + ":" + entry.getValue().asString().orElseThrow())
                        .collect(Collectors.joining(","));
                    stringBuilder.append('{').append(string).append('}');
                }
            );
        return stringBuilder.toString();
    }

    @VisibleForTesting
    static CompoundTag unpackBlockState(String blockStateText) {
        CompoundTag compoundTag = new CompoundTag();
        int index = blockStateText.indexOf(123);
        String sub;
        if (index >= 0) {
            sub = blockStateText.substring(0, index);
            CompoundTag compoundTag1 = new CompoundTag();
            if (index + 2 <= blockStateText.length()) {
                String sub1 = blockStateText.substring(index + 1, blockStateText.indexOf(125, index));
                COMMA_SPLITTER.split(sub1).forEach(string -> {
                    List<String> parts = COLON_SPLITTER.splitToList(string);
                    if (parts.size() == 2) {
                        compoundTag1.putString(parts.get(0), parts.get(1));
                    } else {
                        LOGGER.error("Something went wrong parsing: '{}' -- incorrect gamedata!", blockStateText);
                    }
                });
                compoundTag.put("Properties", compoundTag1);
            }
        } else {
            sub = blockStateText;
        }

        compoundTag.putString("Name", sub);
        return compoundTag;
    }

    public static CompoundTag addCurrentDataVersion(CompoundTag tag) {
        int version = SharedConstants.getCurrentVersion().dataVersion().version();
        return addDataVersion(tag, version);
    }

    public static CompoundTag addDataVersion(CompoundTag tag, int dataVersion) {
        tag.putInt("DataVersion", dataVersion);
        return tag;
    }

    public static Dynamic<Tag> addCurrentDataVersion(Dynamic<Tag> dynamic) {
        int version = SharedConstants.getCurrentVersion().dataVersion().version();
        return addDataVersion(dynamic, version);
    }

    public static Dynamic<Tag> addDataVersion(Dynamic<Tag> dynamic, int dataVersion) {
        return dynamic.set("DataVersion", dynamic.createInt(dataVersion));
    }

    public static void addCurrentDataVersion(ValueOutput output) {
        int version = SharedConstants.getCurrentVersion().dataVersion().version();
        addDataVersion(output, version);
    }

    public static void addDataVersion(ValueOutput output, int dataVersion) {
        output.putInt("DataVersion", dataVersion);
    }

    public static int getDataVersion(CompoundTag tag) {
        return getDataVersion(tag, -1);
    }

    public static int getDataVersion(CompoundTag tag, int defaultValue) {
        return tag.getIntOr("DataVersion", defaultValue);
    }

    public static int getDataVersion(Dynamic<?> tag, int defaultValue) {
        return tag.get("DataVersion").asInt(defaultValue);
    }
}
