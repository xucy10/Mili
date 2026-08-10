package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CloneCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_OVERLAP = new SimpleCommandExceptionType(Component.translatable("commands.clone.overlap"));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.clone.toobig", maxBlocks, specifiedBlocks)
    );
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.clone.failed"));
    public static final Predicate<BlockInWorld> FILTER_AIR = blockInWorld -> !blockInWorld.getState().isAir();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("clone")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(beginEndDestinationAndModeSuffix(context, input -> input.getSource().getLevel()))
                .then(
                    Commands.literal("from")
                        .then(
                            Commands.argument("sourceDimension", DimensionArgument.dimension())
                                .then(beginEndDestinationAndModeSuffix(context, context1 -> DimensionArgument.getDimension(context1, "sourceDimension")))
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> beginEndDestinationAndModeSuffix(
        CommandBuildContext buildContext, InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> levelGetter
    ) {
        return Commands.argument("begin", BlockPosArgument.blockPos())
            .then(
                Commands.argument("end", BlockPosArgument.blockPos())
                    .then(destinationAndStrictSuffix(buildContext, levelGetter, input -> input.getSource().getLevel()))
                    .then(
                        Commands.literal("to")
                            .then(
                                Commands.argument("targetDimension", DimensionArgument.dimension())
                                    .then(
                                        destinationAndStrictSuffix(
                                            buildContext, levelGetter, context -> DimensionArgument.getDimension(context, "targetDimension")
                                        )
                                    )
                            )
                    )
            );
    }

    private static CloneCommands.DimensionAndPosition getLoadedDimensionAndPosition(CommandContext<CommandSourceStack> context, ServerLevel level, String name) throws CommandSyntaxException {
        BlockPos loadedBlockPos = BlockPosArgument.getLoadedBlockPos(context, level, name);
        return new CloneCommands.DimensionAndPosition(level, loadedBlockPos);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> destinationAndStrictSuffix(
        CommandBuildContext buildContext,
        InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> sourceLevelGetter,
        InCommandFunction<CommandContext<CommandSourceStack>, ServerLevel> destinationLevelGetter
    ) {
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> inCommandFunction = input -> getLoadedDimensionAndPosition(
            input, sourceLevelGetter.apply(input), "begin"
        );
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> inCommandFunction1 = context -> getLoadedDimensionAndPosition(
            context, sourceLevelGetter.apply(context), "end"
        );
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> inCommandFunction2 = context -> getLoadedDimensionAndPosition(
            context, destinationLevelGetter.apply(context), "destination"
        );
        return modeSuffix(
                buildContext, inCommandFunction, inCommandFunction1, inCommandFunction2, false, Commands.argument("destination", BlockPosArgument.blockPos())
            )
            .then(modeSuffix(buildContext, inCommandFunction, inCommandFunction1, inCommandFunction2, true, Commands.literal("strict")));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> modeSuffix(
        CommandBuildContext buildContext,
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> begin,
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> end,
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> destination,
        boolean strict,
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder
    ) {
        return argumentBuilder.executes(
                commandContext -> clone(
                    commandContext.getSource(),
                    begin.apply(commandContext),
                    end.apply(commandContext),
                    destination.apply(commandContext),
                    blockInWorld -> true,
                    CloneCommands.Mode.NORMAL,
                    strict
                )
            )
            .then(wrapWithCloneMode(begin, end, destination, input -> blockInWorld -> true, strict, Commands.literal("replace")))
            .then(wrapWithCloneMode(begin, end, destination, context -> FILTER_AIR, strict, Commands.literal("masked")))
            .then(
                Commands.literal("filtered")
                    .then(
                        wrapWithCloneMode(
                            begin,
                            end,
                            destination,
                            context -> BlockPredicateArgument.getBlockPredicate(context, "filter"),
                            strict,
                            Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext))
                        )
                    )
            );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithCloneMode(
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> begin,
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> end,
        InCommandFunction<CommandContext<CommandSourceStack>, CloneCommands.DimensionAndPosition> destination,
        InCommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filter,
        boolean strict,
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder
    ) {
        return argumentBuilder.executes(
                commandContext -> clone(
                    commandContext.getSource(),
                    begin.apply(commandContext),
                    end.apply(commandContext),
                    destination.apply(commandContext),
                    filter.apply(commandContext),
                    CloneCommands.Mode.NORMAL,
                    strict
                )
            )
            .then(
                Commands.literal("force")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            begin.apply(context),
                            end.apply(context),
                            destination.apply(context),
                            filter.apply(context),
                            CloneCommands.Mode.FORCE,
                            strict
                        )
                    )
            )
            .then(
                Commands.literal("move")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            begin.apply(context),
                            end.apply(context),
                            destination.apply(context),
                            filter.apply(context),
                            CloneCommands.Mode.MOVE,
                            strict
                        )
                    )
            )
            .then(
                Commands.literal("normal")
                    .executes(
                        context -> clone(
                            context.getSource(),
                            begin.apply(context),
                            end.apply(context),
                            destination.apply(context),
                            filter.apply(context),
                            CloneCommands.Mode.NORMAL,
                            strict
                        )
                    )
            );
    }

    private static int clone(
        CommandSourceStack source,
        CloneCommands.DimensionAndPosition begin,
        CloneCommands.DimensionAndPosition end,
        CloneCommands.DimensionAndPosition destination,
        Predicate<BlockInWorld> filter,
        CloneCommands.Mode mode,
        boolean strict
    ) throws CommandSyntaxException {
        BlockPos blockPos = begin.position();
        BlockPos blockPos1 = end.position();
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos1);
        BlockPos blockPos2 = destination.position();
        BlockPos blockPos3 = blockPos2.offset(boundingBox.getLength());
        BoundingBox boundingBox1 = BoundingBox.fromCorners(blockPos2, blockPos3);
        ServerLevel serverLevel = begin.dimension();
        ServerLevel serverLevel1 = destination.dimension();
        if (!mode.canOverlap() && serverLevel == serverLevel1 && boundingBox1.intersects(boundingBox)) {
            throw ERROR_OVERLAP.create();
        } else {
            int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
            int i1 = source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
            if (i > i1) {
                throw ERROR_AREA_TOO_LARGE.create(i1, i);
            } else if (!serverLevel.hasChunksAt(blockPos, blockPos1) || !serverLevel1.hasChunksAt(blockPos2, blockPos3)) {
                throw BlockPosArgument.ERROR_NOT_LOADED.create();
            } else if (serverLevel1.isDebug()) {
                throw ERROR_FAILED.create();
            } else {
                List<CloneCommands.CloneBlockInfo> list = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list1 = Lists.newArrayList();
                List<CloneCommands.CloneBlockInfo> list2 = Lists.newArrayList();
                Deque<BlockPos> list3 = Lists.newLinkedList();
                int i2 = 0;
                ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER);

                try {
                    BlockPos blockPos4 = new BlockPos(
                        boundingBox1.minX() - boundingBox.minX(), boundingBox1.minY() - boundingBox.minY(), boundingBox1.minZ() - boundingBox.minZ()
                    );

                    for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                        for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                                BlockPos blockPos5 = new BlockPos(x, y, z);
                                BlockPos blockPos6 = blockPos5.offset(blockPos4);
                                BlockInWorld blockInWorld = new BlockInWorld(serverLevel, blockPos5, false);
                                BlockState state = blockInWorld.getState();
                                if (filter.test(blockInWorld)) {
                                    BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos5);
                                    if (blockEntity != null) {
                                        TagValueOutput tagValueOutput = TagValueOutput.createWithContext(
                                            scopedCollector.forChild(blockEntity.problemPath()), source.registryAccess()
                                        );
                                        blockEntity.saveCustomOnly(tagValueOutput);
                                        CloneCommands.CloneBlockEntityInfo cloneBlockEntityInfo = new CloneCommands.CloneBlockEntityInfo(
                                            tagValueOutput.buildResult(), blockEntity.components()
                                        );
                                        list1.add(
                                            new CloneCommands.CloneBlockInfo(blockPos6, state, cloneBlockEntityInfo, serverLevel1.getBlockState(blockPos6))
                                        );
                                        list3.addLast(blockPos5);
                                    } else if (!state.isSolidRender() && !state.isCollisionShapeFullBlock(serverLevel, blockPos5)) {
                                        list2.add(new CloneCommands.CloneBlockInfo(blockPos6, state, null, serverLevel1.getBlockState(blockPos6)));
                                        list3.addFirst(blockPos5);
                                    } else {
                                        list.add(new CloneCommands.CloneBlockInfo(blockPos6, state, null, serverLevel1.getBlockState(blockPos6)));
                                        list3.addLast(blockPos5);
                                    }
                                }
                            }
                        }
                    }

                    int z = 2 | (strict ? 816 : 0);
                    if (mode == CloneCommands.Mode.MOVE) {
                        for (BlockPos blockPos7 : list3) {
                            serverLevel.setBlock(blockPos7, Blocks.BARRIER.defaultBlockState(), z | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                        }

                        int y = strict ? z : Block.UPDATE_ALL;

                        for (BlockPos blockPos5 : list3) {
                            serverLevel.setBlock(blockPos5, Blocks.AIR.defaultBlockState(), y);
                        }
                    }

                    List<CloneCommands.CloneBlockInfo> list4 = Lists.newArrayList();
                    list4.addAll(list);
                    list4.addAll(list1);
                    list4.addAll(list2);
                    List<CloneCommands.CloneBlockInfo> list5 = Lists.reverse(list4);

                    for (CloneCommands.CloneBlockInfo cloneBlockInfo : list5) {
                        serverLevel1.setBlock(cloneBlockInfo.pos, Blocks.BARRIER.defaultBlockState(), z | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
                    }

                    for (CloneCommands.CloneBlockInfo cloneBlockInfo : list4) {
                        if (serverLevel1.setBlock(cloneBlockInfo.pos, cloneBlockInfo.state, z)) {
                            i2++;
                        }
                    }

                    for (CloneCommands.CloneBlockInfo cloneBlockInfox : list1) {
                        BlockEntity blockEntity1 = serverLevel1.getBlockEntity(cloneBlockInfox.pos);
                        if (cloneBlockInfox.blockEntityInfo != null && blockEntity1 != null) {
                            blockEntity1.loadCustomOnly(
                                TagValueInput.create(
                                    scopedCollector.forChild(blockEntity1.problemPath()), serverLevel1.registryAccess(), cloneBlockInfox.blockEntityInfo.tag
                                )
                            );
                            blockEntity1.setComponents(cloneBlockInfox.blockEntityInfo.components);
                            blockEntity1.setChanged();
                        }

                        serverLevel1.setBlock(cloneBlockInfox.pos, cloneBlockInfox.state, z);
                    }

                    if (!strict) {
                        for (CloneCommands.CloneBlockInfo cloneBlockInfox : list5) {
                            serverLevel1.updateNeighboursOnBlockSet(cloneBlockInfox.pos, cloneBlockInfox.previousStateAtDestination);
                        }
                    }

                    serverLevel1.getBlockTicks().copyAreaFrom(serverLevel.getBlockTicks(), boundingBox, blockPos4);
                } catch (Throwable var35) {
                    try {
                        scopedCollector.close();
                    } catch (Throwable var34) {
                        var35.addSuppressed(var34);
                    }

                    throw var35;
                }

                scopedCollector.close();
                if (i2 == 0) {
                    throw ERROR_FAILED.create();
                } else {
                    int i3 = i2;
                    source.sendSuccess(() -> Component.translatable("commands.clone.success", i3), true);
                    return i2;
                }
            }
        }
    }

    record CloneBlockEntityInfo(CompoundTag tag, DataComponentMap components) {
    }

    record CloneBlockInfo(BlockPos pos, BlockState state, CloneCommands.@Nullable CloneBlockEntityInfo blockEntityInfo, BlockState previousStateAtDestination) {
    }

    record DimensionAndPosition(ServerLevel dimension, BlockPos position) {
    }

    static enum Mode {
        FORCE(true),
        MOVE(true),
        NORMAL(false);

        private final boolean canOverlap;

        private Mode(final boolean canOverlap) {
            this.canOverlap = canOverlap;
        }

        public boolean canOverlap() {
            return this.canOverlap;
        }
    }
}
