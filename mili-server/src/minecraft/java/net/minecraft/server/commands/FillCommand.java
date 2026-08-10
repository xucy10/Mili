package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

public class FillCommand {
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.fill.toobig", maxBlocks, specifiedBlocks)
    );
    static final BlockInput HOLLOW_CORE = new BlockInput(Blocks.AIR.defaultBlockState(), Collections.emptySet(), null);
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("fill")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("from", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("to", BlockPosArgument.blockPos())
                                .then(
                                    wrapWithMode(
                                            buildContext,
                                            Commands.argument("block", BlockStateArgument.block(buildContext)),
                                            input -> BlockPosArgument.getLoadedBlockPos(input, "from"),
                                            context -> BlockPosArgument.getLoadedBlockPos(context, "to"),
                                            input -> BlockStateArgument.getBlock(input, "block"),
                                            context -> null
                                        )
                                        .then(
                                            Commands.literal("replace")
                                                .executes(
                                                    commandContext -> fillBlocks(
                                                        commandContext.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(commandContext, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(commandContext, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(commandContext, "block"),
                                                        FillCommand.Mode.REPLACE,
                                                        null,
                                                        false
                                                    )
                                                )
                                                .then(
                                                    wrapWithMode(
                                                        buildContext,
                                                        Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext)),
                                                        input -> BlockPosArgument.getLoadedBlockPos(input, "from"),
                                                        context -> BlockPosArgument.getLoadedBlockPos(context, "to"),
                                                        input -> BlockStateArgument.getBlock(input, "block"),
                                                        context -> BlockPredicateArgument.getBlockPredicate(context, "filter")
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("keep")
                                                .executes(
                                                    commandContext -> fillBlocks(
                                                        commandContext.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(commandContext, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(commandContext, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(commandContext, "block"),
                                                        FillCommand.Mode.REPLACE,
                                                        blockInWorld -> blockInWorld.getLevel().isEmptyBlock(blockInWorld.getPos()),
                                                        false
                                                    )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapWithMode(
        CommandBuildContext buildContext,
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
        InCommandFunction<CommandContext<CommandSourceStack>, BlockPos> from,
        InCommandFunction<CommandContext<CommandSourceStack>, BlockPos> to,
        InCommandFunction<CommandContext<CommandSourceStack>, BlockInput> block,
        FillCommand.NullableCommandFunction<CommandContext<CommandSourceStack>, Predicate<BlockInWorld>> filter
    ) {
        return argumentBuilder.executes(
                commandContext -> fillBlocks(
                    commandContext.getSource(),
                    BoundingBox.fromCorners(from.apply(commandContext), to.apply(commandContext)),
                    block.apply(commandContext),
                    FillCommand.Mode.REPLACE,
                    filter.apply(commandContext),
                    false
                )
            )
            .then(
                Commands.literal("outline")
                    .executes(
                        context -> fillBlocks(
                            context.getSource(),
                            BoundingBox.fromCorners(from.apply(context), to.apply(context)),
                            block.apply(context),
                            FillCommand.Mode.OUTLINE,
                            filter.apply(context),
                            false
                        )
                    )
            )
            .then(
                Commands.literal("hollow")
                    .executes(
                        context -> fillBlocks(
                            context.getSource(),
                            BoundingBox.fromCorners(from.apply(context), to.apply(context)),
                            block.apply(context),
                            FillCommand.Mode.HOLLOW,
                            filter.apply(context),
                            false
                        )
                    )
            )
            .then(
                Commands.literal("destroy")
                    .executes(
                        context -> fillBlocks(
                            context.getSource(),
                            BoundingBox.fromCorners(from.apply(context), to.apply(context)),
                            block.apply(context),
                            FillCommand.Mode.DESTROY,
                            filter.apply(context),
                            false
                        )
                    )
            )
            .then(
                Commands.literal("strict")
                    .executes(
                        context -> fillBlocks(
                            context.getSource(),
                            BoundingBox.fromCorners(from.apply(context), to.apply(context)),
                            block.apply(context),
                            FillCommand.Mode.REPLACE,
                            filter.apply(context),
                            true
                        )
                    )
            );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int fillBlocks(
        CommandSourceStack source, BoundingBox box, BlockInput block, FillCommand.Mode mode, @Nullable Predicate<BlockInWorld> filter, boolean strict
    ) throws CommandSyntaxException {
        int i = box.getXSpan() * box.getYSpan() * box.getZSpan();
        int i1 = source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
        if (i > i1) {
            throw ERROR_AREA_TOO_LARGE.create(i1, i);
        } else {
            record UpdatedPosition(BlockPos pos, BlockState oldState) {
            }

            List<UpdatedPosition> list = Lists.newArrayList();
            ServerLevel level = source.getLevel();
            if (level.isDebug()) {
                throw ERROR_FAILED.create();
            } else {
                // Folia start - region threading
                int buffer = 32;
                // physics may spill into neighbour chunks, so use a buffer
                level.moonrise$loadChunksAsync(
                        (box.minX() - buffer) >> 4,
                        (box.maxX() + buffer) >> 4,
                        (box.minZ() - buffer) >> 4,
                        (box.maxZ() + buffer) >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                        ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                        (chunks) -> {
                            try { // Folia end - region threading
                int i2 = 0;

                for (BlockPos blockPos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    if (filter == null || filter.test(new BlockInWorld(level, blockPos, true))) {
                        BlockState blockState = level.getBlockState(blockPos);
                        boolean flag = false;
                        if (mode.affector.affect(level, blockPos)) {
                            flag = true;
                        }

                        BlockInput blockInput = mode.filter.filter(box, blockPos, block, level);
                        if (blockInput == null) {
                            if (flag) {
                                i2++;
                            }
                        } else if (!blockInput.place(
                            level, blockPos, Block.UPDATE_CLIENTS | (strict ? Block.UPDATE_SKIP_ALL_SIDEEFFECTS : Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS)
                        )) {
                            if (flag) {
                                i2++;
                            }
                        } else {
                            if (!strict) {
                                list.add(new UpdatedPosition(blockPos.immutable(), blockState));
                            }

                            i2++;
                        }
                    }
                }

                for (UpdatedPosition updatedPosition : list) {
                    level.updateNeighboursOnBlockSet(updatedPosition.pos, updatedPosition.oldState);
                }

                if (i2 == 0) {
                    throw ERROR_FAILED.create();
                } else {
                    int i3 = i2;
                    source.sendSuccess(() -> Component.translatable("commands.fill.success", i3), true);
                    return; // Folia - region threading
                }
                // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }}); return 0; // Folia end - region threading
            }
        }
    }

    @FunctionalInterface
    public interface Affector {
        FillCommand.Affector NOOP = (level, pos) -> false;

        boolean affect(ServerLevel level, BlockPos pos);
    }

    @FunctionalInterface
    public interface Filter {
        FillCommand.Filter NOOP = (box, pos, block, level) -> block;

        @Nullable BlockInput filter(BoundingBox box, BlockPos pos, BlockInput block, ServerLevel level);
    }

    static enum Mode {
        REPLACE(FillCommand.Affector.NOOP, FillCommand.Filter.NOOP),
        OUTLINE(
            FillCommand.Affector.NOOP,
            (area, pos, newBlock, level) -> pos.getX() != area.minX()
                    && pos.getX() != area.maxX()
                    && pos.getY() != area.minY()
                    && pos.getY() != area.maxY()
                    && pos.getZ() != area.minZ()
                    && pos.getZ() != area.maxZ()
                ? null
                : newBlock
        ),
        HOLLOW(
            FillCommand.Affector.NOOP,
            (area, pos, newBlock, level) -> pos.getX() != area.minX()
                    && pos.getX() != area.maxX()
                    && pos.getY() != area.minY()
                    && pos.getY() != area.maxY()
                    && pos.getZ() != area.minZ()
                    && pos.getZ() != area.maxZ()
                ? FillCommand.HOLLOW_CORE
                : newBlock
        ),
        DESTROY((level, pos) -> level.destroyBlock(pos, true), FillCommand.Filter.NOOP);

        public final FillCommand.Filter filter;
        public final FillCommand.Affector affector;

        private Mode(final FillCommand.Affector affector, final FillCommand.Filter filter) {
            this.affector = affector;
            this.filter = filter;
        }
    }

    @FunctionalInterface
    interface NullableCommandFunction<T, R> {
        @Nullable R apply(T context) throws CommandSyntaxException;
    }
}
