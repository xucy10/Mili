package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.mutable.MutableInt;

public class FillBiomeCommand {
    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Component.translatable("argument.pos.unloaded"));
    private static final Dynamic2CommandExceptionType ERROR_VOLUME_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.fillbiome.toobig", maxBlocks, specifiedBlocks)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("fillbiome")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("from", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("to", BlockPosArgument.blockPos())
                                .then(
                                    Commands.argument("biome", ResourceArgument.resource(context, Registries.BIOME))
                                        .executes(
                                            context1 -> fill(
                                                context1.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context1, "from"),
                                                BlockPosArgument.getLoadedBlockPos(context1, "to"),
                                                ResourceArgument.getResource(context1, "biome", Registries.BIOME),
                                                holder -> true
                                            )
                                        )
                                        .then(
                                            Commands.literal("replace")
                                                .then(
                                                    Commands.argument("filter", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME))
                                                        .executes(
                                                            context1 -> fill(
                                                                context1.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(context1, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(context1, "to"),
                                                                ResourceArgument.getResource(context1, "biome", Registries.BIOME),
                                                                ResourceOrTagArgument.getResourceOrTag(context1, "filter", Registries.BIOME)
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int quantize(int value) {
        return QuartPos.toBlock(QuartPos.fromBlock(value));
    }

    private static BlockPos quantize(BlockPos pos) {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    private static BiomeResolver makeResolver(
        MutableInt biomeEntries, ChunkAccess chunk, BoundingBox targetRegion, Holder<Biome> replacementBiome, Predicate<Holder<Biome>> filter
    ) {
        return (x, y, z, sampler) -> {
            int blockPosX = QuartPos.toBlock(x);
            int blockPosY = QuartPos.toBlock(y);
            int blockPosZ = QuartPos.toBlock(z);
            Holder<Biome> noiseBiome = chunk.getNoiseBiome(x, y, z);
            if (targetRegion.isInside(blockPosX, blockPosY, blockPosZ) && filter.test(noiseBiome)) {
                biomeEntries.increment();
                return replacementBiome;
            } else {
                return noiseBiome;
            }
        };
    }

    public static Either<Integer, CommandSyntaxException> fill(ServerLevel level, BlockPos from, BlockPos to, Holder<Biome> biome) {
        return fill(level, from, to, biome, holder -> true, supplier -> {});
    }

    // Folia start - region threading
    private static void sendMessage(Consumer<Supplier<Component>> src, Supplier<Either<Integer, CommandSyntaxException>> supplier) {
        Either<Integer, CommandSyntaxException> either = supplier.get();
        CommandSyntaxException ex = either == null ? null : either.right().orElse(null);
        if (ex != null) {
            src.accept(() -> (Component)ex.getRawMessage());
        }
    }
    // Folia end - region threading

    public static Either<Integer, CommandSyntaxException> fill(
        ServerLevel level, BlockPos from, BlockPos to, Holder<Biome> biome, Predicate<Holder<Biome>> filter, Consumer<Supplier<Component>> messageOutput
    ) {
        BlockPos blockPos = quantize(from);
        BlockPos blockPos1 = quantize(to);
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos1);
        int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
        int i1 = level.getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
        if (i > i1) {
            return Either.right(ERROR_VOLUME_TOO_LARGE.create(i1, i));
        } else {
            // Folia start - region threading
            int buffer = 0; // no buffer, we do not touch neighbours
            level.moonrise$loadChunksAsync(
                (boundingBox.minX() - buffer) >> 4,
                (boundingBox.maxX() + buffer) >> 4,
                (boundingBox.minZ() - buffer) >> 4,
                (boundingBox.maxZ() + buffer) >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.util.Priority.NORMAL,
                (chunks) -> {
                    sendMessage(messageOutput, () -> {
            List<ChunkAccess> list = new ArrayList<>();

            for (int sectionPosMinZ = SectionPos.blockToSectionCoord(boundingBox.minZ());
                sectionPosMinZ <= SectionPos.blockToSectionCoord(boundingBox.maxZ());
                sectionPosMinZ++
            ) {
                for (int sectionPosMinX = SectionPos.blockToSectionCoord(boundingBox.minX());
                    sectionPosMinX <= SectionPos.blockToSectionCoord(boundingBox.maxX());
                    sectionPosMinX++
                ) {
                    ChunkAccess chunk = level.getChunk(sectionPosMinX, sectionPosMinZ, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        return Either.right(ERROR_NOT_LOADED.create());
                    }

                    list.add(chunk);
                }
            }

            MutableInt mutableInt = new MutableInt(0);

            for (ChunkAccess chunk : list) {
                chunk.fillBiomesFromNoise(makeResolver(mutableInt, chunk, boundingBox, biome, filter), level.getChunkSource().randomState().sampler());
                chunk.markUnsaved();
            }

            level.getChunkSource().chunkMap.resendBiomesForChunks(list);
            messageOutput.accept(
                () -> Component.translatable(
                    "commands.fillbiome.success.count",
                    mutableInt.intValue(),
                    boundingBox.minX(),
                    boundingBox.minY(),
                    boundingBox.minZ(),
                    boundingBox.maxX(),
                    boundingBox.maxY(),
                    boundingBox.maxZ()
                )
            );
            return Either.left(mutableInt.intValue());
            // Folia start - region threading
                    }); // sendMessage
                    }); // loadChunksAsync
            return Either.left(Integer.valueOf(0));
            // Folia end - region threading
        }
    }

    private static int fill(CommandSourceStack source, BlockPos from, BlockPos to, Holder.Reference<Biome> biome, Predicate<Holder<Biome>> filter) throws CommandSyntaxException {
        Either<Integer, CommandSyntaxException> either = fill(source.getLevel(), from, to, biome, filter, supplier -> source.sendSuccess(supplier, true));
        Optional<CommandSyntaxException> optional = either.right();
        if (optional.isPresent()) {
            throw (CommandSyntaxException)optional.get();
        } else {
            return either.left().get();
        }
    }
}
