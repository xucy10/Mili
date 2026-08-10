package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jspecify.annotations.Nullable;

public class SetBlockCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        Predicate<BlockInWorld> predicate = block -> block.getLevel().isEmptyBlock(block.getPos());
        dispatcher.register(
            Commands.literal("setblock")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("block", BlockStateArgument.block(buildContext))
                                .executes(
                                    context -> setBlock(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        BlockStateArgument.getBlock(context, "block"),
                                        SetBlockCommand.Mode.REPLACE,
                                        null,
                                        false
                                    )
                                )
                                .then(
                                    Commands.literal("destroy")
                                        .executes(
                                            context -> setBlock(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                BlockStateArgument.getBlock(context, "block"),
                                                SetBlockCommand.Mode.DESTROY,
                                                null,
                                                false
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("keep")
                                        .executes(
                                            commandContext -> setBlock(
                                                commandContext.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(commandContext, "pos"),
                                                BlockStateArgument.getBlock(commandContext, "block"),
                                                SetBlockCommand.Mode.REPLACE,
                                                predicate,
                                                false
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("replace")
                                        .executes(
                                            context -> setBlock(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                BlockStateArgument.getBlock(context, "block"),
                                                SetBlockCommand.Mode.REPLACE,
                                                null,
                                                false
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("strict")
                                        .executes(
                                            context -> setBlock(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                BlockStateArgument.getBlock(context, "block"),
                                                SetBlockCommand.Mode.REPLACE,
                                                null,
                                                true
                                            )
                                        )
                                )
                        )
                )
        );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int setBlock(
        CommandSourceStack source, BlockPos pos, BlockInput block, SetBlockCommand.Mode mode, @Nullable Predicate<BlockInWorld> filter, boolean strict
    ) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                level, pos.getX() >> 4, pos.getZ() >> 4, () -> {
                    try {
                        // Folia end - region threading
        if (level.isDebug()) {
            throw ERROR_FAILED.create();
        } else if (filter != null && !filter.test(new BlockInWorld(level, pos, true))) {
            throw ERROR_FAILED.create();
        } else {
            boolean flag;
            if (mode == SetBlockCommand.Mode.DESTROY) {
                level.destroyBlock(pos, true);
                flag = !block.getState().isAir() || !level.getBlockState(pos).isAir();
            } else {
                flag = true;
            }

            BlockState blockState = level.getBlockState(pos);
            if (flag
                && !block.place(level, pos, Block.UPDATE_CLIENTS | (strict ? Block.UPDATE_SKIP_ALL_SIDEEFFECTS : Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS))) {
                throw ERROR_FAILED.create();
            } else {
                if (!strict) {
                    level.updateNeighboursOnBlockSet(pos, blockState);
                }

                source.sendSuccess(() -> Component.translatable("commands.setblock.success", pos.getX(), pos.getY(), pos.getZ()), true);
                return; // Folia - region threading
            }
        }
        // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            });
        return 1;
        // Folia end - region threading
    }

    public static enum Mode {
        REPLACE,
        DESTROY;
    }
}
