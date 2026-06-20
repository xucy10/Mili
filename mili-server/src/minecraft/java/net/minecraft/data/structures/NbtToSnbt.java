package net.minecraft.data.structures;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class NbtToSnbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Iterable<Path> inputFolders;
    private final PackOutput output;

    public NbtToSnbt(PackOutput output, Collection<Path> inputFolders) {
        this.inputFolders = inputFolders;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path outputFolder = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = new ArrayList<>();

        for (Path path : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture>supplyAsync(
                        () -> {
                            try {
                                CompletableFuture var4;
                                try (Stream<Path> stream = Files.walk(path)) {
                                    var4 = CompletableFuture.allOf(
                                        stream.filter(path1 -> path1.toString().endsWith(".nbt"))
                                            .map(
                                                path1 -> CompletableFuture.runAsync(
                                                    () -> convertStructure(output, path1, getName(path, path1), outputFolder), Util.ioPool()
                                                )
                                            )
                                            .toArray(CompletableFuture[]::new)
                                    );
                                }

                                return var4;
                            } catch (IOException var8) {
                                LOGGER.error("Failed to read structure input directory", (Throwable)var8);
                                return CompletableFuture.completedFuture(null);
                            }
                        },
                        Util.backgroundExecutor().forName("NbtToSnbt")
                    )
                    .thenCompose(completableFuture -> completableFuture)
            );
        }

        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    @Override
    public final String getName() {
        return "NBT -> SNBT";
    }

    private static String getName(Path inputFolder, Path nbtPath) {
        String string = inputFolder.relativize(nbtPath).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".nbt".length());
    }

    public static @Nullable Path convertStructure(CachedOutput output, Path nbtPath, String name, Path directoryPath) {
        try {
            Path var7;
            try (
                InputStream inputStream = Files.newInputStream(nbtPath);
                InputStream inputStream1 = new FastBufferedInputStream(inputStream);
            ) {
                Path path = directoryPath.resolve(name + ".snbt");
                writeSnbt(output, path, NbtUtils.structureToSnbt(NbtIo.readCompressed(inputStream1, NbtAccounter.unlimitedHeap())));
                LOGGER.info("Converted {} from NBT to SNBT", name);
                var7 = path;
            }

            return var7;
        } catch (IOException var12) {
            LOGGER.error("Couldn't convert {} from NBT to SNBT at {}", name, nbtPath, var12);
            return null;
        }
    }

    public static void writeSnbt(CachedOutput output, Path path, String contents) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
        hashingOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
        hashingOutputStream.write(10);
        output.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
    }
}
