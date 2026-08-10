package net.minecraft.util;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;

public class FileZipper implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path outputFile;
    private final Path tempFile;
    private final FileSystem fs;

    public FileZipper(Path outputFile) {
        this.outputFile = outputFile;
        this.tempFile = outputFile.resolveSibling(outputFile.getFileName().toString() + "_tmp");

        try {
            this.fs = Util.ZIP_FILE_SYSTEM_PROVIDER.newFileSystem(this.tempFile, ImmutableMap.of("create", "true"));
        } catch (IOException var3) {
            throw new UncheckedIOException(var3);
        }
    }

    public void add(Path path, String filename) {
        try {
            Path path1 = this.fs.getPath(File.separator);
            Path path2 = path1.resolve(path.toString());
            Files.createDirectories(path2.getParent());
            Files.write(path2, filename.getBytes(StandardCharsets.UTF_8));
        } catch (IOException var5) {
            throw new UncheckedIOException(var5);
        }
    }

    public void add(Path path, File filename) {
        try {
            Path path1 = this.fs.getPath(File.separator);
            Path path2 = path1.resolve(path.toString());
            Files.createDirectories(path2.getParent());
            Files.copy(filename.toPath(), path2);
        } catch (IOException var5) {
            throw new UncheckedIOException(var5);
        }
    }

    public void add(Path path) {
        try {
            Path path1 = this.fs.getPath(File.separator);
            if (Files.isRegularFile(path)) {
                Path path2 = path1.resolve(path.getParent().relativize(path).toString());
                Files.copy(path2, path);
            } else {
                try (Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, (path5, basicFileAttributes) -> basicFileAttributes.isRegularFile())) {
                    for (Path path3 : stream.collect(Collectors.toList())) {
                        Path path4 = path1.resolve(path.relativize(path3).toString());
                        Files.createDirectories(path4.getParent());
                        Files.copy(path3, path4);
                    }
                }
            }
        } catch (IOException var9) {
            throw new UncheckedIOException(var9);
        }
    }

    @Override
    public void close() {
        try {
            this.fs.close();
            Files.move(this.tempFile, this.outputFile);
            LOGGER.info("Compressed to {}", this.outputFile);
        } catch (IOException var2) {
            throw new UncheckedIOException(var2);
        }
    }
}
