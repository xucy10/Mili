package net.minecraft.server.commands;

import com.google.common.base.Joiner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class ForceLoadCommand {
    private static final int MAX_CHUNK_LIMIT = 256;
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType(
        (maxChunks, specifiedChunks) -> Component.translatableEscape("commands.forceload.toobig", maxChunks, specifiedChunks)
    );
    private static final Dynamic2CommandExceptionType ERROR_NOT_TICKING = new Dynamic2CommandExceptionType(
        (chunkPos, level) -> Component.translatableEscape("commands.forceload.query.failure", chunkPos, level)
    );
    private static final SimpleCommandExceptionType ERROR_ALL_ADDED = new SimpleCommandExceptionType(Component.translatable("commands.forceload.added.failure"));
    private static final SimpleCommandExceptionType ERROR_NONE_REMOVED = new SimpleCommandExceptionType(
        Component.translatable("commands.forceload.removed.failure")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("forceload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("from", ColumnPosArgument.columnPos())
                                .executes(
                                    commandContext -> changeForceLoad(
                                        commandContext.getSource(),
                                        ColumnPosArgument.getColumnPos(commandContext, "from"),
                                        ColumnPosArgument.getColumnPos(commandContext, "from"),
                                        true
                                    )
                                )
                                .then(
                                    Commands.argument("to", ColumnPosArgument.columnPos())
                                        .executes(
                                            context -> changeForceLoad(
                                                context.getSource(),
                                                ColumnPosArgument.getColumnPos(context, "from"),
                                                ColumnPosArgument.getColumnPos(context, "to"),
                                                true
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("from", ColumnPosArgument.columnPos())
                                .executes(
                                    context -> changeForceLoad(
                                        context.getSource(),
                                        ColumnPosArgument.getColumnPos(context, "from"),
                                        ColumnPosArgument.getColumnPos(context, "from"),
                                        false
                                    )
                                )
                                .then(
                                    Commands.argument("to", ColumnPosArgument.columnPos())
                                        .executes(
                                            context -> changeForceLoad(
                                                context.getSource(),
                                                ColumnPosArgument.getColumnPos(context, "from"),
                                                ColumnPosArgument.getColumnPos(context, "to"),
                                                false
                                            )
                                        )
                                )
                        )
                        .then(Commands.literal("all").executes(context -> removeAll(context.getSource())))
                )
                .then(
                    Commands.literal("query")
                        .executes(context -> listForceLoad(context.getSource()))
                        .then(
                            Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(context -> queryForceLoad(context.getSource(), ColumnPosArgument.getColumnPos(context, "pos")))
                        )
                )
        );
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int queryForceLoad(CommandSourceStack source, ColumnPos pos) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
        // Folia end - region threading
        ChunkPos chunkPos = pos.toChunkPos();
        ServerLevel level = source.getLevel();
        ResourceKey<Level> resourceKey = level.dimension();
        boolean flag = level.getForceLoadedChunks().contains(chunkPos.toLong());
        if (flag) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.forceload.query.success", Component.translationArg(chunkPos), Component.translationArg(resourceKey.identifier())
                ),
                false
            );
            return; // Folia - region threading
        } else {
            throw ERROR_NOT_TICKING.create(chunkPos, resourceKey.identifier());
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }

    private static int listForceLoad(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        ResourceKey<Level> resourceKey = level.dimension();
        LongSet forceLoadedChunks = level.getForceLoadedChunks();
        int size = forceLoadedChunks.size();
        if (size > 0) {
            String string = Joiner.on(", ").join(forceLoadedChunks.stream().sorted().map(ChunkPos::new).map(ChunkPos::toString).iterator());
            if (size == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.forceload.list.single", Component.translationArg(resourceKey.identifier()), string), false
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable("commands.forceload.list.multiple", size, Component.translationArg(resourceKey.identifier()), string), false
                );
            }
        } else {
            source.sendFailure(Component.translatable("commands.forceload.added.none", Component.translationArg(resourceKey.identifier())));
        }
        }); // Folia - region threading

        return 1; // Folia - region threading
    }

    private static int removeAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ResourceKey<Level> resourceKey = level.dimension();
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
        LongSet forceLoadedChunks = level.getForceLoadedChunks();
        forceLoadedChunks.forEach(l -> level.setChunkForced(ChunkPos.getX(l), ChunkPos.getZ(l), false));
        source.sendSuccess(() -> Component.translatable("commands.forceload.removed.all", Component.translationArg(resourceKey.identifier())), true);
        }); // Folia - region threading
        return 0;
    }

    private static int changeForceLoad(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean add) throws CommandSyntaxException {
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
        // Folia end - region threading
        int min = Math.min(from.x(), to.x());
        int min1 = Math.min(from.z(), to.z());
        int max = Math.max(from.x(), to.x());
        int max1 = Math.max(from.z(), to.z());
        if (min >= -30000000 && min1 >= -30000000 && max < 30000000 && max1 < 30000000) {
            int sectionPosCoord = SectionPos.blockToSectionCoord(min);
            int sectionPosCoord1 = SectionPos.blockToSectionCoord(min1);
            int sectionPosCoord2 = SectionPos.blockToSectionCoord(max);
            int sectionPosCoord3 = SectionPos.blockToSectionCoord(max1);
            long l = (sectionPosCoord2 - sectionPosCoord + 1L) * (sectionPosCoord3 - sectionPosCoord1 + 1L);
            if (l > 256L) {
                throw ERROR_TOO_MANY_CHUNKS.create(256, l);
            } else {
                ServerLevel level = source.getLevel();
                ResourceKey<Level> resourceKey = level.dimension();
                ChunkPos chunkPos = null;
                int i = 0;

                for (int i1 = sectionPosCoord; i1 <= sectionPosCoord2; i1++) {
                    for (int i2 = sectionPosCoord1; i2 <= sectionPosCoord3; i2++) {
                        boolean flag = level.setChunkForced(i1, i2, add);
                        if (flag) {
                            i++;
                            if (chunkPos == null) {
                                chunkPos = new ChunkPos(i1, i2);
                            }
                        }
                    }
                }

                ChunkPos chunkPos1 = chunkPos;
                int i2x = i;
                if (i2x == 0) {
                    throw (add ? ERROR_ALL_ADDED : ERROR_NONE_REMOVED).create();
                } else {
                    if (i2x == 1) {
                        source.sendSuccess(
                            () -> Component.translatable(
                                "commands.forceload." + (add ? "added" : "removed") + ".single",
                                Component.translationArg(chunkPos1),
                                Component.translationArg(resourceKey.identifier())
                            ),
                            true
                        );
                    } else {
                        ChunkPos chunkPos2 = new ChunkPos(sectionPosCoord, sectionPosCoord1);
                        ChunkPos chunkPos3 = new ChunkPos(sectionPosCoord2, sectionPosCoord3);
                        source.sendSuccess(
                            () -> Component.translatable(
                                "commands.forceload." + (add ? "added" : "removed") + ".multiple",
                                i2x,
                                Component.translationArg(resourceKey.identifier()),
                                Component.translationArg(chunkPos2),
                                Component.translationArg(chunkPos3)
                            ),
                            true
                        );
                    }

                    return; // Folia - region threading
                }
            }
        } else {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
        // Folia start - region threading
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
        // Folia end - region threading
    }
}
