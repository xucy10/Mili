package net.minecraft.data.structures;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Util;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class SnbtToNbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput output;
    private final Iterable<Path> inputFolders;
    private final List<SnbtToNbt.Filter> filters = Lists.newArrayList();

    public SnbtToNbt(PackOutput output, Iterable<Path> inputFolders) {
        this.output = output;
        this.inputFolders = inputFolders;
    }

    public SnbtToNbt addFilter(SnbtToNbt.Filter filter) {
        this.filters.add(filter);
        return this;
    }

    private CompoundTag applyFilters(String fileName, CompoundTag tag) {
        CompoundTag compoundTag = tag;

        for (SnbtToNbt.Filter filter : this.filters) {
            compoundTag = filter.apply(fileName, compoundTag);
        }

        return compoundTag;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path outputFolder = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = Lists.newArrayList();

        for (Path path : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture>supplyAsync(
                        () -> {
                            try {
                                CompletableFuture var5x;
                                try (Stream<Path> stream = Files.walk(path)) {
                                    var5x = CompletableFuture.allOf(
                                        stream.filter(path1 -> path1.toString().endsWith(".snbt")).map(path1 -> CompletableFuture.runAsync(() -> {
                                            SnbtToNbt.TaskResult structure = this.readStructure(path1, this.getName(path, path1));
                                            this.storeStructureIfChanged(output, structure, outputFolder);
                                        }, Util.backgroundExecutor().forName("SnbtToNbt"))).toArray(CompletableFuture[]::new)
                                    );
                                }

                                return var5x;
                            } catch (Exception var9) {
                                throw new RuntimeException("Failed to read structure input directory, aborting", var9);
                            }
                        },
                        Util.backgroundExecutor().forName("SnbtToNbt")
                    )
                    .thenCompose(completableFuture -> completableFuture)
            );
        }

        return Util.sequenceFailFast(list);
    }

    @Override
    public final String getName() {
        return "SNBT -> NBT";
    }

    private String getName(Path inputFolder, Path filePath) {
        String string = inputFolder.relativize(filePath).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".snbt".length());
    }

    private SnbtToNbt.TaskResult readStructure(Path filePath, String fileName) {
        try {
            SnbtToNbt.TaskResult var10;
            try (BufferedReader bufferedReader = Files.newBufferedReader(filePath)) {
                String string = IOUtils.toString(bufferedReader);
                CompoundTag compoundTag = this.applyFilters(fileName, NbtUtils.snbtToStructure(string));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
                NbtIo.writeCompressed(compoundTag, hashingOutputStream);
                byte[] bytes = byteArrayOutputStream.toByteArray();
                HashCode hashCode = hashingOutputStream.hash();
                var10 = new SnbtToNbt.TaskResult(fileName, bytes, hashCode);
            }

            return var10;
        } catch (Throwable var13) {
            throw new SnbtToNbt.StructureConversionException(filePath, var13);
        }
    }

    private void storeStructureIfChanged(CachedOutput output, SnbtToNbt.TaskResult taskResult, Path directoryPath) {
        Path path = directoryPath.resolve(taskResult.name + ".nbt");

        try {
            output.writeIfNeeded(path, taskResult.payload, taskResult.hash);
        } catch (IOException var6) {
            LOGGER.error("Couldn't write structure {} at {}", taskResult.name, path, var6);
        }
    }

    @FunctionalInterface
    public interface Filter {
        CompoundTag apply(String structureLocationPath, CompoundTag tag);
    }

    static class StructureConversionException extends RuntimeException {
        public StructureConversionException(Path path, Throwable cause) {
            super(path.toAbsolutePath().toString(), cause);
        }
    }

    record TaskResult(String name, byte[] payload, HashCode hash) {
    }
}
