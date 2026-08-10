package net.minecraft.server.commands.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.PrimitiveTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class DataCommands {
    private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Component.translatable("commands.data.merge.failed"));
    private static final DynamicCommandExceptionType ERROR_GET_NOT_NUMBER = new DynamicCommandExceptionType(
        nbtPath -> Component.translatableEscape("commands.data.get.invalid", nbtPath)
    );
    private static final DynamicCommandExceptionType ERROR_GET_NON_EXISTENT = new DynamicCommandExceptionType(
        nbtPath -> Component.translatableEscape("commands.data.get.unknown", nbtPath)
    );
    private static final SimpleCommandExceptionType ERROR_MULTIPLE_TAGS = new SimpleCommandExceptionType(Component.translatable("commands.data.get.multiple"));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(
        unexpectedNbt -> Component.translatableEscape("commands.data.modify.expected_object", unexpectedNbt)
    );
    private static final DynamicCommandExceptionType ERROR_EXPECTED_VALUE = new DynamicCommandExceptionType(
        unexpectedNbt -> Component.translatableEscape("commands.data.modify.expected_value", unexpectedNbt)
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_SUBSTRING = new Dynamic2CommandExceptionType(
        (startIndex, endIndex) -> Component.translatableEscape("commands.data.modify.invalid_substring", startIndex, endIndex)
    );
    public static final List<Function<String, DataCommands.DataProvider>> ALL_PROVIDERS = ImmutableList.of(
        EntityDataAccessor.PROVIDER, BlockDataAccessor.PROVIDER, StorageDataAccessor.PROVIDER
    );
    public static final List<DataCommands.DataProvider> TARGET_PROVIDERS = ALL_PROVIDERS.stream()
        .map(dataProviderFactory -> dataProviderFactory.apply("target"))
        .collect(ImmutableList.toImmutableList());
    public static final List<DataCommands.DataProvider> SOURCE_PROVIDERS = ALL_PROVIDERS.stream()
        .map(dataProviderFactory -> dataProviderFactory.apply("source"))
        .collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("data")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            literalArgumentBuilder.then(
                    dataProvider.wrap(
                        Commands.literal("merge"),
                        argumentBuilder -> argumentBuilder.then(
                            Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                .executes(
                                    commandContext -> mergeData(
                                        commandContext.getSource(),
                                        dataProvider.access(commandContext),
                                        CompoundTagArgument.getCompoundTag(commandContext, "nbt")
                                    )
                                )
                        )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("get"),
                        argumentBuilder -> argumentBuilder.executes(commandContext -> getData(commandContext.getSource(), dataProvider.access(commandContext)))
                            .then(
                                Commands.argument("path", NbtPathArgument.nbtPath())
                                    .executes(context -> getData(context.getSource(), dataProvider.access(context), NbtPathArgument.getPath(context, "path")))
                                    .then(
                                        Commands.argument("scale", DoubleArgumentType.doubleArg())
                                            .executes(
                                                context -> getNumeric(
                                                    context.getSource(),
                                                    dataProvider.access(context),
                                                    NbtPathArgument.getPath(context, "path"),
                                                    DoubleArgumentType.getDouble(context, "scale")
                                                )
                                            )
                                    )
                            )
                    )
                )
                .then(
                    dataProvider.wrap(
                        Commands.literal("remove"),
                        argumentBuilder -> argumentBuilder.then(
                            Commands.argument("path", NbtPathArgument.nbtPath())
                                .executes(
                                    commandContext -> removeData(
                                        commandContext.getSource(), dataProvider.access(commandContext), NbtPathArgument.getPath(commandContext, "path")
                                    )
                                )
                        )
                    )
                )
                .then(
                    decorateModification(
                        (argumentBuilder, dataManipulatorDecorator) -> argumentBuilder.then(
                                Commands.literal("insert")
                                    .then(
                                        Commands.argument("index", IntegerArgumentType.integer())
                                            .then(
                                                dataManipulatorDecorator.create(
                                                    (context, data, nbtPath, tags) -> nbtPath.insert(
                                                        IntegerArgumentType.getInteger(context, "index"), data, tags
                                                    )
                                                )
                                            )
                                    )
                            )
                            .then(
                                Commands.literal("prepend")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.insert(0, sourceCompoundTag, tags)
                                        )
                                    )
                            )
                            .then(
                                Commands.literal("append")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.insert(-1, sourceCompoundTag, tags)
                                        )
                                    )
                            )
                            .then(
                                Commands.literal("set")
                                    .then(
                                        dataManipulatorDecorator.create(
                                            (context, sourceCompoundTag, nbtPath, tags) -> nbtPath.set(sourceCompoundTag, Iterables.getLast(tags))
                                        )
                                    )
                            )
                            .then(Commands.literal("merge").then(dataManipulatorDecorator.create((context, sourceCompoundTag, nbtPath, tags) -> {
                                CompoundTag compoundTag = new CompoundTag();

                                for (Tag tag : tags) {
                                    if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
                                        throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                                    }

                                    if (!(tag instanceof CompoundTag compoundTag1)) {
                                        throw ERROR_EXPECTED_OBJECT.create(tag);
                                    }

                                    compoundTag.merge(compoundTag1);
                                }

                                Collection<Tag> collection = nbtPath.getOrCreate(sourceCompoundTag, CompoundTag::new);
                                int i = 0;

                                for (Tag tag1 : collection) {
                                    if (!(tag1 instanceof CompoundTag compoundTag2)) {
                                        throw ERROR_EXPECTED_OBJECT.create(tag1);
                                    }

                                    CompoundTag compoundTag3 = compoundTag2.copy();
                                    compoundTag2.merge(compoundTag);
                                    i += compoundTag3.equals(compoundTag2) ? 0 : 1;
                                }

                                return i;
                            })))
                    )
                );
        }

        dispatcher.register(literalArgumentBuilder);
    }

    private static String getAsText(Tag tag) throws CommandSyntaxException {
        return switch (tag) {
            case StringTag(String var7) -> var7;
            case PrimitiveTag primitiveTag -> primitiveTag.toString();
            default -> throw ERROR_EXPECTED_VALUE.create(tag);
        };
    }

    private static List<Tag> stringifyTagList(List<Tag> tagList, DataCommands.StringProcessor processor) throws CommandSyntaxException {
        List<Tag> list = new ArrayList<>(tagList.size());

        for (Tag tag : tagList) {
            String asText = getAsText(tag);
            list.add(StringTag.valueOf(processor.process(asText)));
        }

        return list;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> decorateModification(
        BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, DataCommands.DataManipulatorDecorator> decorator
    ) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("modify");

        for (DataCommands.DataProvider dataProvider : TARGET_PROVIDERS) {
            dataProvider.wrap(
                literalArgumentBuilder,
                argumentBuilder -> {
                    ArgumentBuilder<CommandSourceStack, ?> argumentBuilder1 = Commands.argument("targetPath", NbtPathArgument.nbtPath());

                    for (DataCommands.DataProvider dataProvider1 : SOURCE_PROVIDERS) {
                        decorator.accept(
                            argumentBuilder1,
                            dataManipulator -> dataProvider1.wrap(
                                Commands.literal("from"),
                                argumentBuilder2 -> argumentBuilder2.executes(
                                        commandContext -> manipulateData(
                                            commandContext, dataProvider, dataManipulator, getSingletonSource(commandContext, dataProvider1)
                                        )
                                    )
                                    .then(
                                        Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                            .executes(
                                                context -> manipulateData(context, dataProvider, dataManipulator, resolveSourcePath(context, dataProvider1))
                                            )
                                    )
                            )
                        );
                        decorator.accept(
                            argumentBuilder1,
                            dataManipulator -> dataProvider1.wrap(
                                Commands.literal("string"),
                                argumentBuilder2 -> argumentBuilder2.executes(
                                        commandContext -> manipulateData(
                                            commandContext,
                                            dataProvider,
                                            dataManipulator,
                                            stringifyTagList(getSingletonSource(commandContext, dataProvider1), input -> input)
                                        )
                                    )
                                    .then(
                                        Commands.argument("sourcePath", NbtPathArgument.nbtPath())
                                            .executes(
                                                commandContext -> manipulateData(
                                                    commandContext,
                                                    dataProvider,
                                                    dataManipulator,
                                                    stringifyTagList(resolveSourcePath(commandContext, dataProvider1), input -> input)
                                                )
                                            )
                                            .then(
                                                Commands.argument("start", IntegerArgumentType.integer())
                                                    .executes(
                                                        commandContext -> manipulateData(
                                                            commandContext,
                                                            dataProvider,
                                                            dataManipulator,
                                                            stringifyTagList(
                                                                resolveSourcePath(commandContext, dataProvider1),
                                                                input -> substring(input, IntegerArgumentType.getInteger(commandContext, "start"))
                                                            )
                                                        )
                                                    )
                                                    .then(
                                                        Commands.argument("end", IntegerArgumentType.integer())
                                                            .executes(
                                                                commandContext -> manipulateData(
                                                                    commandContext,
                                                                    dataProvider,
                                                                    dataManipulator,
                                                                    stringifyTagList(
                                                                        resolveSourcePath(commandContext, dataProvider1),
                                                                        input -> substring(
                                                                            input,
                                                                            IntegerArgumentType.getInteger(commandContext, "start"),
                                                                            IntegerArgumentType.getInteger(commandContext, "end")
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                    )
                                            )
                                    )
                            )
                        );
                    }

                    decorator.accept(
                        argumentBuilder1,
                        dataManipulator -> Commands.literal("value").then(Commands.argument("value", NbtTagArgument.nbtTag()).executes(commandContext -> {
                            List<Tag> list = Collections.singletonList(NbtTagArgument.getNbtTag(commandContext, "value"));
                            return manipulateData(commandContext, dataProvider, dataManipulator, list);
                        }))
                    );
                    return argumentBuilder.then(argumentBuilder1);
                }
            );
        }

        return literalArgumentBuilder;
    }

    private static String validatedSubstring(String source, int start, int end) throws CommandSyntaxException {
        if (start >= 0 && end <= source.length() && start <= end) {
            return source.substring(start, end);
        } else {
            throw ERROR_INVALID_SUBSTRING.create(start, end);
        }
    }

    private static String substring(String source, int start, int end) throws CommandSyntaxException {
        int len = source.length();
        int offset = getOffset(start, len);
        int offset1 = getOffset(end, len);
        return validatedSubstring(source, offset, offset1);
    }

    private static String substring(String source, int start) throws CommandSyntaxException {
        int len = source.length();
        return validatedSubstring(source, getOffset(start, len), len);
    }

    private static int getOffset(int index, int length) {
        return index >= 0 ? index : length + index;
    }

    private static List<Tag> getSingletonSource(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(context);
        return Collections.singletonList(dataAccessor.getData());
    }

    private static List<Tag> resolveSourcePath(CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(context);
        NbtPathArgument.NbtPath path = NbtPathArgument.getPath(context, "sourcePath");
        return path.get(dataAccessor.getData());
    }

    private static int manipulateData(
        CommandContext<CommandSourceStack> context, DataCommands.DataProvider dataProvider, DataCommands.DataManipulator dataManipulator, List<Tag> tags
    ) throws CommandSyntaxException {
        DataAccessor dataAccessor = dataProvider.access(context);
        NbtPathArgument.NbtPath path = NbtPathArgument.getPath(context, "targetPath");
        CompoundTag data = dataAccessor.getData();
        int i = dataManipulator.modify(context, data, path, tags);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            dataAccessor.setData(data);
            context.getSource().sendSuccess(() -> dataAccessor.getModifiedSuccess(), true);
            return i;
        }
    }

    private static int removeData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        CompoundTag data = accessor.getData();
        int i = path.remove(data);
        if (i == 0) {
            throw ERROR_MERGE_UNCHANGED.create();
        } else {
            accessor.setData(data);
            source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
            return i;
        }
    }

    public static Tag getSingleTag(NbtPathArgument.NbtPath path, DataAccessor accessor) throws CommandSyntaxException {
        Collection<Tag> collection = path.get(accessor.getData());
        Iterator<Tag> iterator = collection.iterator();
        Tag tag = iterator.next();
        if (iterator.hasNext()) {
            throw ERROR_MULTIPLE_TAGS.create();
        } else {
            return tag;
        }
    }

    private static int getData(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        Tag singleTag = getSingleTag(path, accessor);

        int i = switch (singleTag) {
            case NumericTag numericTag -> Mth.floor(numericTag.doubleValue());
            case CollectionTag collectionTag -> collectionTag.size();
            case CompoundTag compoundTag -> compoundTag.size();
            case StringTag(String var14) -> var14.length();
            case EndTag endTag -> throw ERROR_GET_NON_EXISTENT.create(path.toString());
            default -> throw new MatchException(null, null);
        };
        source.sendSuccess(() -> accessor.getPrintSuccess(singleTag), false);
        return i;
    }

    private static int getNumeric(CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, double scale) throws CommandSyntaxException {
        Tag singleTag = getSingleTag(path, accessor);
        if (!(singleTag instanceof NumericTag)) {
            throw ERROR_GET_NOT_NUMBER.create(path.toString());
        } else {
            int floor = Mth.floor(((NumericTag)singleTag).doubleValue() * scale);
            source.sendSuccess(() -> accessor.getPrintSuccess(path, scale, floor), false);
            return floor;
        }
    }

    private static int getData(CommandSourceStack source, DataAccessor accessor) throws CommandSyntaxException {
        CompoundTag data = accessor.getData();
        source.sendSuccess(() -> accessor.getPrintSuccess(data), false);
        return 1;
    }

    private static int mergeData(CommandSourceStack source, DataAccessor accessor, CompoundTag tag) throws CommandSyntaxException {
        CompoundTag data = accessor.getData();
        if (NbtPathArgument.NbtPath.isTooDeep(tag, 0)) {
            throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
        } else {
            CompoundTag compoundTag = data.copy().merge(tag);
            if (data.equals(compoundTag)) {
                throw ERROR_MERGE_UNCHANGED.create();
            } else {
                accessor.setData(compoundTag);
                source.sendSuccess(() -> accessor.getModifiedSuccess(), true);
                return 1;
            }
        }
    }

    @FunctionalInterface
    interface DataManipulator {
        int modify(CommandContext<CommandSourceStack> context, CompoundTag data, NbtPathArgument.NbtPath nbtPath, List<Tag> tags) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DataManipulatorDecorator {
        ArgumentBuilder<CommandSourceStack, ?> create(DataCommands.DataManipulator dataManipulator);
    }

    public interface DataProvider {
        DataAccessor access(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        ArgumentBuilder<CommandSourceStack, ?> wrap(
            ArgumentBuilder<CommandSourceStack, ?> builder, Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> action
        );
    }

    @FunctionalInterface
    interface StringProcessor {
        String process(String input) throws CommandSyntaxException;
    }
}
